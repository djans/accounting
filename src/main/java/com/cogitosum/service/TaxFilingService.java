package com.cogitosum.service;

import com.cogitosum.entity.*;
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

    @Autowired
    private TaxFilingPeriodRepository periodRepository;

    @Autowired
    private TaxAgencyRepository agencyRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private ChartOfAccountRepository accountRepository;

    @Autowired
    private GeneralJournalService journalService;

    @Transactional
    public TaxFilingPeriod createPeriod(Long agencyId, LocalDate start, LocalDate end) {
        TaxAgency agency = agencyRepository.findById(agencyId)
            .orElseThrow(() -> new IllegalArgumentException("Agency not found: " + agencyId));
        TaxFilingPeriod period = new TaxFilingPeriod();
        period.setAgency(agency);
        period.setPeriodStart(start);
        period.setPeriodEnd(end);
        period.setStatus(TaxFilingStatus.OPEN);
        return periodRepository.save(period);
    }

    @Transactional
    public TaxFilingPeriod calculate(Long periodId) {
        TaxFilingPeriod period = periodRepository.findById(periodId)
            .orElseThrow(() -> new IllegalArgumentException("Period not found: " + periodId));
        if (period.getStatus() == TaxFilingStatus.FILED || period.getStatus() == TaxFilingStatus.PAID) {
            throw new IllegalStateException("Cannot recalculate a filed or paid period");
        }
        BigDecimal collected = sumTaxCollected(period.getAgency(), period.getPeriodStart(), period.getPeriodEnd());
        period.setTaxCollected(collected);
        period.setNetOwing(collected.subtract(period.getTaxITC() == null ? BigDecimal.ZERO : period.getTaxITC()));
        period.setStatus(TaxFilingStatus.CALCULATED);
        return periodRepository.save(period);
    }

    @Transactional
    public TaxFilingPeriod file(Long periodId, BigDecimal itcAmount, String postedBy) {
        TaxFilingPeriod period = periodRepository.findById(periodId)
            .orElseThrow(() -> new IllegalArgumentException("Period not found: " + periodId));
        if (period.getStatus() != TaxFilingStatus.CALCULATED && period.getStatus() != TaxFilingStatus.OPEN) {
            throw new IllegalStateException("Period must be OPEN or CALCULATED to file. Current: " + period.getStatus());
        }
        BigDecimal collected = sumTaxCollected(period.getAgency(), period.getPeriodStart(), period.getPeriodEnd());
        BigDecimal itc = itcAmount == null ? BigDecimal.ZERO : itcAmount;
        BigDecimal net = collected.subtract(itc);

        period.setTaxCollected(collected);
        period.setTaxITC(itc);
        period.setNetOwing(net);
        period.setFiledDate(LocalDate.now());

        // Post the ITC adjustment if any — moves ITC value out of Tax Payable into Tax Adjustments.
        if (itc.compareTo(BigDecimal.ZERO) > 0) {
            GeneralJournal filingJournal = buildItcAdjustmentJournal(period, itc);
            GeneralJournal saved = journalService.createJournal(filingJournal);
            GeneralJournal posted = journalService.postJournal(saved.getId(), postedBy);
            period.setFilingJournal(posted);
        }

        period.setStatus(TaxFilingStatus.FILED);
        return periodRepository.save(period);
    }

    @Transactional
    public TaxFilingPeriod recordPayment(Long periodId, String bankAccountNumber, LocalDate paymentDate, String postedBy) {
        TaxFilingPeriod period = periodRepository.findById(periodId)
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

        ChartOfAccount bank = accountRepository.findByAccountNumber(bankAccountNumber)
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
        return periodRepository.findById(id);
    }

    public List<TaxFilingPeriod> getByAgency(Long agencyId) {
        return periodRepository.findByAgencyId(agencyId);
    }

    public List<TaxFilingPeriod> getAll() {
        return periodRepository.findAll();
    }

    @Transactional
    public void cancel(Long periodId) {
        TaxFilingPeriod period = periodRepository.findById(periodId)
            .orElseThrow(() -> new IllegalArgumentException("Period not found: " + periodId));
        if (period.getStatus() == TaxFilingStatus.PAID) {
            throw new IllegalStateException("Cannot cancel a PAID period");
        }
        period.setStatus(TaxFilingStatus.CANCELLED);
        periodRepository.save(period);
    }

    /**
     * Sums tax collected for an agency by aggregating the relevant invoice fields in the period.
     * CRA collects TPS (gstAmount) and HST (hstAmount); Revenu Quebec collects TVQ (qstAmount).
     */
    public BigDecimal sumTaxCollected(TaxAgency agency, LocalDate start, LocalDate end) {
        List<Invoice> invoices = invoiceRepository.findByInvoiceDateBetween(start, end);
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
            default -> BigDecimal.ZERO;
        };
    }

    private GeneralJournal buildItcAdjustmentJournal(TaxFilingPeriod period, BigDecimal itc) {
        ChartOfAccount payable = lookupPayableAccount(period.getAgency());
        ChartOfAccount adjustments = lookupAccount("4900", "Tax Adjustments account (4900) not seeded");

        GeneralJournal jl = new GeneralJournal();
        jl.setJournalDate(LocalDate.now());
        jl.setNarrative("ITC adjustment for " + period.getAgency().getCode()
            + " filing " + period.getPeriodStart() + " to " + period.getPeriodEnd());
        jl.setReference("TAX-FILING-" + period.getId());

        List<JournalEntry> entries = new ArrayList<>();

        JournalEntry dr = new JournalEntry();
        dr.setAccount(payable);
        dr.setDebit(itc);
        dr.setCredit(BigDecimal.ZERO);
        dr.setDescription("ITC claimed for period");
        dr.setLineNumber(1);
        entries.add(dr);

        JournalEntry cr = new JournalEntry();
        cr.setAccount(adjustments);
        cr.setDebit(BigDecimal.ZERO);
        cr.setCredit(itc);
        cr.setDescription("ITC adjustment income");
        cr.setLineNumber(2);
        entries.add(cr);

        jl.setEntries(entries);
        return jl;
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
        return accountRepository.findByAccountNumber(number)
            .orElseThrow(() -> new IllegalStateException(errorMsg));
    }
}
