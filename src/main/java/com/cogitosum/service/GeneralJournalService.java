package com.cogitosum.service;

import com.cogitosum.entity.GeneralJournal;
import com.cogitosum.entity.JournalEntry;
import com.cogitosum.entity.JournalStatus;
import com.cogitosum.entity.GeneralLedger;
import com.cogitosum.repository.CustomerRepository;
import com.cogitosum.repository.GeneralJournalRepository;
import com.cogitosum.repository.GeneralLedgerRepository;
import com.cogitosum.repository.ChartOfAccountRepository;
import com.cogitosum.repository.TaxAgencyRepository;
import com.cogitosum.repository.TaxItemRepository;
import com.cogitosum.repository.VendorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class GeneralJournalService {
    private static final List<String> SYSTEM_REFERENCE_PREFIXES = List.of(
            "BILLPAYMENT-", "BILL-", "CHEQUE-", "CARD-CHARGE-", "FY-CLOSE-",
            "REVERSAL OF ", "INVOICE-", "PAYMENT-", "TAX-FILING-", "TAX-PAYMENT-", "TRANSFER-");

    @Autowired
    private GeneralJournalRepository generalJournalRepository;

    @Autowired
    private GeneralLedgerRepository generalLedgerRepository;

    @Autowired
    private GeneralLedgerService generalLedgerService;

    @Autowired
    private FiscalYearService fiscalYearService;

    @Autowired
    private ChartOfAccountRepository chartOfAccountRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private TaxAgencyRepository taxAgencyRepository;

    @Autowired
    private TaxItemRepository taxItemRepository;

    @Autowired
    private CurrentCompanyContext companyContext;

    @Transactional
    public GeneralJournal createJournal(GeneralJournal journal) {
        Long companyId = companyContext.requireCompanyId();
        journal.setCompany(companyContext.requireCompany());
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
                entry.setAccount(resolveAccount(entry.getAccount(), companyId));
                resolveEntryReferences(entry, companyId);
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
        Optional<GeneralJournal> journal = generalJournalRepository.findByIdAndCompanyId(
                journalId, companyContext.requireCompanyId());
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
        Long companyId = companyContext.requireCompanyId();
        Optional<GeneralJournal> existingJournal = generalJournalRepository.findLockedByIdAndCompanyId(id, companyId);
        if (existingJournal.isPresent()) {
            GeneralJournal jl = existingJournal.get();

            if (jl.getStatus() == JournalStatus.POSTED) {
                return updatePostedJournal(jl, journal, companyId);
            }
            if (jl.getStatus() != JournalStatus.DRAFT) {
                throw new IllegalArgumentException("Only DRAFT or POSTED journals can be updated");
            }

            resolveAndValidateEntries(journal, companyId);
            jl.setNarrative(journal.getNarrative());
            jl.setReference(journal.getReference());
            if (journal.getJournalDate() != null) {
                jl.setJournalDate(journal.getJournalDate());
            }
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

    private GeneralJournal updatePostedJournal(GeneralJournal existing, GeneralJournal replacement,
                                                Long companyId) {
        if (!isPostedJournalEditingEnabled()) {
            throw new IllegalStateException("Posted journal editing is disabled");
        }
        if (replacement.getJournalDate() == null) {
            throw new IllegalArgumentException("A journal date is required");
        }
        if (fiscalYearService.isLocked(existing.getJournalDate())
                || fiscalYearService.isLocked(replacement.getJournalDate())) {
            throw new IllegalStateException("Journal dates in closed fiscal years cannot be changed");
        }

        resolveAndValidateEntries(replacement, companyId);
        if (replacement.getEntries().size() < 2) {
            throw new IllegalArgumentException("A journal needs at least two entries with amounts");
        }
        validateJournalBalance(replacement);

        List<JournalEntry> previousEntries = List.copyOf(existing.getEntries());
        reverseLedgerImpact(previousEntries, companyId);

        existing.setJournalDate(replacement.getJournalDate());
        existing.setNarrative(replacement.getNarrative());
        existing.setReference(replacement.getReference());
        existing.getEntries().clear();
        for (JournalEntry entry : replacement.getEntries()) {
            entry.setJournal(existing);
            existing.getEntries().add(entry);
        }
        postToLedger(existing);
        return generalJournalRepository.save(existing);
    }

    private void resolveAndValidateEntries(GeneralJournal journal, Long companyId) {
        if (journal.getEntries() == null) {
            throw new IllegalArgumentException("Journal must have entries");
        }
        for (JournalEntry entry : journal.getEntries()) {
            entry.setAccount(resolveAccount(entry.getAccount(), companyId));
            resolveEntryReferences(entry, companyId);
        }
    }

    private void reverseLedgerImpact(List<JournalEntry> entries, Long companyId) {
        Map<Long, BigDecimal[]> accountTotals = new LinkedHashMap<>();
        for (JournalEntry entry : entries) {
            if (entry.getAccount() == null || entry.getAccount().getId() == null) {
                throw new IllegalStateException("Posted journal entry is missing its account");
            }
            BigDecimal[] totals = accountTotals.computeIfAbsent(
                    entry.getAccount().getId(), ignored -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            totals[0] = totals[0].add(amountOrZero(entry.getDebit()));
            totals[1] = totals[1].add(amountOrZero(entry.getCredit()));
        }

        for (Map.Entry<Long, BigDecimal[]> accountTotal : accountTotals.entrySet()) {
            GeneralLedger ledger = generalLedgerRepository
                    .findByCompanyIdAndAccountId(companyId, accountTotal.getKey())
                    .orElseThrow(() -> new IllegalStateException(
                            "General ledger balance not found for account " + accountTotal.getKey()));
            ledger.setDebitBalance(ledger.getDebitBalance().subtract(accountTotal.getValue()[0]));
            ledger.setCreditBalance(ledger.getCreditBalance().subtract(accountTotal.getValue()[1]));
            generalLedgerRepository.save(ledger);
        }
    }

    @Transactional(readOnly = true)
    public GeneralJournal getJournalForEdit(Long id) {
        GeneralJournal journal = generalJournalRepository.findByIdAndCompanyId(
                        id, companyContext.requireCompanyId())
                .orElseThrow(() -> new IllegalArgumentException("Journal not found"));
        if (journal.getStatus() == JournalStatus.DRAFT) {
            return journal;
        }
        if (journal.getStatus() != JournalStatus.POSTED || !isPostedJournalEditingEnabled()) {
            throw new IllegalStateException("Only DRAFT or editable POSTED journals can be edited");
        }
        if (fiscalYearService.isLocked(journal.getJournalDate())) {
            throw new IllegalStateException("Journals in closed fiscal years cannot be edited");
        }
        return journal;
    }

    @Transactional(readOnly = true)
    public GeneralJournal getDirectJournalForCopy(Long id) {
        GeneralJournal journal = generalJournalRepository.findByIdAndCompanyId(
                        id, companyContext.requireCompanyId())
                .orElseThrow(() -> new IllegalArgumentException("Journal not found"));
        if (!isDirectlyEntered(journal)) {
            throw new IllegalStateException("Only directly entered journals can be copied here");
        }
        return journal;
    }

    public boolean isDirectlyEntered(GeneralJournal journal) {
        if (journal == null) {
            return false;
        }
        String reference = journal.getReference();
        if (reference == null || reference.isBlank()) {
            return true;
        }
        String normalizedReference = reference.strip().toUpperCase(Locale.ROOT);
        return SYSTEM_REFERENCE_PREFIXES.stream().noneMatch(normalizedReference::startsWith);
    }

    @Transactional
    public PostedJournalDateChange updatePostedJournalDate(Long id, LocalDate newDate) {
        if (!isPostedJournalEditingEnabled()) {
            throw new IllegalStateException("Posted journal date editing is disabled");
        }
        if (newDate == null) {
            throw new IllegalArgumentException("A journal date is required");
        }
        GeneralJournal journal = generalJournalRepository.findLockedByIdAndCompanyId(
                        id, companyContext.requireCompanyId())
                .orElseThrow(() -> new IllegalArgumentException("Journal not found"));
        if (journal.getStatus() != JournalStatus.POSTED) {
            throw new IllegalStateException("Only POSTED journals can use this date-only update");
        }
        if (fiscalYearService.isLocked(journal.getJournalDate()) || fiscalYearService.isLocked(newDate)) {
            throw new IllegalStateException("Journal dates in closed fiscal years cannot be changed");
        }

        LocalDate previousDate = journal.getJournalDate();
        journal.setJournalDate(newDate);
        generalJournalRepository.save(journal);
        return new PostedJournalDateChange(previousDate, newDate);
    }

    public boolean isPostedJournalEditingEnabled() {
        return companyContext.requireCompany().isPostedJournalEditingEnabled();
    }

    public record PostedJournalDateChange(LocalDate previousDate, LocalDate newDate) {
    }

    @Transactional
    public GeneralJournal reverseJournal(Long journalId, String reversalReason) {
        Optional<GeneralJournal> journal = generalJournalRepository.findByIdAndCompanyId(
                journalId, companyContext.requireCompanyId());
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
                reversalEntry.setCustomer(entry.getCustomer());
                reversalEntry.setVendor(entry.getVendor());
                reversalEntry.setTaxAgency(entry.getTaxAgency());
                reversalEntry.setTaxItem(entry.getTaxItem());
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
        return generalJournalRepository.findByIdAndCompanyId(id, companyContext.requireCompanyId());
    }

    public Optional<GeneralJournal> getJournalByNumber(String journalNumber) {
        return generalJournalRepository.findByCompanyIdAndJournalNumber(companyContext.requireCompanyId(), journalNumber);
    }

    public List<GeneralJournal> getJournalsByStatus(JournalStatus status) {
        return generalJournalRepository.findByCompanyIdAndStatus(companyContext.requireCompanyId(), status);
    }

    public List<GeneralJournal> getJournalsByDateRange(LocalDate startDate, LocalDate endDate) {
        return generalJournalRepository.findByCompanyIdAndJournalDateBetween(
                companyContext.requireCompanyId(), startDate, endDate);
    }

    public List<GeneralJournal> getPostedJournalsByDateRange(LocalDate startDate, LocalDate endDate) {
        return generalJournalRepository.findByCompanyIdAndStatusAndJournalDateBetween(
                companyContext.requireCompanyId(), JournalStatus.POSTED, startDate, endDate);
    }

    public List<GeneralJournal> getAllJournals() {
        return generalJournalRepository.findAllByCompanyIdOrderByPostedFirstAndRecent(
                companyContext.requireCompanyId(), JournalStatus.POSTED);
    }

    @Transactional
    public void deleteJournal(Long id) {
        Optional<GeneralJournal> journal = generalJournalRepository.findByIdAndCompanyId(id, companyContext.requireCompanyId());
        if (journal.isPresent()) {
            if (!journal.get().getStatus().equals(JournalStatus.DRAFT)) {
                throw new IllegalArgumentException("Only DRAFT journals can be deleted");
            }
            generalJournalRepository.delete(journal.get());
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
            GeneralLedger gl = generalLedgerRepository.findByCompanyIdAndAccountId(
                    companyContext.requireCompanyId(), entry.getAccount().getId())
                    .orElseGet(() -> generalLedgerService.createLedgerAccount(entry.getAccount()));

            gl.setDebitBalance(gl.getDebitBalance().add(amountOrZero(entry.getDebit())));
            gl.setCreditBalance(gl.getCreditBalance().add(amountOrZero(entry.getCredit())));
            generalLedgerRepository.save(gl);
        }
    }

    private BigDecimal amountOrZero(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private com.cogitosum.entity.ChartOfAccount resolveAccount(
            com.cogitosum.entity.ChartOfAccount account, Long companyId) {
        if (account == null || account.getId() == null) {
            throw new IllegalArgumentException("Journal entry is missing an account reference");
        }
        return chartOfAccountRepository.findByIdAndCompanyId(account.getId(), companyId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
    }

    private void resolveEntryReferences(JournalEntry entry, Long companyId) {
        int selectedPartyCount = (entry.getCustomer() == null ? 0 : 1)
                + (entry.getVendor() == null ? 0 : 1)
                + (entry.getTaxAgency() == null ? 0 : 1);
        if (selectedPartyCount > 1) {
            throw new IllegalArgumentException("A journal line can have only one NAME");
        }

        if (entry.getCustomer() != null) {
            Long id = entry.getCustomer().getId();
            if (id == null) {
                throw new IllegalArgumentException("Customer is invalid");
            }
            entry.setCustomer(customerRepository.findByIdAndCompanyId(id, companyId)
                    .orElseThrow(() -> new IllegalArgumentException("Customer not found")));
        }
        if (entry.getVendor() != null) {
            Long id = entry.getVendor().getId();
            if (id == null) {
                throw new IllegalArgumentException("Vendor is invalid");
            }
            entry.setVendor(vendorRepository.findByIdAndCompanyId(id, companyId)
                    .orElseThrow(() -> new IllegalArgumentException("Vendor not found")));
        }
        if (entry.getTaxAgency() != null) {
            Long id = entry.getTaxAgency().getId();
            if (id == null) {
                throw new IllegalArgumentException("Tax agency is invalid");
            }
            entry.setTaxAgency(taxAgencyRepository.findByIdAndCompanyId(id, companyId)
                    .orElseThrow(() -> new IllegalArgumentException("Tax agency not found")));
        }
        if (entry.getTaxItem() != null) {
            Long id = entry.getTaxItem().getId();
            if (id == null) {
                throw new IllegalArgumentException("Tax item is invalid");
            }
            entry.setTaxItem(taxItemRepository.findByIdAndCompanyId(id, companyId)
                    .orElseThrow(() -> new IllegalArgumentException("Tax item not found")));
        }
    }
}
