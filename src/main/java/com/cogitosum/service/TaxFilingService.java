package com.cogitosum.service;

import com.cogitosum.dto.TaxContributionDTO;
import com.cogitosum.entity.*;
import com.cogitosum.repository.BillRepository;
import com.cogitosum.repository.ChartOfAccountRepository;
import com.cogitosum.repository.InvoiceRepository;
import com.cogitosum.repository.TaxAgencyRepository;
import com.cogitosum.repository.TaxFilingPeriodRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class TaxFilingService {

    public static final String CRA_CODE = "CRA";
    public static final String RQ_CODE = "RQ";
    public static final String MRQ2013_CODE = "MRQ2013";

    @Autowired
    private TaxFilingPeriodRepository periodRepository;

    @Autowired
    private TaxAgencyRepository agencyRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private BillRepository billRepository;

    @Autowired
    private ChartOfAccountRepository accountRepository;

    @Autowired
    private GeneralJournalService journalService;

    @Autowired
    private CurrentCompanyContext companyContext;

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
    public TaxFilingPeriod file(Long periodId, BigDecimal itcAmount, String postedBy) {
        TaxFilingPeriod period = periodRepository.findByIdAndCompanyId(periodId, companyContext.requireCompanyId())
            .orElseThrow(() -> new IllegalArgumentException("Period not found: " + periodId));
        if (period.getStatus() != TaxFilingStatus.CALCULATED && period.getStatus() != TaxFilingStatus.OPEN) {
            throw new IllegalStateException("Period must be OPEN or CALCULATED to file. Current: " + period.getStatus());
        }
        BigDecimal collected = sumTaxCollected(period.getAgency(), period.getPeriodStart(), period.getPeriodEnd());
        // The provided itcAmount is an optional manual override; otherwise the CTI is derived
        // automatically from the purchase invoices (bills) of the period.
        BigDecimal itc = (itcAmount != null && itcAmount.compareTo(BigDecimal.ZERO) > 0)
            ? itcAmount
            : sumItc(period.getAgency(), period.getPeriodStart(), period.getPeriodEnd());
        BigDecimal net = collected.subtract(itc);

        period.setTaxCollected(collected);
        period.setTaxItc(itc);
        period.setNetOwing(net);
        period.setFiledDate(LocalDate.now());

        // Clear the CTI/RTI sitting in the receivable accounts against the tax payable:
        //   Dr Tax Payable / Cr TPS|TVQ|HST à recevoir, for the amounts accumulated by bills.
        GeneralJournal filingJournal = buildItcAdjustmentJournal(period);
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
        return details;
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
        return sum;
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
        return c.tps.add(c.hst).add(c.tvq);
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
        return c;
    }

    private static class ItcComponents {
        BigDecimal tps = BigDecimal.ZERO;
        BigDecimal hst = BigDecimal.ZERO;
        BigDecimal tvq = BigDecimal.ZERO;
    }

    private GeneralJournal buildItcAdjustmentJournal(TaxFilingPeriod period) {
        ItcComponents c = itcComponents(period.getAgency(), period.getPeriodStart(), period.getPeriodEnd());
        BigDecimal total = c.tps.add(c.hst).add(c.tvq);
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            return null; // nothing to clear
        }

        ChartOfAccount payable = lookupPayableAccount(period.getAgency());

        GeneralJournal jl = new GeneralJournal();
        jl.setJournalDate(LocalDate.now());
        jl.setNarrative("CTI/RTI claimed for " + period.getAgency().getCode()
            + " filing " + period.getPeriodStart() + " to " + period.getPeriodEnd());
        jl.setReference("TAX-FILING-" + period.getId());

        List<JournalEntry> entries = new ArrayList<>();
        int line = 1;

        // Dr the payable for the total CTI claimed.
        JournalEntry dr = new JournalEntry();
        dr.setAccount(payable);
        dr.setDebit(total);
        dr.setCredit(BigDecimal.ZERO);
        dr.setDescription("CTI/RTI claimed for period");
        dr.setLineNumber(line++);
        entries.add(dr);

        // Cr each ITC receivable account for the amount accumulated by bills.
        if (period.getAgency().getCode().equals(MRQ2013_CODE)) {
            if (total.compareTo(BigDecimal.ZERO) > 0) {
                entries.add(creditItc("1330", "Compte TPS/TVH/TVQ_2013 non semé", total, "TPS/TVQ 2013 récupérée", line++));
            }
        } else {
            if (c.tps.compareTo(BigDecimal.ZERO) > 0) {
                entries.add(creditItc("1300", "TPS à recevoir (CTI) non semé", c.tps, "TPS récupérée", line++));
            }
            if (c.hst.compareTo(BigDecimal.ZERO) > 0) {
                entries.add(creditItc("1320", "HST à recevoir (CTI) non semé", c.hst, "HST récupérée", line++));
            }
            if (c.tvq.compareTo(BigDecimal.ZERO) > 0) {
                entries.add(creditItc("1310", "TVQ à recevoir (RTI) non semé", c.tvq, "TVQ récupérée", line++));
            }
        }

        jl.setEntries(entries);
        return jl;
    }

    private JournalEntry creditItc(String accountNumber, String errorMsg, BigDecimal amount, String desc, int line) {
        JournalEntry cr = new JournalEntry();
        cr.setAccount(lookupAccount(accountNumber, errorMsg));
        cr.setDebit(BigDecimal.ZERO);
        cr.setCredit(amount);
        cr.setDescription(desc);
        cr.setLineNumber(line);
        return cr;
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
            default -> throw new IllegalStateException("No payable account mapping for agency " + agency.getCode());
        };
    }

    private ChartOfAccount lookupAccount(String number, String errorMsg) {
        return accountRepository.findByCompanyIdAndAccountNumber(companyContext.requireCompanyId(), number)
            .orElseThrow(() -> new IllegalStateException(errorMsg));
    }
}
