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
 * Auto-creates and posts a journal entry whenever a bill is created or updated:
 *   Dr Expense/Asset account (per line), Dr TPS ITC, Dr TVQ ITC, Dr HST ITC, Cr Accounts Payable.
 * On update, reverses the prior posting and creates a fresh one.
 * On cancel, reverses without re-posting. Mirrors {@link InvoicePostingService}.
 */
@Service
public class BillPostingService {

    public static final String AP_ACCOUNT = "2000";       // Comptes fournisseurs
    public static final String TPS_ITC = "1300";          // TPS à recevoir (CTI)
    public static final String TVQ_ITC = "1310";          // TVQ à recevoir (RTI)
    public static final String HST_ITC = "1320";          // HST à recevoir (CTI)
    public static final String DEFAULT_EXPENSE = "5000";   // Coût des marchandises vendues — repli

    @Autowired
    private GeneralJournalService journalService;

    @Autowired
    private GeneralJournalRepository journalRepository;

    @Autowired
    private ChartOfAccountRepository accountRepository;

    @Transactional
    public void postBill(Bill bill) {
        if (bill.getStatus() == BillStatus.CANCELLED) return;

        GeneralJournal jl = buildBillJournal(bill);
        if (jl == null) return;
        GeneralJournal saved = journalService.createJournal(jl);
        journalService.postJournal(saved.getId(), "auto-bill");
    }

    @Transactional
    public void repostBill(Bill bill) {
        reversePriorPosting(bill, "Bill updated");
        postBill(bill);
    }

    @Transactional
    public void reverseBill(Bill bill, String reason) {
        reversePriorPosting(bill, reason);
    }

    private void reversePriorPosting(Bill bill, String reason) {
        Optional<GeneralJournal> existing = journalRepository.findAll().stream()
            .filter(j -> ("BILL-" + bill.getId()).equals(j.getReference()))
            .filter(j -> j.getStatus() == JournalStatus.POSTED)
            .findFirst();
        existing.ifPresent(j -> journalService.reverseJournal(j.getId(), reason));
    }

    private GeneralJournal buildBillJournal(Bill bill) {
        BigDecimal total = bill.getTotalAmount();
        if (total == null || total.compareTo(BigDecimal.ZERO) == 0) return null;

        ChartOfAccount ap = lookup(AP_ACCOUNT);

        GeneralJournal jl = new GeneralJournal();
        jl.setJournalDate(bill.getBillDate() == null ? LocalDate.now() : bill.getBillDate());
        jl.setNarrative("Bill " + bill.getBillNumber()
            + (bill.getVendor() != null ? " - " + bill.getVendor().getBusinessName() : ""));
        jl.setReference("BILL-" + bill.getId());

        List<JournalEntry> entries = new ArrayList<>();
        int line = 1;

        // Dr each line to its expense/asset account (fallback to a default expense account).
        ChartOfAccount fallback = lookup(DEFAULT_EXPENSE);
        for (BillLineItem item : bill.getLineItems()) {
            ChartOfAccount expense = item.getExpenseAccount() != null ? item.getExpenseAccount() : fallback;
            entries.add(entry(expense, item.calculateTotal(), BigDecimal.ZERO, item.getDescription(), line++));
        }

        BigDecimal gst = bill.getGstAmount() == null ? BigDecimal.ZERO : bill.getGstAmount();
        if (gst.compareTo(BigDecimal.ZERO) > 0) {
            entries.add(entry(lookup(TPS_ITC), gst, BigDecimal.ZERO, "TPS/GST payée (CTI)", line++));
        }
        BigDecimal qst = bill.getQstAmount() == null ? BigDecimal.ZERO : bill.getQstAmount();
        if (qst.compareTo(BigDecimal.ZERO) > 0) {
            entries.add(entry(lookup(TVQ_ITC), qst, BigDecimal.ZERO, "TVQ/QST payée (RTI)", line++));
        }
        BigDecimal hst = bill.getHstAmount() == null ? BigDecimal.ZERO : bill.getHstAmount();
        if (hst.compareTo(BigDecimal.ZERO) > 0) {
            entries.add(entry(lookup(HST_ITC), hst, BigDecimal.ZERO, "HST payée (CTI)", line++));
        }

        entries.add(entry(ap, BigDecimal.ZERO, total, "A/P for bill " + bill.getBillNumber(), line++));

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
