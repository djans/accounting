package com.cogitosum.service;

import com.cogitosum.entity.AccountCategory;
import com.cogitosum.entity.AccountType;
import com.cogitosum.entity.ChartOfAccount;
import com.cogitosum.entity.GeneralJournal;
import com.cogitosum.entity.JournalEntry;
import com.cogitosum.entity.JournalStatus;
import com.cogitosum.entity.Transfer;
import com.cogitosum.entity.TransferStatus;
import com.cogitosum.repository.GeneralJournalRepository;
import com.cogitosum.repository.TransferRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class TransferService {

    private final TransferRepository transferRepository;
    private final ChartOfAccountService accountService;
    private final GeneralJournalService generalJournalService;
    private final GeneralJournalRepository generalJournalRepository;
    private final CurrentCompanyContext companyContext;

    public TransferService(TransferRepository transferRepository, ChartOfAccountService accountService,
                           GeneralJournalService generalJournalService,
                           GeneralJournalRepository generalJournalRepository,
                           CurrentCompanyContext companyContext) {
        this.transferRepository = transferRepository;
        this.accountService = accountService;
        this.generalJournalService = generalJournalService;
        this.generalJournalRepository = generalJournalRepository;
        this.companyContext = companyContext;
    }

    public List<Transfer> getAllTransfers() {
        return transferRepository.findAllByCompanyIdOrderByTransferDateDescIdDesc(
                companyContext.requireCompanyId());
    }

    public Optional<Transfer> findById(Long id) {
        return transferRepository.findByIdAndCompanyId(id, companyContext.requireCompanyId());
    }

    public Transfer getDraftForEdit(Long id) {
        Transfer transfer = findById(id).orElseThrow(() -> new IllegalArgumentException("Transfer not found"));
        requireStatus(transfer, TransferStatus.DRAFT, "Only draft transfers can be edited");
        return transfer;
    }

    @Transactional
    public Transfer saveDraft(Long fromAccountId, Long toAccountId, BigDecimal amount,
                              LocalDate transferDate, String notes) {
        Transfer transfer = new Transfer();
        transfer.setCompany(companyContext.requireCompany());
        updateDetails(transfer, fromAccountId, toAccountId, amount, transferDate, notes);
        transfer.setStatus(TransferStatus.DRAFT);
        return transferRepository.save(transfer);
    }

    @Transactional
    public Transfer updateDraft(Long id, Long fromAccountId, Long toAccountId, BigDecimal amount,
                                LocalDate transferDate, String notes) {
        Long companyId = companyContext.requireCompanyId();
        Transfer transfer = transferRepository.findLockedByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found"));
        requireStatus(transfer, TransferStatus.DRAFT, "Only draft transfers can be edited");
        updateDetails(transfer, fromAccountId, toAccountId, amount, transferDate, notes);
        return transferRepository.save(transfer);
    }

    @Transactional
    public void deleteDraft(Long id) {
        Transfer transfer = transferRepository.findLockedByIdAndCompanyId(
                        id, companyContext.requireCompanyId())
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found"));
        requireStatus(transfer, TransferStatus.DRAFT, "Only draft transfers can be deleted");
        transferRepository.delete(transfer);
    }

    @Transactional
    public Transfer postTransfer(Long id, String postedBy) {
        Transfer transfer = transferRepository.findLockedByIdAndCompanyId(
                        id, companyContext.requireCompanyId())
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found"));
        requireStatus(transfer, TransferStatus.DRAFT, "Only draft transfers can be posted");
        updateDetails(transfer, transfer.getFromAccount().getId(), transfer.getToAccount().getId(),
                transfer.getAmount(), transfer.getTransferDate(), transfer.getNotes());

        GeneralJournal journal = new GeneralJournal();
        journal.setJournalDate(transfer.getTransferDate());
        journal.setNarrative("Transfer from " + transfer.getFromAccount().getAccountNumber()
                + " to " + transfer.getToAccount().getAccountNumber());
        journal.setReference("TRANSFER-" + transfer.getId());

        JournalEntry debit = new JournalEntry();
        debit.setAccount(transfer.getToAccount());
        debit.setDebit(transfer.getAmount());
        debit.setCredit(BigDecimal.ZERO);
        debit.setLineNumber(1);

        JournalEntry credit = new JournalEntry();
        credit.setAccount(transfer.getFromAccount());
        credit.setDebit(BigDecimal.ZERO);
        credit.setCredit(transfer.getAmount());
        credit.setLineNumber(2);

        journal.getEntries().add(debit);
        journal.getEntries().add(credit);

        GeneralJournal savedJournal = generalJournalService.createJournal(journal);
        GeneralJournal postedJournal = generalJournalService.postJournal(
                savedJournal.getId(), postedBy == null || postedBy.isBlank() ? "portal" : postedBy);
        if (postedJournal == null) {
            throw new IllegalStateException("The transfer journal could not be posted");
        }

        transfer.setJournal(postedJournal);
        transfer.setStatus(TransferStatus.POSTED);
        return transferRepository.save(transfer);
    }

    @Transactional
    public Transfer voidTransfer(Long id, String reason) {
        Transfer transfer = transferRepository.findLockedByIdAndCompanyId(
                        id, companyContext.requireCompanyId())
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found"));
        requireStatus(transfer, TransferStatus.POSTED, "Only posted transfers can be reversed");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required to reverse a transfer");
        }
        String voidReason = reason.trim();
        if (voidReason.length() > 500) {
            throw new IllegalArgumentException("The reversal reason cannot exceed 500 characters");
        }
        if (transfer.getJournal() == null || transfer.getJournal().getId() == null) {
            throw new IllegalStateException("The transfer's posted journal entry was not found");
        }

        GeneralJournal journal = generalJournalRepository.findByIdAndCompanyId(
                        transfer.getJournal().getId(), companyContext.requireCompanyId())
                .orElseThrow(() -> new IllegalStateException("The transfer's posted journal entry was not found"));
        if (journal.getStatus() != JournalStatus.POSTED) {
            throw new IllegalStateException("Only a posted transfer journal can be reversed");
        }
        if (journal.getEntries().stream().anyMatch(JournalEntry::isCleared)) {
            throw new IllegalStateException("A reconciled transfer cannot be reversed");
        }

        GeneralJournal reversal = generalJournalService.reverseJournal(
                journal.getId(), "Transfer reversed: " + voidReason);
        if (reversal == null) {
            throw new IllegalStateException("The transfer's reversing journal entry could not be created");
        }
        transfer.setStatus(TransferStatus.REVERSED);
        transfer.setVoidedAt(LocalDateTime.now());
        transfer.setVoidReason(voidReason);
        return transferRepository.save(transfer);
    }

    private void updateDetails(Transfer transfer, Long fromAccountId, Long toAccountId, BigDecimal amount,
                               LocalDate transferDate, String notes) {
        ChartOfAccount from = accountService.getAccountById(fromAccountId)
                .orElseThrow(() -> new IllegalArgumentException("From account not found"));
        ChartOfAccount to = accountService.getAccountById(toAccountId)
                .orElseThrow(() -> new IllegalArgumentException("To account not found"));
        if (from.getId().equals(to.getId())) {
            throw new IllegalArgumentException("Source and destination accounts must be different");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Transfer amount must be greater than zero");
        }
        if (transferDate == null) {
            throw new IllegalArgumentException("A transfer date is required");
        }
        if (!isTransferAccount(from) || !isTransferAccount(to)) {
            throw new IllegalArgumentException("Transfers are limited to bank and credit card accounts");
        }

        transfer.setFromAccount(from);
        transfer.setToAccount(to);
        transfer.setAmount(amount);
        transfer.setTransferDate(transferDate);
        transfer.setNotes(notes);
    }

    private boolean isTransferAccount(ChartOfAccount account) {
        return account.getAccountType() == AccountType.ASSET
                || account.getAccountType() == AccountType.LIABILITY
                || account.getAccountType() == AccountType.EQUITY
                || account.getCategory() == AccountCategory.BANK
                || account.getCategory() == AccountCategory.CREDIT_CARD;
    }

    private void requireStatus(Transfer transfer, TransferStatus expected, String message) {
        if (transfer.getStatus() != expected) {
            throw new IllegalStateException(message);
        }
    }
}
