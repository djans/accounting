package com.cogitosum.service;

import com.cogitosum.entity.GeneralJournal;
import com.cogitosum.entity.JournalEntry;
import com.cogitosum.entity.JournalStatus;
import com.cogitosum.entity.GeneralLedger;
import com.cogitosum.repository.GeneralJournalRepository;
import com.cogitosum.repository.GeneralLedgerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class GeneralJournalService {

    @Autowired
    private GeneralJournalRepository generalJournalRepository;

    @Autowired
    private GeneralLedgerRepository generalLedgerRepository;

    @Autowired
    private GeneralLedgerService generalLedgerService;

    @Autowired
    private FiscalYearService fiscalYearService;

    @Transactional
    public GeneralJournal createJournal(GeneralJournal journal) {
        // Generate unique journal number
        if (journal.getJournalNumber() == null || journal.getJournalNumber().isEmpty()) {
            journal.setJournalNumber("JL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }

        if (journal.getJournalDate() == null) {
            journal.setJournalDate(LocalDate.now());
        }

        // Wire each entry back to its journal so JPA can cascade-save them.
        if (journal.getEntries() != null) {
            for (JournalEntry entry : journal.getEntries()) {
                entry.setJournal(journal);
            }
        }

        // Validate journal has entries
        if (journal.getEntries() == null || journal.getEntries().isEmpty()) {
            throw new IllegalArgumentException("Journal must have at least one entry");
        }

        // Validate journal is balanced (debits = credits)
        validateJournalBalance(journal);

        return generalJournalRepository.save(journal);
    }

    @Transactional
    public GeneralJournal postJournal(Long journalId, String postedBy) {
        Optional<GeneralJournal> journal = generalJournalRepository.findById(journalId);
        if (journal.isPresent()) {
            GeneralJournal jl = journal.get();

            // Can only post draft journals
            if (!jl.getStatus().equals(JournalStatus.DRAFT)) {
                throw new IllegalArgumentException("Only DRAFT journals can be posted");
            }

            // Reject postings dated within a closed fiscal year (period lock).
            if (fiscalYearService.isLocked(jl.getJournalDate())) {
                throw new IllegalStateException(
                    "Impossible de comptabiliser une écriture datée du " + jl.getJournalDate()
                    + " : l'exercice correspondant est clôturé");
            }

            // Validate balance before posting
            validateJournalBalance(jl);

            // Post to general ledger
            postToLedger(jl);

            // Update journal status
            jl.setStatus(JournalStatus.POSTED);
            jl.setPostedDate(LocalDateTime.now());
            jl.setPostedBy(postedBy);

            return generalJournalRepository.save(jl);
        }
        return null;
    }

    @Transactional
    public GeneralJournal updateJournal(Long id, GeneralJournal journal) {
        Optional<GeneralJournal> existingJournal = generalJournalRepository.findById(id);
        if (existingJournal.isPresent()) {
            GeneralJournal jl = existingJournal.get();

            // Can only update draft journals
            if (!jl.getStatus().equals(JournalStatus.DRAFT)) {
                throw new IllegalArgumentException("Only DRAFT journals can be updated");
            }

            jl.setNarrative(journal.getNarrative());
            jl.setReference(journal.getReference());
            jl.getEntries().clear();
            if (journal.getEntries() != null) {
                for (JournalEntry entry : journal.getEntries()) {
                    entry.setJournal(jl);
                    jl.getEntries().add(entry);
                }
            }

            // Validate balance
            validateJournalBalance(jl);

            return generalJournalRepository.save(jl);
        }
        return null;
    }

    @Transactional
    public GeneralJournal reverseJournal(Long journalId, String reversalReason) {
        Optional<GeneralJournal> journal = generalJournalRepository.findById(journalId);
        if (journal.isPresent()) {
            GeneralJournal originalJournal = journal.get();

            // Only posted journals can be reversed
            if (!originalJournal.getStatus().equals(JournalStatus.POSTED)) {
                throw new IllegalArgumentException("Only POSTED journals can be reversed");
            }

            // Create reversal journal
            GeneralJournal reversalJournal = new GeneralJournal();
            reversalJournal.setJournalNumber("REV-" + originalJournal.getJournalNumber());
            reversalJournal.setJournalDate(LocalDate.now());
            reversalJournal.setNarrative("Reversal of " + originalJournal.getJournalNumber() + ": " + reversalReason);
            reversalJournal.setReference("Reversal of " + originalJournal.getJournalNumber());

            // Reverse all entries (debit becomes credit and vice versa)
            for (JournalEntry entry : originalJournal.getEntries()) {
                JournalEntry reversalEntry = new JournalEntry();
                reversalEntry.setJournal(reversalJournal);
                reversalEntry.setAccount(entry.getAccount());
                reversalEntry.setDebit(entry.getCredit());
                reversalEntry.setCredit(entry.getDebit());
                reversalEntry.setDescription("Reversal: " + entry.getDescription());
                reversalEntry.setLineNumber(entry.getLineNumber());
                reversalJournal.getEntries().add(reversalEntry);
            }

            // Persist as DRAFT, then post it like a normal journal so audit fields are set.
            GeneralJournal savedReversal = createJournal(reversalJournal);
            GeneralJournal posted = postJournal(savedReversal.getId(), "system-reversal");

            // Mark original as reversed
            originalJournal.setStatus(JournalStatus.REVERSED);
            generalJournalRepository.save(originalJournal);

            return posted;
        }
        return null;
    }

    public Optional<GeneralJournal> getJournalById(Long id) {
        return generalJournalRepository.findById(id);
    }

    public Optional<GeneralJournal> getJournalByNumber(String journalNumber) {
        return generalJournalRepository.findByJournalNumber(journalNumber);
    }

    public List<GeneralJournal> getJournalsByStatus(JournalStatus status) {
        return generalJournalRepository.findByStatus(status);
    }

    public List<GeneralJournal> getJournalsByDateRange(LocalDate startDate, LocalDate endDate) {
        return generalJournalRepository.findByJournalDateBetween(startDate, endDate);
    }

    public List<GeneralJournal> getPostedJournalsByDateRange(LocalDate startDate, LocalDate endDate) {
        return generalJournalRepository.findByStatusAndJournalDateBetween(JournalStatus.POSTED, startDate, endDate);
    }

    public List<GeneralJournal> getAllJournals() {
        return generalJournalRepository.findAll();
    }

    @Transactional
    public void deleteJournal(Long id) {
        Optional<GeneralJournal> journal = generalJournalRepository.findById(id);
        if (journal.isPresent()) {
            if (!journal.get().getStatus().equals(JournalStatus.DRAFT)) {
                throw new IllegalArgumentException("Only DRAFT journals can be deleted");
            }
            generalJournalRepository.deleteById(id);
        }
    }

    private void validateJournalBalance(GeneralJournal journal) {
        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;

        for (JournalEntry entry : journal.getEntries()) {
            totalDebits = totalDebits.add(entry.getDebit() != null ? entry.getDebit() : BigDecimal.ZERO);
            totalCredits = totalCredits.add(entry.getCredit() != null ? entry.getCredit() : BigDecimal.ZERO);
        }

        // Check if balanced (within 2 decimal places precision)
        if (totalDebits.compareTo(totalCredits) != 0) {
            throw new IllegalArgumentException("Journal is not balanced. Total Debits: " + totalDebits + ", Total Credits: " + totalCredits);
        }
    }

    private void postToLedger(GeneralJournal journal) {
        for (JournalEntry entry : journal.getEntries()) {
            if (entry.getAccount() == null || entry.getAccount().getId() == null) {
                throw new IllegalArgumentException("Journal entry is missing an account reference");
            }

            // Defense-in-depth: if no GL row exists for this account yet, create one
            // so a posting can never silently drop entries.
            GeneralLedger gl = generalLedgerRepository.findByAccountId(entry.getAccount().getId())
                    .orElseGet(() -> generalLedgerService.createLedgerAccount(entry.getAccount()));

            gl.setDebitBalance(gl.getDebitBalance().add(entry.getDebit()));
            gl.setCreditBalance(gl.getCreditBalance().add(entry.getCredit()));
            generalLedgerRepository.save(gl);
        }
    }
}
