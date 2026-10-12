package com.cogitosum.service;

import com.cogitosum.entity.ChartOfAccount;
import com.cogitosum.entity.CreditNoteRefund;
import com.cogitosum.entity.GeneralJournal;
import com.cogitosum.entity.JournalEntry;
import com.cogitosum.repository.ChartOfAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class CreditNoteRefundPostingService {

    private static final String AR_ACCOUNT = "1100";

    private final GeneralJournalService journalService;
    private final ChartOfAccountRepository accountRepository;
    private final CurrentCompanyContext companyContext;

    public CreditNoteRefundPostingService(GeneralJournalService journalService,
                                          ChartOfAccountRepository accountRepository,
                                          CurrentCompanyContext companyContext) {
        this.journalService = journalService;
        this.accountRepository = accountRepository;
        this.companyContext = companyContext;
    }

    @Transactional
    public void postRefund(CreditNoteRefund refund) {
        var creditNote = refund.getCreditNote();
        GeneralJournal journal = new GeneralJournal();
        journal.setJournalDate(refund.getRefundDate());
        journal.setNarrative("Refund of credit note " + creditNote.getCreditNoteNumber()
                + " for invoice " + creditNote.getInvoice().getInvoiceNumber());
        journal.setReference("CREDIT-NOTE-REFUND-" + refund.getId());

        ChartOfAccount receivables = account(AR_ACCOUNT);
        JournalEntry debit = entry(receivables, refund.getAmount(), BigDecimal.ZERO,
                "Settle customer credit " + creditNote.getCreditNoteNumber(), 1);
        JournalEntry credit = entry(refund.getBankAccount(), BigDecimal.ZERO, refund.getAmount(),
                "Customer credit refund " + creditNote.getCreditNoteNumber(), 2);
        journal.setEntries(List.of(debit, credit));

        var saved = journalService.createJournal(journal);
        journalService.postJournal(saved.getId(), "auto-credit-note-refund");
    }

    private ChartOfAccount account(String accountNumber) {
        return accountRepository.findByCompanyIdAndAccountNumber(
                        companyContext.requireCompanyId(), accountNumber)
                .orElseThrow(() -> new IllegalStateException(
                        "Required account not seeded: " + accountNumber));
    }

    private JournalEntry entry(ChartOfAccount account, BigDecimal debit, BigDecimal credit,
                               String description, int line) {
        JournalEntry entry = new JournalEntry();
        entry.setAccount(account);
        entry.setDebit(debit);
        entry.setCredit(credit);
        entry.setDescription(description);
        entry.setLineNumber(line);
        return entry;
    }
}
