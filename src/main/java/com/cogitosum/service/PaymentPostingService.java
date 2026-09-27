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
 * Auto-posts a journal entry when a payment is recorded:
 *   Dr Bank Account (selected by user), Cr A/R.
 * On refund / delete the prior posting is reversed.
 */
@Service
public class PaymentPostingService {

    public static final String AR_ACCOUNT = "1100";

    @Autowired
    private GeneralJournalService journalService;

    @Autowired
    private GeneralJournalRepository journalRepository;

    @Autowired
    private ChartOfAccountRepository accountRepository;

    @Autowired
    private CurrentCompanyContext companyContext;

    @Transactional
    public void postPayment(Payment payment) {
        if (payment.getBankAccount() == null) return; // no bank → skip posting
        if (payment.getAmount() == null || payment.getAmount().compareTo(BigDecimal.ZERO) == 0) return;

        GeneralJournal jl = buildPaymentJournal(payment);
        GeneralJournal saved = journalService.createJournal(jl);
        journalService.postJournal(saved.getId(), "auto-payment");
    }

    @Transactional
    public void reversePayment(Payment payment, String reason) {
        Optional<GeneralJournal> existing = journalRepository.findAllByCompanyId(companyContext.requireCompanyId()).stream()
            .filter(j -> ("PAYMENT-" + payment.getId()).equals(j.getReference()))
            .filter(j -> j.getStatus() == JournalStatus.POSTED)
            .findFirst();
        existing.ifPresent(j -> journalService.reverseJournal(j.getId(), reason));
    }

    private GeneralJournal buildPaymentJournal(Payment payment) {
        ChartOfAccount ar = payment.getArAccount();
        if (ar == null) {
            ar = accountRepository.findByCompanyIdAndAccountNumber(companyContext.requireCompanyId(), AR_ACCOUNT)
                .orElseThrow(() -> new IllegalStateException("Accounts Receivable account (1100) not seeded"));
        }

        GeneralJournal jl = new GeneralJournal();
        jl.setJournalDate(payment.getPaymentDate() == null ? LocalDate.now() : payment.getPaymentDate());
        jl.setNarrative("Payment " + payment.getTransactionId()
            + " for invoice " + (payment.getInvoice() != null ? payment.getInvoice().getInvoiceNumber() : ""));
        jl.setReference("PAYMENT-" + payment.getId());

        List<JournalEntry> entries = new ArrayList<>();

        JournalEntry dr = new JournalEntry();
        dr.setAccount(payment.getBankAccount());
        dr.setDebit(payment.getAmount());
        dr.setCredit(BigDecimal.ZERO);
        dr.setDescription("Cash received via " + payment.getPaymentMethod());
        dr.setLineNumber(1);
        entries.add(dr);

        JournalEntry cr = new JournalEntry();
        cr.setAccount(ar);
        cr.setDebit(BigDecimal.ZERO);
        cr.setCredit(payment.getAmount());
        cr.setDescription("Settle A/R for invoice "
            + (payment.getInvoice() != null ? payment.getInvoice().getInvoiceNumber() : ""));
        cr.setLineNumber(2);
        entries.add(cr);

        jl.setEntries(entries);
        return jl;
    }
}
