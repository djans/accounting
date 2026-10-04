package com.cogitosum.service;

import com.cogitosum.dto.TaxContributionDTO;
import com.cogitosum.dto.TaxReturnLineDTO;
import com.cogitosum.dto.TaxReturnLineDetailDTO;
import com.cogitosum.dto.TaxReturnRowDTO;
import com.cogitosum.entity.*;
import com.cogitosum.repository.BillRepository;
import com.cogitosum.repository.ChartOfAccountRepository;
import com.cogitosum.repository.CreditCardChargeRepository;
import com.cogitosum.repository.InvoiceRepository;
import com.cogitosum.repository.TaxAgencyRepository;
import com.cogitosum.repository.TaxFilingPeriodRepository;
import com.cogitosum.repository.TaxReturnRowSnapshotRepository;
import com.cogitosum.repository.WrittenChequeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class TaxFilingService {

    public static final String CRA_CODE = "CRA";
    public static final String RQ_CODE = "RQ";
    public static final String MRQ2013_CODE = "MRQ2013";

    @Autowired
    private TaxFilingPeriodRepository periodRepository;

    @Autowired
    private TaxReturnRowSnapshotRepository returnRowSnapshotRepository;

    @Autowired
    private TaxAgencyRepository agencyRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private BillRepository billRepository;

    @Autowired
    private CreditCardChargeRepository creditCardChargeRepository;

    @Autowired
    private WrittenChequeRepository chequeRepository;

    @Autowired
    private ChartOfAccountRepository accountRepository;

    @Autowired
    private GeneralJournalService journalService;

    @Autowired
    private CurrentCompanyContext companyContext;

    @Autowired
    private TaxCodeService taxCodeService;

    @Autowired
    private BillService billService;

    @Transactional
    public TaxFilingPeriod createPeriod(Long agencyId, LocalDate start, LocalDate end) {
        Long companyId = companyContext.requireCompanyId();
        TaxAgency agency = agencyRepository.findByIdAndCompanyId(agencyId, companyId)
            .orElseThrow(() -> new IllegalArgumentException("Agency not found: " + agencyId));
        TaxFilingPeriod period = new TaxFilingPeriod();
        period.setCompany(companyContext.requireCompany());
        period.setAgency(agency);
        period.setPeriodStart(start);
        period.setPeriodEnd(end);
        period.setStatus(TaxFilingStatus.OPEN);
        period.setTaxCollected(BigDecimal.ZERO);
        period.setTaxItc(BigDecimal.ZERO);
        period.setNetOwing(BigDecimal.ZERO);
        return periodRepository.save(period);
    }

    @Transactional
    public TaxFilingPeriod calculate(Long periodId) {
        TaxFilingPeriod period = periodRepository.findByIdAndCompanyId(periodId, companyContext.requireCompanyId())
            .orElseThrow(() -> new IllegalArgumentException("Period not found: " + periodId));
        if (period.getStatus() == TaxFilingStatus.FILED || period.getStatus() == TaxFilingStatus.PAID) {
            throw new IllegalStateException("Cannot recalculate a filed or paid period");
        }
        BigDecimal collected = sumTaxCollected(period.getAgency(), period.getPeriodStart(), period.getPeriodEnd());
        BigDecimal itc = sumItc(period.getAgency(), period.getPeriodStart(), period.getPeriodEnd());
        period.setTaxCollected(collected);
        period.setTaxItc(itc);
        period.setNetOwing(collected.subtract(itc));
        period.setStatus(TaxFilingStatus.CALCULATED);
        return periodRepository.save(period);
    }

    @Transactional
    public TaxFilingPeriod file(Long periodId, String postedBy) {
        TaxFilingPeriod period = periodRepository.findByIdAndCompanyId(periodId, companyContext.requireCompanyId())
            .orElseThrow(() -> new IllegalArgumentException("Period not found: " + periodId));
        if (period.getStatus() != TaxFilingStatus.CALCULATED && period.getStatus() != TaxFilingStatus.OPEN) {
            throw new IllegalStateException("Period must be OPEN or CALCULATED to file. Current: " + period.getStatus());
        }
        List<TaxReturnLineDTO> returnLines = getReturnLineBreakdown(period);
        if (hasUnmappedReturnLineAmounts(period, returnLines)) {
            throw new IllegalStateException("Tax return lines must be configured for all non-zero amounts before filing");
        }
        List<TaxReturnRowDTO> returnRows = getTaxReturnRows(period, returnLines);
        BigDecimal collected = returnBalance(returnRows, "105").add(returnBalance(returnRows, "205"));
        BigDecimal itc = returnBalance(returnRows, "108").add(returnBalance(returnRows, "208"));
        BigDecimal net = returnBalanceByDescription(returnRows, "tax.detail.returnLinePayable");

        period.setTaxCollected(collected);
        period.setTaxItc(itc);
        period.setNetOwing(net);
        period.setFiledDate(LocalDate.now());
        List<TaxReturnRowSnapshot> snapshots = new ArrayList<>(returnRows.size());
        for (int index = 0; index < returnRows.size(); index++) {
            TaxReturnRowDTO row = returnRows.get(index);
            TaxReturnRowSnapshot snapshot = new TaxReturnRowSnapshot();
            snapshot.setPeriod(period);
            snapshot.setDisplayOrder(index);
            snapshot.setDescriptionKey(row.descriptionKey());
            snapshot.setLine(row.line());
            snapshot.setAmount(row.amount());
            snapshot.setBalance(row.balance());
            snapshot.setTotal(row.total());
            snapshot.setUnmapped(row.unmapped());
            snapshots.add(snapshot);
        }
        returnRowSnapshotRepository.saveAll(snapshots);

        // The filing journal reclassifies collected tax and clears recoverable input tax.
        GeneralJournal filingJournal = buildItcAdjustmentJournal(period, returnRows);
        if (filingJournal != null) {
            GeneralJournal saved = journalService.createJournal(filingJournal);
            GeneralJournal posted = journalService.postJournal(saved.getId(), postedBy);
            period.setFilingJournal(posted);
        }

        period.setStatus(TaxFilingStatus.FILED);
        return periodRepository.save(period);
    }

    @Transactional
    public TaxFilingPeriod recordPayment(Long periodId, String bankAccountNumber, LocalDate paymentDate, String postedBy) {
        Long companyId = companyContext.requireCompanyId();
        TaxFilingPeriod period = periodRepository.findByIdAndCompanyId(periodId, companyId)
            .orElseThrow(() -> new IllegalArgumentException("Period not found: " + periodId));
        if (period.getStatus() != TaxFilingStatus.FILED) {
            throw new IllegalStateException("Period must be FILED before recording payment. Current: " + period.getStatus());
        }
        if (period.getNetOwing().compareTo(BigDecimal.ZERO) <= 0) {
            // Nothing to pay — just mark as paid.
            period.setStatus(TaxFilingStatus.PAID);
            period.setPaidDate(paymentDate == null ? LocalDate.now() : paymentDate);
            return periodRepository.save(period);
        }

        ChartOfAccount bank = accountRepository.findByCompanyIdAndAccountNumber(companyId, bankAccountNumber)
            .orElseThrow(() -> new IllegalArgumentException("Bank account not found: " + bankAccountNumber));

        GeneralJournal paymentJournal = buildPaymentJournal(period, bank, paymentDate);
        GeneralJournal saved = journalService.createJournal(paymentJournal);
        GeneralJournal posted = journalService.postJournal(saved.getId(), postedBy);

        period.setPaymentJournal(posted);
        period.setPaidDate(paymentDate == null ? LocalDate.now() : paymentDate);
        period.setStatus(TaxFilingStatus.PAID);
        return periodRepository.save(period);
    }

    public Optional<TaxFilingPeriod> getById(Long id) {
        return periodRepository.findByIdAndCompanyId(id, companyContext.requireCompanyId());
    }

    @Transactional(readOnly = true)
    public Optional<TaxReturnReportData> getFiledReturnReport(Long periodId) {
        return periodRepository.findByIdAndCompanyId(periodId, companyContext.requireCompanyId())
                .filter(period -> period.getStatus() == TaxFilingStatus.FILED
                        || period.getStatus() == TaxFilingStatus.PAID)
                .map(period -> {
                    List<TaxReturnRowSnapshot> snapshots =
                            returnRowSnapshotRepository.findByPeriodIdOrderByDisplayOrderAsc(periodId);
                    boolean reconstructed = snapshots.isEmpty();
                    List<TaxReturnRowDTO> rows = reconstructed
                            ? getTaxReturnRows(period, getReturnLineBreakdown(period))
                            : snapshots.stream().map(snapshot -> new TaxReturnRowDTO(
                                    snapshot.getDescriptionKey(),
                                    snapshot.getLine(),
                                    snapshot.getAmount(),
                                    snapshot.getBalance(),
                                    snapshot.isTotal(),
                                    snapshot.isUnmapped())).toList();
                    Company company = period.getCompany();
                    String companyName = company.getLegalName() == null || company.getLegalName().isBlank()
                            ? company.getName() : company.getLegalName();
                    return new TaxReturnReportData(
                            companyName,
                            period.getAgency().getName(),
                            period.getPeriodStart(),
                            period.getPeriodEnd(),
                            rows,
                            reconstructed);
                });
    }

    public record TaxReturnReportData(
            String companyName,
            String agencyName,
            LocalDate periodStart,
            LocalDate periodEnd,
            List<TaxReturnRowDTO> rows,
            boolean reconstructed) {
    }

    public List<TaxFilingPeriod> getByAgency(Long agencyId) {
        return periodRepository.findByCompanyIdAndAgencyId(companyContext.requireCompanyId(), agencyId);
    }

    public List<TaxFilingPeriod> getAll() {
        return periodRepository.findAllByCompanyId(companyContext.requireCompanyId());
    }

    @Transactional
    public void cancel(Long periodId) {
        TaxFilingPeriod period = periodRepository.findByIdAndCompanyId(periodId, companyContext.requireCompanyId())
            .orElseThrow(() -> new IllegalArgumentException("Period not found: " + periodId));
        if (period.getStatus() == TaxFilingStatus.PAID) {
            throw new IllegalStateException("Cannot cancel a PAID period");
        }
        period.setStatus(TaxFilingStatus.CANCELLED);
        periodRepository.save(period);
    }

    public List<TaxContributionDTO> getTaxCollectedDetail(TaxAgency agency, LocalDate start, LocalDate end) {
        companyContext.requireCurrentCompany(agency);
        List<Invoice> invoices = invoiceRepository.findByCompanyIdAndInvoiceDateBetweenOrderByInvoiceDateDesc(
                companyContext.requireCompanyId(), start, end);
        List<TaxContributionDTO> details = new ArrayList<>();
        for (Invoice inv : invoices) {
            if (inv.getStatus() == InvoiceStatus.CANCELLED) continue;
            BigDecimal contribution = taxContribution(inv, agency);
            if (contribution.compareTo(BigDecimal.ZERO) != 0) {
                details.add(new TaxContributionDTO(
                    inv.getId(),
                    "INVOICE",
                    inv.getInvoiceNumber(),
                    inv.getInvoiceDate(),
                    inv.getCustomer() != null ? inv.getCustomer().getBusinessName() : "Unknown Customer",
                    contribution
                ));
            }
        }
        details.addAll(journalTaxDetails(agency, start, end, false));
        details.sort(Comparator.comparing(TaxContributionDTO::getDate).reversed());
        return details;
    }

    public List<TaxContributionDTO> getItcDetail(TaxAgency agency, LocalDate start, LocalDate end) {
        companyContext.requireCurrentCompany(agency);
        List<Bill> bills = billRepository.findByCompanyIdAndBillDateBetweenOrderByBillDateDesc(
                companyContext.requireCompanyId(), start, end);
        List<TaxContributionDTO> details = new ArrayList<>();
        for (Bill bill : bills) {
            if (bill.getStatus() == BillStatus.CANCELLED) continue;
            BigDecimal contribution = billTaxContribution(bill, agency);
            if (contribution.compareTo(BigDecimal.ZERO) != 0) {
                details.add(new TaxContributionDTO(
                    bill.getId(),
                    "BILL",
                    bill.getBillNumber(),
                    bill.getBillDate(),
                    bill.getVendor() != null ? bill.getVendor().getBusinessName() : "Unknown Vendor",
                    contribution
                ));
            }
        }
        for (CreditCardCharge charge : creditCardChargeRepository
                .findByCompanyIdAndChargeDateBetweenOrderByChargeDateDescIdDesc(
                        companyContext.requireCompanyId(), start, end)) {
            if (charge.getStatus() != CreditCardChargeStatus.POSTED) {
                continue;
            }
            BigDecimal contribution = creditCardChargeTaxContribution(charge, agency);
            if (contribution.compareTo(BigDecimal.ZERO) != 0) {
                details.add(new TaxContributionDTO(
                        charge.getId(), "CREDIT_CARD_CHARGE", "CC-" + charge.getId(),
                        charge.getChargeDate(),
                        charge.getVendor() == null ? "" : charge.getVendor().getBusinessName(),
                        contribution));
            }
        }
        for (WrittenCheque cheque : chequeRepository
                .findByCompanyIdAndChequeDateBetweenOrderByChequeDateDescIdDesc(
                        companyContext.requireCompanyId(), start, end)) {
            if (!isIssuedCheque(cheque)) {
                continue;
            }
            BigDecimal contribution = chequeTaxContribution(cheque, agency);
            if (contribution.compareTo(BigDecimal.ZERO) != 0) {
                details.add(new TaxContributionDTO(
                        cheque.getId(), "CHEQUE", cheque.getChequeNumber(), cheque.getChequeDate(),
                        cheque.getVendor() == null ? "" : cheque.getVendor().getBusinessName(),
                        contribution));
            }
        }
        details.addAll(journalTaxDetails(agency, start, end, true));
        details.sort(Comparator.comparing(TaxContributionDTO::getDate).reversed());
        return details;
    }

    public List<TaxReturnLineDTO> getReturnLineBreakdown(TaxFilingPeriod period) {
        Map<ReturnLineKey, BigDecimal> totals = new LinkedHashMap<>();
        TaxAgency agency = period.getAgency();
        List<TaxItem> agencyItems = taxCodeService.getItemsByAgency(agency.getId());
        Map<String, TaxCode> taxCodesByCode = new HashMap<>();
        for (TaxCode taxCode : taxCodeService.getAllCodes()) {
            taxCodesByCode.put(taxCode.getCode(), taxCode);
        }

        for (Invoice invoice : invoiceRepository.findByCompanyIdAndInvoiceDateBetweenOrderByInvoiceDateDesc(
                companyContext.requireCompanyId(), period.getPeriodStart(), period.getPeriodEnd())) {
            if (invoice.getStatus() == InvoiceStatus.CANCELLED) continue;
            TaxCode taxCode = taxCodesByCode.get(invoice.getTaxRegime());
            List<TaxItem> items = taxItemsFor(taxCode, agencyItems, agency, true);
            addDocumentReturnLines(totals, "SALES", agency, items, true,
                    invoice.getSubtotal(), invoice.getGstAmount(), invoice.getHstAmount(), invoice.getQstAmount());
        }

        for (Bill bill : billRepository.findByCompanyIdAndBillDateBetweenOrderByBillDateDesc(
                companyContext.requireCompanyId(), period.getPeriodStart(), period.getPeriodEnd())) {
            if (bill.getStatus() == BillStatus.CANCELLED) continue;
            String taxRegime = bill.getTaxRegime();
            if (taxRegime == null || taxRegime.isBlank()) {
                taxRegime = bill.getVendor() == null ? null : bill.getVendor().getProvince();
            }
            TaxCode taxCode = taxCodesByCode.get(taxRegime);
            List<TaxItem> items = taxItemsFor(taxCode, agencyItems, agency, false);
            addDocumentReturnLines(totals, "PURCHASES", agency, items, false,
                    bill.getSubtotal(), bill.getGstAmount(), bill.getHstAmount(), bill.getQstAmount());
        }

        for (CreditCardCharge charge : creditCardChargeRepository
                .findByCompanyIdAndChargeDateBetweenOrderByChargeDateDescIdDesc(
                        companyContext.requireCompanyId(), period.getPeriodStart(), period.getPeriodEnd())) {
            if (charge.getStatus() != CreditCardChargeStatus.POSTED) {
                continue;
            }
            List<TaxItem> items = taxItemsFor(
                    taxCodesByCode.get(charge.getTaxRegime()), agencyItems, agency, false);
            Map<String, BigDecimal> components = creditCardChargeTaxComponents(charge);
            addDocumentReturnLines(totals, "PURCHASES", agency, items, false,
                    charge.getNetAmount(), components.get("GST"), components.get("HST"), components.get("QST"));
        }

        for (WrittenCheque cheque : chequeRepository
                .findByCompanyIdAndChequeDateBetweenOrderByChequeDateDescIdDesc(
                        companyContext.requireCompanyId(), period.getPeriodStart(), period.getPeriodEnd())) {
            if (!isIssuedCheque(cheque)) {
                continue;
            }
            for (ChequeExpense expense : cheque.getExpenses()) {
                for (ChequeTaxContribution contribution : chequeTaxContributions(
                        expense, taxCodesByCode, agency)) {
                    addReturnLineAmount(totals, "PURCHASES",
                            contribution.item().getPurchaseReturnLine(), contribution.amount());
                }
            }
        }

        for (JournalTaxContribution contribution : journalTaxContributions(
                agency, period.getPeriodStart(), period.getPeriodEnd(), false)) {
            addReturnLineAmount(totals, "SALES", contribution.entry().getTaxItem().getSalesReturnLine(),
                    contribution.amount());
        }
        for (JournalTaxContribution contribution : journalTaxContributions(
                agency, period.getPeriodStart(), period.getPeriodEnd(), true)) {
            addReturnLineAmount(totals, "PURCHASES", contribution.entry().getTaxItem().getPurchaseReturnLine(),
                    contribution.amount());
        }

        return totals.entrySet().stream()
                .filter(entry -> entry.getValue().compareTo(BigDecimal.ZERO) != 0)
                .map(entry -> new TaxReturnLineDTO(
                        entry.getKey().type(), entry.getKey().returnLine(), entry.getValue()))
                .toList();
    }

    public boolean hasUnmappedReturnLineAmounts(TaxFilingPeriod period) {
        return hasUnmappedReturnLineAmounts(period, getReturnLineBreakdown(period));
    }

    public boolean hasUnmappedReturnLineAmounts(TaxFilingPeriod period, List<TaxReturnLineDTO> returnLines) {
        return returnLines.stream()
                .anyMatch(line -> line.amount().signum() != 0
                        && !isSupportedReturnLine(period.getAgency().getCode(), line));
    }

    public List<TaxReturnRowDTO> getTaxReturnRows(
            TaxFilingPeriod period, List<TaxReturnLineDTO> returnLines) {
        String agencyCode = period.getAgency().getCode();
        BigDecimal salesRevenue = sumSalesRevenue(period);
        BigDecimal gstHstCollected = returnLineAmount(returnLines, "SALES", "103")
                .add(returnLineAmount(returnLines, "SALES", "104"));
        BigDecimal gstHstItcs = returnLineAmount(returnLines, "PURCHASES", "106")
                .add(returnLineAmount(returnLines, "PURCHASES", "107"));
        BigDecimal qstCollected = returnLineAmount(returnLines, "SALES", "203")
                .add(returnLineAmount(returnLines, "SALES", "204"));
        BigDecimal qstItcs = returnLineAmount(returnLines, "PURCHASES", "206")
                .add(returnLineAmount(returnLines, "PURCHASES", "207"));

        BigDecimal gstHstTotal = gstHstCollected;
        BigDecimal gstHstItcTotal = gstHstItcs;
        BigDecimal netGstHst = gstHstTotal.subtract(gstHstItcTotal);
        BigDecimal gstHstOtherCredits = BigDecimal.ZERO;
        BigDecimal gstHstBalance = netGstHst.subtract(gstHstOtherCredits);
        BigDecimal gstHstPayable = gstHstBalance;

        BigDecimal qstTotal = qstCollected;
        BigDecimal qstItcTotal = qstItcs;
        BigDecimal netQst = qstTotal.subtract(qstItcTotal);
        BigDecimal qstOtherCredits = BigDecimal.ZERO;
        BigDecimal netQstPayable = netQst.subtract(qstOtherCredits);
        BigDecimal qstPayable = netQstPayable;

        List<TaxReturnRowDTO> rows = new ArrayList<>();
        rows.add(row("tax.detail.returnLine101", "101", salesRevenue, null, false));
        rows.add(row("tax.detail.returnLine103", "103",
                returnLineAmount(returnLines, "SALES", "103"), null, false));
        rows.add(row("tax.detail.returnLine104", "104",
                returnLineAmount(returnLines, "SALES", "104"), null, false));
        rows.add(row("tax.detail.returnLine105", "105", null, gstHstTotal, true));
        rows.add(row("tax.detail.returnLine106", "106",
                returnLineAmount(returnLines, "PURCHASES", "106"), null, false));
        rows.add(row("tax.detail.returnLine107", "107",
                returnLineAmount(returnLines, "PURCHASES", "107"), null, false));
        rows.add(row("tax.detail.returnLine108", "108", null, gstHstItcTotal, true));
        rows.add(row("tax.detail.returnLine109", "109", null, netGstHst, true));
        rows.add(row("tax.detail.returnLine110", "110", BigDecimal.ZERO, null, false));
        rows.add(row("tax.detail.returnLine111", "111", BigDecimal.ZERO, null, false));
        rows.add(row("tax.detail.returnLine112", "112", null, gstHstOtherCredits, true));
        rows.add(row("tax.detail.returnLine113", "113", null, gstHstBalance, true));
        rows.add(row("tax.detail.returnLine114", "114", BigDecimal.ZERO, null, false));
        rows.add(row("tax.detail.returnLine115", "115", BigDecimal.ZERO, null, false));
        rows.add(row("tax.detail.returnLine116", "116", null, gstHstPayable, true));
        rows.add(row("tax.detail.returnLine203", "203",
                returnLineAmount(returnLines, "SALES", "203"), null, false));
        rows.add(row("tax.detail.returnLine204", "204",
                returnLineAmount(returnLines, "SALES", "204"), null, false));
        rows.add(row("tax.detail.returnLine205", "205", null, qstTotal, true));
        rows.add(row("tax.detail.returnLine206", "206",
                returnLineAmount(returnLines, "PURCHASES", "206"), null, false));
        rows.add(row("tax.detail.returnLine207", "207",
                returnLineAmount(returnLines, "PURCHASES", "207"), null, false));
        rows.add(row("tax.detail.returnLine208", "208", null, qstItcTotal, true));
        rows.add(row("tax.detail.returnLine209", "209", null, netQst, true));
        rows.add(row("tax.detail.returnLine210", "210", BigDecimal.ZERO, null, false));
        rows.add(row("tax.detail.returnLine211", "211", BigDecimal.ZERO, null, false));
        rows.add(row("tax.detail.returnLine212", "212", null, qstOtherCredits, true));
        rows.add(row("tax.detail.returnLine213", "213", null, netQstPayable, true));
        rows.add(row("tax.detail.returnLine214", "214", BigDecimal.ZERO, null, false));
        rows.add(row("tax.detail.returnLine216", "216", null, qstPayable, true));
        rows.add(row("tax.detail.returnLinePayable", null, null,
                gstHstPayable.add(qstPayable), true));

        for (TaxReturnLineDTO line : returnLines) {
            if (!isSupportedReturnLine(agencyCode, line) && line.amount().signum() != 0) {
                rows.add(new TaxReturnRowDTO(
                        "tax.detail.returnLineUnmapped",
                        line.returnLine(),
                        line.amount(),
                        null,
                        false,
                        true));
            }
        }
        return rows;
    }

    public List<TaxReturnLineDetailDTO> getReturnLineDetails(TaxFilingPeriod period, String line) {
        if ("101".equals(line)) {
            return salesRevenueDetails(period);
        }
        if (Set.of("103", "104", "106", "107", "203", "204", "206", "207").contains(line)) {
            return sourceTaxLineDetails(period, line);
        }

        List<TaxReturnRowDTO> rows = getTaxReturnRows(period, getReturnLineBreakdown(period));
        return calculationLineDetails(line, rows);
    }

    private List<TaxReturnLineDetailDTO> salesRevenueDetails(TaxFilingPeriod period) {
        List<TaxReturnLineDetailDTO> details = new ArrayList<>();
        for (Invoice invoice : invoiceRepository.findByCompanyIdAndInvoiceDateBetweenOrderByInvoiceDateDesc(
                companyContext.requireCompanyId(), period.getPeriodStart(), period.getPeriodEnd())) {
            BigDecimal subtotal = invoice.getSubtotal();
            if (invoice.getStatus() == InvoiceStatus.CANCELLED
                    || subtotal == null || subtotal.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            details.add(new TaxReturnLineDetailDTO(
                    "INVOICE", invoice.getId(), invoice.getInvoiceDate(), invoice.getInvoiceNumber(),
                    invoice.getCustomer() == null ? "" : invoice.getCustomer().getBusinessName(),
                    null, subtotal));
        }

        for (GeneralJournal journal : journalService.getJournalsByDateRange(
                period.getPeriodStart(), period.getPeriodEnd())) {
            if (journal.getStatus() != JournalStatus.POSTED || !journalService.isDirectlyEntered(journal)) {
                continue;
            }
            for (JournalEntry entry : journal.getEntries()) {
                if (entry.getAccount() == null
                        || entry.getAccount().getAccountType() != AccountType.REVENUE) {
                    continue;
                }
                BigDecimal amount = zeroIfNull(entry.getCredit()).subtract(zeroIfNull(entry.getDebit()));
                if (amount.compareTo(BigDecimal.ZERO) == 0) {
                    continue;
                }
                String description = entry.getAccount().getAccountNumber() + " — "
                        + entry.getAccount().getAccountName();
                if (entry.getDescription() != null && !entry.getDescription().isBlank()) {
                    description += " — " + entry.getDescription();
                }
                details.add(new TaxReturnLineDetailDTO(
                        "JOURNAL", journal.getId(), journal.getJournalDate(),
                        journal.getJournalNumber() + " / " + entry.getLineNumber(),
                        description, null, amount));
            }
        }
        return details;
    }

    private List<TaxReturnLineDetailDTO> sourceTaxLineDetails(TaxFilingPeriod period, String line) {
        TaxAgency agency = period.getAgency();
        boolean sales = Set.of("103", "104", "203", "204").contains(line);
        List<TaxItem> agencyItems = taxCodeService.getItemsByAgency(agency.getId());
        Map<String, TaxCode> taxCodesByCode = new HashMap<>();
        for (TaxCode taxCode : taxCodeService.getAllCodes()) {
            taxCodesByCode.put(taxCode.getCode(), taxCode);
        }

        List<TaxReturnLineDetailDTO> details = new ArrayList<>();
        if (sales) {
            for (Invoice invoice : invoiceRepository.findByCompanyIdAndInvoiceDateBetweenOrderByInvoiceDateDesc(
                    companyContext.requireCompanyId(), period.getPeriodStart(), period.getPeriodEnd())) {
                if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
                    continue;
                }
                TaxCode taxCode = taxCodesByCode.get(invoice.getTaxRegime());
                List<TaxItem> items = taxItemsFor(taxCode, agencyItems, agency, true);
                addDocumentTaxLineDetails(details, "INVOICE", invoice.getId(), invoice.getInvoiceDate(),
                        invoice.getInvoiceNumber(),
                        invoice.getCustomer() == null ? "" : invoice.getCustomer().getBusinessName(),
                        line, agency, items, true, invoice.getSubtotal(), invoice.getGstAmount(),
                        invoice.getHstAmount(), invoice.getQstAmount());
            }
        } else {
            for (Bill bill : billRepository.findByCompanyIdAndBillDateBetweenOrderByBillDateDesc(
                    companyContext.requireCompanyId(), period.getPeriodStart(), period.getPeriodEnd())) {
                if (bill.getStatus() == BillStatus.CANCELLED) {
                    continue;
                }
                String taxRegime = bill.getTaxRegime();
                if (taxRegime == null || taxRegime.isBlank()) {
                    taxRegime = bill.getVendor() == null ? null : bill.getVendor().getProvince();
                }
                TaxCode taxCode = taxCodesByCode.get(taxRegime);
                List<TaxItem> items = taxItemsFor(taxCode, agencyItems, agency, false);
                addDocumentTaxLineDetails(details, "BILL", bill.getId(), bill.getBillDate(),
                        bill.getBillNumber(),
                        bill.getVendor() == null ? "" : bill.getVendor().getBusinessName(),
                        line, agency, items, false, bill.getSubtotal(), bill.getGstAmount(),
                        bill.getHstAmount(), bill.getQstAmount());
            }
            for (CreditCardCharge charge : creditCardChargeRepository
                    .findByCompanyIdAndChargeDateBetweenOrderByChargeDateDescIdDesc(
                            companyContext.requireCompanyId(), period.getPeriodStart(), period.getPeriodEnd())) {
                if (charge.getStatus() != CreditCardChargeStatus.POSTED) {
                    continue;
                }
                List<TaxItem> items = taxItemsFor(
                        taxCodesByCode.get(charge.getTaxRegime()), agencyItems, agency, false);
                Map<String, BigDecimal> components = creditCardChargeTaxComponents(charge);
                String vendorName = charge.getVendor() == null ? "" : charge.getVendor().getBusinessName();
                if (charge.getMemo() != null && !charge.getMemo().isBlank()) {
                    vendorName = vendorName.isBlank() ? charge.getMemo() : vendorName + " — " + charge.getMemo();
                }
                addDocumentTaxLineDetails(details, "CREDIT_CARD_CHARGE", charge.getId(),
                        charge.getChargeDate(), "CC-" + charge.getId(), vendorName,
                        line, agency, items, false, charge.getNetAmount(), components.get("GST"),
                        components.get("HST"), components.get("QST"));
            }
            for (WrittenCheque cheque : chequeRepository
                    .findByCompanyIdAndChequeDateBetweenOrderByChequeDateDescIdDesc(
                            companyContext.requireCompanyId(), period.getPeriodStart(), period.getPeriodEnd())) {
                if (!isIssuedCheque(cheque)) {
                    continue;
                }
                for (ChequeExpense expense : cheque.getExpenses()) {
                    for (ChequeTaxContribution contribution : chequeTaxContributions(
                            expense, taxCodesByCode, agency)) {
                        if (!line.equals(normalizeReturnLine(
                                contribution.item().getPurchaseReturnLine()))) {
                            continue;
                        }
                        String description = taxItemLabel(contribution.item());
                        if (cheque.getVendor() != null) {
                            description += " — " + cheque.getVendor().getBusinessName();
                        }
                        if (expense.getMemo() != null && !expense.getMemo().isBlank()) {
                            description += " — " + expense.getMemo();
                        }
                        details.add(new TaxReturnLineDetailDTO(
                                "CHEQUE", cheque.getId(), cheque.getChequeDate(), cheque.getChequeNumber(),
                                description, null, contribution.amount(), expense.getAmount(),
                                contribution.item().getRate()));
                    }
                }
            }
        }

        for (JournalTaxContribution contribution : journalTaxContributions(
                agency, period.getPeriodStart(), period.getPeriodEnd(), !sales)) {
            JournalEntry entry = contribution.entry();
            TaxItem item = entry.getTaxItem();
            String itemLine = sales ? item.getSalesReturnLine() : item.getPurchaseReturnLine();
            if (!line.equals(normalizeReturnLine(itemLine))
                    || contribution.amount().compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            GeneralJournal journal = entry.getJournal();
            details.add(new TaxReturnLineDetailDTO(
                    "JOURNAL", journal.getId(), journal.getJournalDate(),
                    journal.getJournalNumber() + " / " + entry.getLineNumber(),
                    entry.getAccount().getAccountNumber() + " — " + entry.getAccount().getAccountName()
                            + " — " + item.getCode(),
                    null, contribution.amount()));
        }
        return details;
    }

    private void addDocumentTaxLineDetails(
            List<TaxReturnLineDetailDTO> details,
            String sourceType,
            Long sourceId,
            LocalDate date,
            String documentNumber,
            String partyName,
            String targetLine,
            TaxAgency agency,
            List<TaxItem> sourceItems,
            boolean sales,
            BigDecimal subtotal,
            BigDecimal gst,
            BigDecimal hst,
            BigDecimal qst) {
        Map<String, BigDecimal> components = new LinkedHashMap<>();
        components.put("GST", zeroIfNull(gst));
        components.put("HST", zeroIfNull(hst));
        components.put("QST", zeroIfNull(qst));
        for (Map.Entry<String, BigDecimal> component : components.entrySet()) {
            if (!componentApplies(agency.getCode(), component.getKey())
                    || component.getValue().compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            List<TaxItem> componentItems = sourceItems.stream()
                    .filter(item -> item.getAgency() != null
                            && item.getAgency().getId().equals(agency.getId()))
                    .filter(item -> sales
                            ? Boolean.TRUE.equals(item.getForSales())
                            : Boolean.TRUE.equals(item.getForPurchases()))
                    .filter(item -> component.getKey().equals(taxComponent(item)))
                    .toList();
            for (ReturnLineAllocation allocation : returnLineAllocations(
                    component.getValue(), subtotal, componentItems, sales)) {
                if (!targetLine.equals(allocation.returnLine())
                        || allocation.amount().compareTo(BigDecimal.ZERO) == 0) {
                    continue;
                }
                String description = component.getKey();
                if (!allocation.taxItems().isBlank()) {
                    description += " — " + allocation.taxItems();
                }
                if (partyName != null && !partyName.isBlank()) {
                    description += " — " + partyName;
                }
                BigDecimal effectiveRate = subtotal == null || subtotal.signum() == 0
                        ? null
                        : allocation.amount().divide(subtotal, 5, RoundingMode.HALF_UP);
                details.add(new TaxReturnLineDetailDTO(
                        sourceType, sourceId, date, documentNumber, description, null,
                        allocation.amount(), subtotal, effectiveRate));
            }
        }
    }

    private List<TaxReturnLineDetailDTO> calculationLineDetails(String line, List<TaxReturnRowDTO> rows) {
        List<CalculationComponent> components = switch (line) {
            case "105" -> List.of(new CalculationComponent("103", false),
                    new CalculationComponent("104", false));
            case "108" -> List.of(new CalculationComponent("106", false),
                    new CalculationComponent("107", false));
            case "109" -> List.of(new CalculationComponent("105", false),
                    new CalculationComponent("108", true));
            case "112", "110", "111", "114", "115", "210", "211", "212", "214" -> List.of();
            case "113" -> List.of(new CalculationComponent("109", false),
                    new CalculationComponent("112", true));
            case "116" -> List.of(new CalculationComponent("113", false));
            case "205" -> List.of(new CalculationComponent("203", false),
                    new CalculationComponent("204", false));
            case "208" -> List.of(new CalculationComponent("206", false),
                    new CalculationComponent("207", false));
            case "209" -> List.of(new CalculationComponent("205", false),
                    new CalculationComponent("208", true));
            case "213" -> List.of(new CalculationComponent("209", false),
                    new CalculationComponent("212", true));
            case "216" -> List.of(new CalculationComponent("213", false));
            case "payable" -> List.of(new CalculationComponent("116", false),
                    new CalculationComponent("216", false));
            default -> throw new IllegalArgumentException("Unsupported tax return line: " + line);
        };

        List<TaxReturnLineDetailDTO> details = new ArrayList<>();
        for (CalculationComponent component : components) {
            TaxReturnRowDTO source = rows.stream()
                    .filter(row -> component.line().equals(row.line()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Tax return line not found: " + component.line()));
            BigDecimal amount = source.balance() == null ? zeroIfNull(source.amount()) : source.balance();
            if (component.subtract()) {
                amount = amount.negate();
            }
            details.add(new TaxReturnLineDetailDTO(
                    "CALCULATION", null, null, component.line(), null,
                    "tax.detail.formulaContribution", amount));
        }
        if (details.isEmpty()) {
            details.add(new TaxReturnLineDetailDTO(
                    "CALCULATION", null, null, null, null,
                    "tax.detail.noContributions", BigDecimal.ZERO));
        }
        return details;
    }

    private TaxReturnRowDTO row(
            String descriptionKey, String line, BigDecimal amount, BigDecimal balance, boolean total) {
        return new TaxReturnRowDTO(descriptionKey, line, amount, balance, total, false);
    }

    private BigDecimal returnLineAmount(List<TaxReturnLineDTO> lines, String type, String lineNumber) {
        return lines.stream()
                .filter(line -> type.equals(line.type()) && lineNumber.equals(line.returnLine()))
                .map(TaxReturnLineDTO::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal returnBalance(List<TaxReturnRowDTO> rows, String lineNumber) {
        return rows.stream()
                .filter(row -> lineNumber.equals(row.line()))
                .map(TaxReturnRowDTO::balance)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal returnBalanceByDescription(List<TaxReturnRowDTO> rows, String descriptionKey) {
        return rows.stream()
                .filter(row -> descriptionKey.equals(row.descriptionKey()))
                .map(TaxReturnRowDTO::balance)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    private boolean isSupportedReturnLine(String agencyCode, TaxReturnLineDTO line) {
        if (line.returnLine() == null) {
            return false;
        }
        return switch (agencyCode) {
            case CRA_CODE -> ("SALES".equals(line.type()) && Set.of("103", "104").contains(line.returnLine()))
                    || ("PURCHASES".equals(line.type()) && Set.of("106", "107").contains(line.returnLine()));
            case RQ_CODE -> ("SALES".equals(line.type()) && Set.of("203", "204").contains(line.returnLine()))
                    || ("PURCHASES".equals(line.type()) && Set.of("206", "207").contains(line.returnLine()));
            case MRQ2013_CODE -> ("SALES".equals(line.type())
                    && Set.of("103", "104", "203", "204").contains(line.returnLine()))
                    || ("PURCHASES".equals(line.type())
                    && Set.of("106", "107", "206", "207").contains(line.returnLine()));
            default -> false;
        };
    }

    private BigDecimal sumSalesRevenue(TaxFilingPeriod period) {
        BigDecimal total = invoiceRepository.findByCompanyIdAndInvoiceDateBetweenOrderByInvoiceDateDesc(
                        companyContext.requireCompanyId(), period.getPeriodStart(), period.getPeriodEnd()).stream()
                .filter(invoice -> invoice.getStatus() != InvoiceStatus.CANCELLED)
                .map(Invoice::getSubtotal)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        for (GeneralJournal journal : journalService.getJournalsByDateRange(
                period.getPeriodStart(), period.getPeriodEnd())) {
            if (journal.getStatus() != JournalStatus.POSTED || !journalService.isDirectlyEntered(journal)) {
                continue;
            }
            for (JournalEntry entry : journal.getEntries()) {
                if (entry.getAccount() == null
                        || entry.getAccount().getAccountType() != AccountType.REVENUE) {
                    continue;
                }
                BigDecimal debit = zeroIfNull(entry.getDebit());
                BigDecimal credit = zeroIfNull(entry.getCredit());
                total = total.add(credit.subtract(debit));
            }
        }
        return total;
    }

    private List<TaxItem> taxItemsFor(
            TaxCode taxCode, List<TaxItem> fallbackItems, TaxAgency agency, boolean sales) {
        List<TaxItem> groupItems = taxCode == null
                ? List.of()
                : taxItemsForGroup(taxCode, sales).stream()
                        .filter(item -> item.getAgency() != null
                                && java.util.Objects.equals(item.getAgency().getId(), agency.getId()))
                        .toList();
        return groupItems.isEmpty() ? fallbackItems : groupItems;
    }

    private List<TaxItem> taxItemsForGroup(TaxCode taxCode, boolean sales) {
        TaxGroup group = sales ? taxCode.getSalesTaxGroup() : taxCode.getPurchaseTaxGroup();
        return group == null ? List.of() : group.getTaxItems();
    }

    private Map<String, TaxCode> taxCodesByCode() {
        Map<String, TaxCode> taxCodes = new HashMap<>();
        for (TaxCode taxCode : taxCodeService.getAllCodes()) {
            taxCodes.put(taxCode.getCode(), taxCode);
        }
        return taxCodes;
    }

    private Map<String, BigDecimal> creditCardChargeTaxComponents(CreditCardCharge charge) {
        TaxRegime regime = billService.getRegimes().stream()
                .filter(candidate -> candidate.code().equals(charge.getTaxRegime()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Unsupported credit card tax regime: " + charge.getTaxRegime()));
        BigDecimal net = zeroIfNull(charge.getNetAmount());
        Map<String, BigDecimal> components = new LinkedHashMap<>();
        components.put("GST", net.multiply(regime.gstRate()).setScale(2, RoundingMode.HALF_UP));
        components.put("HST", net.multiply(regime.hstRate()).setScale(2, RoundingMode.HALF_UP));
        components.put("QST", net.multiply(regime.qstRate()).setScale(2, RoundingMode.HALF_UP));
        BigDecimal calculated = components.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal adjustment = zeroIfNull(charge.getTaxAmount()).subtract(calculated);
        if (components.get("HST").signum() > 0) {
            components.compute("HST", (key, amount) -> amount.add(adjustment));
        } else if (components.get("QST").signum() > 0) {
            components.compute("QST", (key, amount) -> amount.add(adjustment));
        } else if (components.get("GST").signum() > 0) {
            components.compute("GST", (key, amount) -> amount.add(adjustment));
        }
        return components;
    }

    private BigDecimal creditCardChargeTaxContribution(CreditCardCharge charge, TaxAgency agency) {
        return creditCardChargeTaxComponents(charge).entrySet().stream()
                .filter(component -> componentApplies(agency.getCode(), component.getKey()))
                .map(Map.Entry::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<ChequeTaxContribution> chequeTaxContributions(
            ChequeExpense expense, Map<String, TaxCode> taxCodesByCode, TaxAgency agency) {
        TaxCode taxCode = taxCodesByCode.get(expense.getTax());
        if (taxCode == null || taxCode.getPurchaseTaxGroup() == null
                || taxCode.getPurchaseTaxGroup().getTaxItems() == null) {
            return List.of();
        }
        List<ChequeTaxContribution> contributions = new ArrayList<>();
        for (TaxItem item : taxCode.getPurchaseTaxGroup().getTaxItems()) {
            if (Boolean.FALSE.equals(item.getForPurchases())
                    || item.getAgency() == null
                    || !java.util.Objects.equals(item.getAgency().getId(), agency.getId())) {
                continue;
            }
            BigDecimal amount = expense.getAmount().multiply(item.getRate()).setScale(2, RoundingMode.HALF_UP);
            if (amount.signum() > 0) {
                contributions.add(new ChequeTaxContribution(item, amount));
            }
        }
        return contributions;
    }

    private BigDecimal chequeTaxContribution(WrittenCheque cheque, TaxAgency agency) {
        Map<String, TaxCode> taxCodes = taxCodesByCode();
        return cheque.getExpenses().stream()
                .flatMap(expense -> chequeTaxContributions(expense, taxCodes, agency).stream())
                .map(ChequeTaxContribution::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean isIssuedCheque(WrittenCheque cheque) {
        return cheque.getStatus() == WrittenChequeStatus.ISSUED
                || cheque.getStatus() == WrittenChequeStatus.CLEARED;
    }

    private void addDocumentReturnLines(Map<ReturnLineKey, BigDecimal> totals,
                                        String type,
                                        TaxAgency agency,
                                        List<TaxItem> sourceItems,
                                        boolean sales,
                                        BigDecimal subtotal,
                                        BigDecimal gst,
                                        BigDecimal hst,
                                        BigDecimal qst) {
        Map<String, BigDecimal> amounts = new LinkedHashMap<>();
        amounts.put("GST", zeroIfNull(gst));
        amounts.put("HST", zeroIfNull(hst));
        amounts.put("QST", zeroIfNull(qst));
        for (Map.Entry<String, BigDecimal> component : amounts.entrySet()) {
            if (!componentApplies(agency.getCode(), component.getKey())
                    || component.getValue().compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            List<TaxItem> componentItems = sourceItems.stream()
                    .filter(item -> item.getAgency() != null
                            && item.getAgency().getId().equals(agency.getId()))
                    .filter(item -> sales
                            ? Boolean.TRUE.equals(item.getForSales())
                            : Boolean.TRUE.equals(item.getForPurchases()))
                    .filter(item -> component.getKey().equals(taxComponent(item)))
                    .toList();
            addComponentReturnLines(totals, type, component.getValue(), subtotal, componentItems, sales);
        }
    }

    private void addComponentReturnLines(Map<ReturnLineKey, BigDecimal> totals,
                                         String type,
                                         BigDecimal componentAmount,
                                         BigDecimal subtotal,
                                         List<TaxItem> items,
                                         boolean sales) {
        for (ReturnLineAllocation allocation : returnLineAllocations(
                componentAmount, subtotal, items, sales)) {
            addReturnLineAmount(totals, type, allocation.returnLine(), allocation.amount());
        }
    }

    private List<ReturnLineAllocation> returnLineAllocations(
            BigDecimal componentAmount, BigDecimal subtotal, List<TaxItem> items, boolean sales) {
        if (items.isEmpty()) {
            return List.of(new ReturnLineAllocation(null, componentAmount, ""));
        }

        List<BigDecimal> calculatedAmounts = new ArrayList<>();
        BigDecimal calculatedTotal = BigDecimal.ZERO;
        if (subtotal != null) {
            for (TaxItem item : items) {
                BigDecimal amount = subtotal.multiply(item.getRate()).setScale(2, RoundingMode.HALF_UP);
                calculatedAmounts.add(amount);
                calculatedTotal = calculatedTotal.add(amount);
            }
        }
        if (subtotal != null && calculatedTotal.compareTo(componentAmount) == 0) {
            List<ReturnLineAllocation> allocations = new ArrayList<>();
            for (int index = 0; index < items.size(); index++) {
                TaxItem item = items.get(index);
                allocations.add(new ReturnLineAllocation(
                        normalizeReturnLine(sales ? item.getSalesReturnLine() : item.getPurchaseReturnLine()),
                        calculatedAmounts.get(index), taxItemLabel(item)));
            }
            return allocations;
        }

        Set<String> configuredLines = items.stream()
                .map(item -> sales ? item.getSalesReturnLine() : item.getPurchaseReturnLine())
                .filter(line -> line != null && !line.isBlank())
                .map(String::trim)
                .collect(java.util.stream.Collectors.toSet());
        boolean allMapped = items.stream()
                .allMatch(item -> {
                    String line = sales ? item.getSalesReturnLine() : item.getPurchaseReturnLine();
                    return line != null && !line.isBlank();
                });
        if (allMapped && configuredLines.size() == 1) {
            return List.of(new ReturnLineAllocation(configuredLines.iterator().next(), componentAmount,
                    items.stream().map(this::taxItemLabel)
                            .collect(java.util.stream.Collectors.joining(", "))));
        }
        return List.of(new ReturnLineAllocation(null, componentAmount,
                items.stream().map(this::taxItemLabel)
                        .collect(java.util.stream.Collectors.joining(", "))));
    }

    private String normalizeReturnLine(String returnLine) {
        return returnLine == null || returnLine.isBlank() ? null : returnLine.trim();
    }

    private String taxItemLabel(TaxItem item) {
        return item.getCode() + " — " + item.getName();
    }

    private void addReturnLineAmount(Map<ReturnLineKey, BigDecimal> totals,
                                     String type,
                                     String returnLine,
                                     BigDecimal amount) {
        String normalizedLine = returnLine == null || returnLine.isBlank() ? null : returnLine.trim();
        ReturnLineKey key = new ReturnLineKey(type, normalizedLine);
        totals.merge(key, amount, BigDecimal::add);
    }

    private BigDecimal zeroIfNull(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private boolean componentApplies(String agencyCode, String component) {
        return switch (agencyCode) {
            case CRA_CODE -> component.equals("GST") || component.equals("HST");
            case RQ_CODE -> component.equals("QST");
            case MRQ2013_CODE -> component.equals("GST") || component.equals("QST");
            default -> false;
        };
    }

    private String taxComponent(TaxItem item) {
        String code = item.getCode().toUpperCase(Locale.ROOT);
        if (code.contains("HST") || code.contains("TVH")) return "HST";
        if (code.contains("TPS") || code.contains("GST")) return "GST";
        if (code.contains("TVQ") || code.contains("QST")) return "QST";
        return null;
    }

    private record ReturnLineKey(String type, String returnLine) {
    }

    private record ReturnLineAllocation(String returnLine, BigDecimal amount, String taxItems) {
    }

    private record CalculationComponent(String line, boolean subtract) {
    }

    private BigDecimal billTaxContribution(Bill bill, TaxAgency agency) {
        BigDecimal gst = bill.getGstAmount() == null ? BigDecimal.ZERO : bill.getGstAmount();
        BigDecimal hst = bill.getHstAmount() == null ? BigDecimal.ZERO : bill.getHstAmount();
        BigDecimal qst = bill.getQstAmount() == null ? BigDecimal.ZERO : bill.getQstAmount();
        return switch (agency.getCode()) {
            case CRA_CODE -> gst.add(hst);
            case RQ_CODE -> qst;
            case MRQ2013_CODE -> gst.add(qst);
            default -> BigDecimal.ZERO;
        };
    }

    /**
     * Sums tax collected for an agency by aggregating the relevant invoice fields in the period.
     * CRA collects TPS (gstAmount) and HST (hstAmount); Revenu Quebec collects TVQ (qstAmount).
     */
    public BigDecimal sumTaxCollected(TaxAgency agency, LocalDate start, LocalDate end) {
        companyContext.requireCurrentCompany(agency);
        List<Invoice> invoices = invoiceRepository.findByCompanyIdAndInvoiceDateBetweenOrderByInvoiceDateDesc(
                companyContext.requireCompanyId(), start, end);
        BigDecimal sum = BigDecimal.ZERO;
        for (Invoice inv : invoices) {
            if (inv.getStatus() == InvoiceStatus.CANCELLED) continue;
            sum = sum.add(taxContribution(inv, agency));
        }
        return sum.add(sumJournalTaxContributions(agency, start, end, false));
    }

    private BigDecimal taxContribution(Invoice inv, TaxAgency agency) {
        BigDecimal gst = inv.getGstAmount() == null ? BigDecimal.ZERO : inv.getGstAmount();
        BigDecimal hst = inv.getHstAmount() == null ? BigDecimal.ZERO : inv.getHstAmount();
        BigDecimal qst = inv.getQstAmount() == null ? BigDecimal.ZERO : inv.getQstAmount();
        return switch (agency.getCode()) {
            case CRA_CODE -> gst.add(hst);
            case RQ_CODE -> qst;
            case MRQ2013_CODE -> gst.add(qst);
            default -> BigDecimal.ZERO;
        };
    }

    /**
     * Sums the Input Tax Credits (CTI/RTI) for an agency from the purchase invoices (bills)
     * of the period. CRA recovers TPS + HST paid; Revenu Quebec recovers TVQ paid.
     */
    public BigDecimal sumItc(TaxAgency agency, LocalDate start, LocalDate end) {
        ItcComponents c = itcComponents(agency, start, end);
        return c.total();
    }

    private ItcComponents itcComponents(TaxAgency agency, LocalDate start, LocalDate end) {
        companyContext.requireCurrentCompany(agency);
        ItcComponents c = new ItcComponents();
        for (Bill bill : billRepository.findByCompanyIdAndBillDateBetweenOrderByBillDateDesc(
                companyContext.requireCompanyId(), start, end)) {
            if (bill.getStatus() == BillStatus.CANCELLED) continue;
            BigDecimal gst = bill.getGstAmount() == null ? BigDecimal.ZERO : bill.getGstAmount();
            BigDecimal hst = bill.getHstAmount() == null ? BigDecimal.ZERO : bill.getHstAmount();
            BigDecimal qst = bill.getQstAmount() == null ? BigDecimal.ZERO : bill.getQstAmount();
            switch (agency.getCode()) {
                case CRA_CODE -> { c.tps = c.tps.add(gst); c.hst = c.hst.add(hst); }
                case RQ_CODE -> c.tvq = c.tvq.add(qst);
                case MRQ2013_CODE -> { c.tps = c.tps.add(gst); c.tvq = c.tvq.add(qst); }
                default -> { /* no ITC for this agency */ }
            }
        }
        for (CreditCardCharge charge : creditCardChargeRepository
                .findByCompanyIdAndChargeDateBetweenOrderByChargeDateDescIdDesc(
                        companyContext.requireCompanyId(), start, end)) {
            if (charge.getStatus() != CreditCardChargeStatus.POSTED) {
                continue;
            }
            for (Map.Entry<String, BigDecimal> component : creditCardChargeTaxComponents(charge).entrySet()) {
                if (component.getValue().signum() > 0
                        && componentApplies(agency.getCode(), component.getKey())) {
                    c.addJournalItc(lookupAccount(creditCardTaxAccountNumber(component.getKey()),
                            "Credit card ITC account not seeded"), component.getValue());
                }
            }
        }
        Map<String, TaxCode> taxCodesByCode = taxCodesByCode();
        for (WrittenCheque cheque : chequeRepository
                .findByCompanyIdAndChequeDateBetweenOrderByChequeDateDescIdDesc(
                        companyContext.requireCompanyId(), start, end)) {
            if (!isIssuedCheque(cheque)) {
                continue;
            }
            for (ChequeExpense expense : cheque.getExpenses()) {
                for (ChequeTaxContribution contribution : chequeTaxContributions(
                        expense, taxCodesByCode, agency)) {
                    ChartOfAccount account = contribution.item().getItcAccount() == null
                            ? expense.getAccount() : contribution.item().getItcAccount();
                    c.addJournalItc(account, contribution.amount());
                }
            }
        }
        for (JournalTaxContribution contribution : journalTaxContributions(agency, start, end, true)) {
            TaxItem item = contribution.entry().getTaxItem();
            ChartOfAccount itcAccount = item.getItcAccount();
            if (itcAccount == null) {
                throw new IllegalStateException("Tax item " + item.getCode() + " has no ITC account");
            }
            c.addJournalItc(itcAccount, contribution.amount());
        }
        return c;
    }

    private List<TaxContributionDTO> journalTaxDetails(TaxAgency agency, LocalDate start,
                                                        LocalDate end, boolean purchases) {
        List<TaxContributionDTO> details = new ArrayList<>();
        for (JournalTaxContribution contribution : journalTaxContributions(agency, start, end, purchases)) {
            JournalEntry entry = contribution.entry();
            GeneralJournal journal = entry.getJournal();
            details.add(new TaxContributionDTO(
                    journal.getId(),
                    "JOURNAL",
                    journal.getJournalNumber() + " / " + entry.getLineNumber(),
                    journal.getJournalDate(),
                    entryPartyName(entry),
                    contribution.amount()
            ));
        }
        return details;
    }

    private BigDecimal sumJournalTaxContributions(TaxAgency agency, LocalDate start,
                                                    LocalDate end, boolean purchases) {
        return journalTaxContributions(agency, start, end, purchases).stream()
                .map(JournalTaxContribution::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<JournalTaxContribution> journalTaxContributions(
            TaxAgency agency, LocalDate start, LocalDate end, boolean purchases) {
        List<JournalTaxContribution> contributions = new ArrayList<>();
        for (GeneralJournal journal : journalService.getJournalsByDateRange(start, end)) {
            if (journal.getStatus() != JournalStatus.POSTED && journal.getStatus() != JournalStatus.REVERSED) {
                continue;
            }
            for (JournalEntry entry : journal.getEntries()) {
                TaxItem item = entry.getTaxItem();
                if (item == null || item.getAgency() == null || entry.getAccount() == null
                        || !java.util.Objects.equals(item.getAgency().getId(), agency.getId())
                        || isPurchaseTaxEntry(entry, item) != purchases) {
                    continue;
                }
                boolean supported = purchases
                        ? Boolean.TRUE.equals(item.getForPurchases())
                        : Boolean.TRUE.equals(item.getForSales());
                if (!supported) {
                    continue;
                }

                BigDecimal debit = entry.getDebit() == null ? BigDecimal.ZERO : entry.getDebit();
                BigDecimal credit = entry.getCredit() == null ? BigDecimal.ZERO : entry.getCredit();
                BigDecimal taxableBase = purchases ? debit.subtract(credit) : credit.subtract(debit);
                ChartOfAccount taxAccount = purchases ? item.getItcAccount() : item.getPayableAccount();
                boolean amountIsAlreadyTax = taxAccount != null && taxAccount.getId() != null
                        && entry.getAccount().getId() != null
                        && java.util.Objects.equals(taxAccount.getId(), entry.getAccount().getId());
                BigDecimal amount = amountIsAlreadyTax
                        ? taxableBase
                        : taxableBase.multiply(item.getRate()).setScale(2, RoundingMode.HALF_UP);
                if (amount.compareTo(BigDecimal.ZERO) != 0) {
                    contributions.add(new JournalTaxContribution(entry, amount));
                }
            }
        }
        return contributions;
    }

    private boolean isPurchaseTaxEntry(JournalEntry entry, TaxItem item) {
        if (entry.getCustomer() != null) {
            return false;
        }
        if (entry.getVendor() != null) {
            return true;
        }
        boolean forSales = Boolean.TRUE.equals(item.getForSales());
        boolean forPurchases = Boolean.TRUE.equals(item.getForPurchases());
        if (forSales != forPurchases) {
            return forPurchases;
        }
        return entry.getAccount().getAccountType() != AccountType.REVENUE;
    }

    private String entryPartyName(JournalEntry entry) {
        if (entry.getCustomer() != null) return entry.getCustomer().getBusinessName();
        if (entry.getVendor() != null) return entry.getVendor().getBusinessName();
        if (entry.getTaxAgency() != null) return entry.getTaxAgency().getName();
        return entry.getAccount().getAccountName();
    }

    private String creditCardTaxAccountNumber(String component) {
        return switch (component) {
            case "GST" -> "1300";
            case "HST" -> "1320";
            case "QST" -> "1310";
            default -> throw new IllegalArgumentException("Unsupported credit card tax component: " + component);
        };
    }

    private static class ItcComponents {
        BigDecimal tps = BigDecimal.ZERO;
        BigDecimal hst = BigDecimal.ZERO;
        BigDecimal tvq = BigDecimal.ZERO;
        final Map<Long, ItcAccountAmount> journalAmounts = new LinkedHashMap<>();

        void addJournalItc(ChartOfAccount account, BigDecimal amount) {
            journalAmounts.merge(account.getId(), new ItcAccountAmount(account, amount),
                    (existing, added) -> new ItcAccountAmount(
                            existing.account(), existing.amount().add(added.amount())));
        }

        BigDecimal total() {
            BigDecimal total = tps.add(hst).add(tvq);
            for (ItcAccountAmount amount : journalAmounts.values()) {
                total = total.add(amount.amount());
            }
            return total;
        }
    }

    private record ItcAccountAmount(ChartOfAccount account, BigDecimal amount) {
    }

    private record JournalTaxContribution(JournalEntry entry, BigDecimal amount) {
    }

    private record ChequeTaxContribution(TaxItem item, BigDecimal amount) {
    }

    private GeneralJournal buildItcAdjustmentJournal(
            TaxFilingPeriod period, List<TaxReturnRowDTO> returnRows) {
        ItcComponents c = itcComponents(period.getAgency(), period.getPeriodStart(), period.getPeriodEnd());
        BigDecimal total = c.total();
        boolean legacyCombinedAgency = MRQ2013_CODE.equals(period.getAgency().getCode());
        BigDecimal gstCollected = returnAmount(returnRows, "103").add(returnAmount(returnRows, "104"));
        BigDecimal qstCollected = returnAmount(returnRows, "203").add(returnAmount(returnRows, "204"));
        BigDecimal totalCollected = gstCollected.add(qstCollected);
        if (total.compareTo(BigDecimal.ZERO) == 0
                && (!legacyCombinedAgency || totalCollected.compareTo(BigDecimal.ZERO) == 0)) {
            return null; // nothing to clear
        }

        ChartOfAccount payable = lookupPayableAccount(period.getAgency());

        GeneralJournal jl = new GeneralJournal();
        jl.setJournalDate(LocalDate.now());
        jl.setNarrative("Sales tax return settlement for " + period.getAgency().getCode()
            + " filing " + period.getPeriodStart() + " to " + period.getPeriodEnd());
        jl.setReference("TAX-FILING-" + period.getId());

        List<JournalEntry> entries = new ArrayList<>();
        int line = 1;

        if (legacyCombinedAgency) {
            ChartOfAccount gstPayable = lookupAccount("2310", "TPS Payable account (2310) not seeded");
            ChartOfAccount qstPayable = lookupAccount("2320", "TVQ Payable account (2320) not seeded");
            line = addSignedJournalEntry(entries, gstPayable, gstCollected,
                    "Reclassify GST/HST collected for combined return", line);
            line = addSignedJournalEntry(entries, qstPayable, qstCollected,
                    "Reclassify QST collected for combined return", line);
            line = addSignedJournalEntry(entries, payable, totalCollected.negate(),
                    "Combined sales tax collected", line);
        }
        line = addSignedJournalEntry(entries, payable, total, "ITC/ITR claimed for period", line);

        Map<Long, ItcAccountAmount> accountsToClear = new LinkedHashMap<>();
        if (legacyCombinedAgency) {
            BigDecimal legacyAmount = c.tps.add(c.hst).add(c.tvq);
            if (legacyAmount.compareTo(BigDecimal.ZERO) != 0) {
                addItcAmount(accountsToClear,
                        lookupAccount("1330", "Compte TPS/TVH/TVQ_2013 non semé"), legacyAmount);
            }
        } else {
            if (c.tps.compareTo(BigDecimal.ZERO) != 0) addItcAmount(accountsToClear,
                    lookupAccount("1300", "TPS à recevoir (CTI) non semé"), c.tps);
            if (c.hst.compareTo(BigDecimal.ZERO) != 0) addItcAmount(accountsToClear,
                    lookupAccount("1320", "HST à recevoir (CTI) non semé"), c.hst);
            if (c.tvq.compareTo(BigDecimal.ZERO) != 0) addItcAmount(accountsToClear,
                    lookupAccount("1310", "TVQ à recevoir (RTI) non semé"), c.tvq);
        }
        for (ItcAccountAmount amount : c.journalAmounts.values()) {
            addItcAmount(accountsToClear, amount.account(), amount.amount());
        }
        for (ItcAccountAmount amount : accountsToClear.values()) {
            if (amount.amount().compareTo(BigDecimal.ZERO) == 0) continue;
            line = addSignedJournalEntry(entries, amount.account(), amount.amount().negate(),
                    "ITC/ITR claimed for period", line);
        }

        jl.setEntries(entries);
        return jl;
    }

    private int addSignedJournalEntry(
            List<JournalEntry> entries, ChartOfAccount account, BigDecimal debitAmount,
            String description, int lineNumber) {
        if (debitAmount.compareTo(BigDecimal.ZERO) == 0) {
            return lineNumber;
        }
        JournalEntry entry = new JournalEntry();
        entry.setAccount(account);
        entry.setDebit(debitAmount.signum() > 0 ? debitAmount : BigDecimal.ZERO);
        entry.setCredit(debitAmount.signum() < 0 ? debitAmount.abs() : BigDecimal.ZERO);
        entry.setDescription(description);
        entry.setLineNumber(lineNumber);
        entries.add(entry);
        return lineNumber + 1;
    }

    private BigDecimal returnAmount(List<TaxReturnRowDTO> rows, String lineNumber) {
        return rows.stream()
                .filter(row -> lineNumber.equals(row.line()))
                .map(TaxReturnRowDTO::amount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void addItcAmount(Map<Long, ItcAccountAmount> amounts, ChartOfAccount account, BigDecimal amount) {
        amounts.merge(account.getId(), new ItcAccountAmount(account, amount),
                (existing, added) -> new ItcAccountAmount(
                        existing.account(), existing.amount().add(added.amount())));
    }

    private GeneralJournal buildPaymentJournal(TaxFilingPeriod period, ChartOfAccount bank, LocalDate paymentDate) {
        ChartOfAccount payable = lookupPayableAccount(period.getAgency());
        BigDecimal amount = period.getNetOwing();

        GeneralJournal jl = new GeneralJournal();
        jl.setJournalDate(paymentDate == null ? LocalDate.now() : paymentDate);
        jl.setNarrative("Payment to " + period.getAgency().getName()
            + " for filing " + period.getPeriodStart() + " to " + period.getPeriodEnd());
        jl.setReference("TAX-PAYMENT-" + period.getId());

        List<JournalEntry> entries = new ArrayList<>();

        JournalEntry dr = new JournalEntry();
        dr.setAccount(payable);
        dr.setDebit(amount);
        dr.setCredit(BigDecimal.ZERO);
        dr.setDescription("Settle " + period.getAgency().getCode() + " payable");
        dr.setLineNumber(1);
        entries.add(dr);

        JournalEntry cr = new JournalEntry();
        cr.setAccount(bank);
        cr.setDebit(BigDecimal.ZERO);
        cr.setCredit(amount);
        cr.setDescription("Cash payment to " + period.getAgency().getCode());
        cr.setLineNumber(2);
        entries.add(cr);

        jl.setEntries(entries);
        return jl;
    }

    private ChartOfAccount lookupPayableAccount(TaxAgency agency) {
        return switch (agency.getCode()) {
            case CRA_CODE -> lookupAccount("2310", "TPS Payable account (2310) not seeded");
            case RQ_CODE -> lookupAccount("2320", "TVQ Payable account (2320) not seeded");
            case MRQ2013_CODE -> lookupAccount("2340", "TPS/TVQ 2013 payable account (2340) not seeded");
            default -> throw new IllegalStateException("No payable account mapping for agency " + agency.getCode());
        };
    }

    private ChartOfAccount lookupAccount(String number, String errorMsg) {
        return accountRepository.findByCompanyIdAndAccountNumber(companyContext.requireCompanyId(), number)
            .orElseThrow(() -> new IllegalStateException(errorMsg));
    }
}
