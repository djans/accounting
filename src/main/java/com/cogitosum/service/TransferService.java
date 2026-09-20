package com.cogitosum.service;

import com.cogitosum.entity.ChartOfAccount;
import com.cogitosum.entity.GeneralJournal;
import com.cogitosum.entity.JournalEntry;
import com.cogitosum.entity.Transfer;
import com.cogitosum.entity.AccountCategory;
import com.cogitosum.repository.TransferRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class TransferService {

    @Autowired private TransferRepository transferRepository;
    @Autowired private ChartOfAccountService accountService;
    @Autowired private GeneralJournalService generalJournalService;

    @Transactional
    public Transfer createTransfer(Long fromAccountId, Long toAccountId, BigDecimal amount, LocalDate transferDate, String notes, String postedBy) {
        ChartOfAccount from = accountService.getAccountById(fromAccountId).orElseThrow(() -> new IllegalArgumentException("From account not found"));
        ChartOfAccount to = accountService.getAccountById(toAccountId).orElseThrow(() -> new IllegalArgumentException("To account not found"));
        if (from.getId().equals(to.getId())) {
            throw new IllegalArgumentException("Source and destination accounts must be different");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Transfer amount must be greater than zero");
        }
        if (!isTransferAccount(from) || !isTransferAccount(to)) {
            throw new IllegalArgumentException("Transfers are limited to bank and credit card accounts");
        }

        GeneralJournal journal = new GeneralJournal();
        journal.setJournalDate(transferDate != null ? transferDate : LocalDate.now());
        journal.setNarrative("Transfer from " + from.getAccountNumber() + " to " + to.getAccountNumber());
        journal.setReference("Transfer");

        JournalEntry e1 = new JournalEntry();
        e1.setAccount(to);
        e1.setDebit(amount);
        e1.setCredit(java.math.BigDecimal.ZERO);
        e1.setLineNumber(1);

        JournalEntry e2 = new JournalEntry();
        e2.setAccount(from);
        e2.setDebit(java.math.BigDecimal.ZERO);
        e2.setCredit(amount);
        e2.setLineNumber(2);

        journal.getEntries().add(e1);
        journal.getEntries().add(e2);

        GeneralJournal saved = generalJournalService.createJournal(journal);
        GeneralJournal posted = generalJournalService.postJournal(saved.getId(), postedBy != null ? postedBy : "portal");

        Transfer t = new Transfer();
        t.setFromAccount(from);
        t.setToAccount(to);
        t.setAmount(amount);
        t.setTransferDate(posted.getJournalDate());
        t.setNotes(notes);
        t.setJournal(posted);

        return transferRepository.save(t);
    }

    public List<Transfer> getAllTransfers() {
        return transferRepository.findAll();
    }

    private boolean isTransferAccount(ChartOfAccount account) {
        return account.getAccountType() == com.cogitosum.entity.AccountType.ASSET
                || account.getAccountType() == com.cogitosum.entity.AccountType.LIABILITY
                || account.getAccountType() == com.cogitosum.entity.AccountType.EQUITY
                || account.getCategory() == AccountCategory.BANK
                || account.getCategory() == AccountCategory.CREDIT_CARD
                ;
    }
}
