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
 * Auto-posts a journal entry when a bill payment is recorded:
 *   Dr Accounts Payable, Cr Bank Account (selected by user).
 * On refund / delete the prior posting is reversed. Mirrors {@link PaymentPostingService}.
 */
@Service
public class BillPaymentPostingService {

    public static final String AP_ACCOUNT = "2000";

    @Autowired
    private GeneralJournalService journalService;

    @Autowired
    private GeneralJournalRepository journalRepository;

    @Autowired
    private ChartOfAccountRepository accountRepository;

    @Transactional
    public void postPayment(BillPayment payment) {
        if (payment.getBankAccount() == null) return; // no bank → skip posting
        if (payment.getAmount() == null || payment.getAmount().compareTo(BigDecimal.ZERO) == 0) return;

        GeneralJournal jl = buildPaymentJournal(payment);
        GeneralJournal saved = journalService.createJournal(jl);
        journalService.postJournal(saved.getId(), "auto-bill-payment");
    }

    @Transactional
    public void reversePayment(BillPayment payment, String reason) {
        Optional<GeneralJournal> existing = journalRepository.findAll().stream()
            .filter(j -> ("BILLPAYMENT-" + payment.getId()).equals(j.getReference()))
            .filter(j -> j.getStatus() == JournalStatus.POSTED)
            .findFirst();
        existing.ifPresent(j -> journalService.reverseJournal(j.getId(), reason));
    }

    private GeneralJournal buildPaymentJournal(BillPayment payment) {
        ChartOfAccount ap = accountRepository.findByAccountNumber(AP_ACCOUNT)
            .orElseThrow(() -> new IllegalStateException("Accounts Payable account (2000) not seeded"));

        GeneralJournal jl = new GeneralJournal();
        jl.setJournalDate(payment.getPaymentDate() == null ? LocalDate.now() : payment.getPaymentDate());
        jl.setNarrative("Bill payment " + payment.getTransactionId()
            + " for bill " + (payment.getBill() != null ? payment.getBill().getBillNumber() : ""));
        jl.setReference("BILLPAYMENT-" + payment.getId());

        List<JournalEntry> entries = new ArrayList<>();

        JournalEntry dr = new JournalEntry();
        dr.setAccount(ap);
        dr.setDebit(payment.getAmount());
        dr.setCredit(BigDecimal.ZERO);
        dr.setDescription("Settle A/P for bill "
            + (payment.getBill() != null ? payment.getBill().getBillNumber() : ""));
        dr.setLineNumber(1);
        entries.add(dr);

        JournalEntry cr = new JournalEntry();
        cr.setAccount(payment.getBankAccount());
        cr.setDebit(BigDecimal.ZERO);
        cr.setCredit(payment.getAmount());
        cr.setDescription("Cash paid via " + payment.getPaymentMethod());
        cr.setLineNumber(2);
        entries.add(cr);

        jl.setEntries(entries);
        return jl;
    }
}
