package com.cogitosum.service;

import com.cogitosum.entity.*;
import com.cogitosum.repository.ChartOfAccountRepository;
import com.cogitosum.repository.GeneralJournalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Auto-creates and posts a journal entry whenever an invoice is created or updated:
 *   Dr A/R, Cr Sales Revenue, Cr TPS Payable, Cr TVQ Payable, Cr HST Payable.
 * On update, reverses the prior posting and creates a fresh one.
 * On cancel, reverses without re-posting.
 */
@Service
public class InvoicePostingService {

    public static final String AR_ACCOUNT = "1100";
    public static final String SALES_ACCOUNT = "4000";
    public static final String TPS_PAYABLE = "2310";
    public static final String TVQ_PAYABLE = "2320";
    public static final String HST_PAYABLE = "2330";

    @Autowired
    private GeneralJournalService journalService;

    @Autowired
    private GeneralJournalRepository journalRepository;

    @Autowired
    private ChartOfAccountRepository accountRepository;

    @Transactional
    public void postInvoice(Invoice invoice) {
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) return;

        GeneralJournal jl = buildInvoiceJournal(invoice);
        if (jl == null) return;
        GeneralJournal saved = journalService.createJournal(jl);
        journalService.postJournal(saved.getId(), "auto-invoice");
    }

    @Transactional
    public void repostInvoice(Invoice invoice) {
        reversePriorPosting(invoice, "Invoice updated");
        postInvoice(invoice);
    }

    @Transactional
    public void reverseInvoice(Invoice invoice, String reason) {
        reversePriorPosting(invoice, reason);
    }

    private void reversePriorPosting(Invoice invoice, String reason) {
        Optional<GeneralJournal> existing = journalRepository.findAll().stream()
            .filter(j -> ("INVOICE-" + invoice.getId()).equals(j.getReference()))
            .filter(j -> j.getStatus() == JournalStatus.POSTED)
            .findFirst();
        existing.ifPresent(j -> journalService.reverseJournal(j.getId(), reason));
    }

    private GeneralJournal buildInvoiceJournal(Invoice invoice) {
        BigDecimal total = invoice.getTotalAmount();
        if (total == null || total.compareTo(BigDecimal.ZERO) == 0) return null;

        ChartOfAccount ar = lookup(AR_ACCOUNT);
        ChartOfAccount sales = lookup(SALES_ACCOUNT);

        GeneralJournal jl = new GeneralJournal();
        jl.setJournalDate(invoice.getInvoiceDate() == null ? LocalDate.now() : invoice.getInvoiceDate());
        jl.setNarrative("Invoice " + invoice.getInvoiceNumber()
            + (invoice.getCustomer() != null ? " - " + invoice.getCustomer().getBusinessName() : ""));
        jl.setReference("INVOICE-" + invoice.getId());

        List<JournalEntry> entries = new ArrayList<>();
        int line = 1;

        entries.add(entry(ar, total, BigDecimal.ZERO, "A/R for invoice " + invoice.getInvoiceNumber(), line++));
        entries.add(entry(sales, BigDecimal.ZERO, invoice.getSubtotal(), "Sales revenue", line++));

        BigDecimal gst = invoice.getGstAmount() == null ? BigDecimal.ZERO : invoice.getGstAmount();
        if (gst.compareTo(BigDecimal.ZERO) > 0) {
            entries.add(entry(lookup(TPS_PAYABLE), BigDecimal.ZERO, gst, "TPS/GST collected", line++));
        }
        BigDecimal qst = invoice.getQstAmount() == null ? BigDecimal.ZERO : invoice.getQstAmount();
        if (qst.compareTo(BigDecimal.ZERO) > 0) {
            entries.add(entry(lookup(TVQ_PAYABLE), BigDecimal.ZERO, qst, "TVQ/QST collected", line++));
        }
        BigDecimal hst = invoice.getHstAmount() == null ? BigDecimal.ZERO : invoice.getHstAmount();
        if (hst.compareTo(BigDecimal.ZERO) > 0) {
            entries.add(entry(lookup(HST_PAYABLE), BigDecimal.ZERO, hst, "HST collected", line++));
        }

        jl.setEntries(entries);
        return jl;
    }

    private JournalEntry entry(ChartOfAccount acct, BigDecimal debit, BigDecimal credit, String desc, int line) {
        JournalEntry e = new JournalEntry();
        e.setAccount(acct);
        e.setDebit(debit);
        e.setCredit(credit);
        e.setDescription(desc);
        e.setLineNumber(line);
        return e;
    }

    private ChartOfAccount lookup(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
            .orElseThrow(() -> new IllegalStateException("Required account not seeded: " + accountNumber));
    }
}
