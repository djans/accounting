package com.cogitosum.service;

import com.cogitosum.entity.ChartOfAccount;
import com.cogitosum.entity.CreditNote;
import com.cogitosum.entity.GeneralJournal;
import com.cogitosum.entity.JournalEntry;
import com.cogitosum.repository.ChartOfAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class CreditNotePostingService {

    private final GeneralJournalService journalService;
    private final ChartOfAccountRepository accountRepository;
    private final CurrentCompanyContext companyContext;

    public CreditNotePostingService(GeneralJournalService journalService,
                                    ChartOfAccountRepository accountRepository,
                                    CurrentCompanyContext companyContext) {
        this.journalService = journalService;
        this.accountRepository = accountRepository;
        this.companyContext = companyContext;
    }

    @Transactional
    public void postCreditNote(CreditNote creditNote) {
        GeneralJournal journal = new GeneralJournal();
        journal.setJournalDate(creditNote.getCreditNoteDate());
        journal.setNarrative("Credit note " + creditNote.getCreditNoteNumber()
                + " - invoice " + creditNote.getInvoice().getInvoiceNumber());
        journal.setReference("CREDIT-NOTE-" + creditNote.getId());

        List<JournalEntry> entries = new ArrayList<>();
        int line = 1;
        entries.add(entry(InvoicePostingService.SALES_ACCOUNT, creditNote.getSubtotal(),
                BigDecimal.ZERO, "Sales revenue reversal", line++));
        line = addTaxEntry(entries, InvoicePostingService.TPS_PAYABLE, creditNote.getGstAmount(),
                "TPS/GST reversal", line);
        line = addTaxEntry(entries, InvoicePostingService.TVQ_PAYABLE, creditNote.getQstAmount(),
                "TVQ/QST reversal", line);
        line = addTaxEntry(entries, InvoicePostingService.HST_PAYABLE, creditNote.getHstAmount(),
                "HST reversal", line);
        entries.add(entry(InvoicePostingService.AR_ACCOUNT, BigDecimal.ZERO, creditNote.getTotalAmount(),
                "Accounts receivable reversal", line));
        journal.setEntries(entries);
        var saved = journalService.createJournal(journal);
        journalService.postJournal(saved.getId(), "auto-credit-note");
    }

    private int addTaxEntry(List<JournalEntry> entries, String accountNumber,
                            BigDecimal amount, String description, int line) {
        if (amount != null && amount.signum() > 0) {
            entries.add(entry(accountNumber, amount, BigDecimal.ZERO, description, line++));
        }
        return line;
    }

    private JournalEntry entry(String accountNumber, BigDecimal debit, BigDecimal credit,
                               String description, int line) {
        ChartOfAccount account = accountRepository.findByCompanyIdAndAccountNumber(
                        companyContext.requireCompanyId(), accountNumber)
                .orElseThrow(() -> new IllegalStateException("Required account not seeded: " + accountNumber));
        JournalEntry entry = new JournalEntry();
        entry.setAccount(account);
        entry.setDebit(debit);
        entry.setCredit(credit);
        entry.setDescription(description);
        entry.setLineNumber(line);
        return entry;
    }
}
