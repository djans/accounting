package com.cogitosum.service;

import com.cogitosum.entity.*;
import com.cogitosum.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ChequeService {
    private static final Pattern CHEQUE_REFERENCE = Pattern.compile("^CHEQUE-(\\d+)$");

    private final WrittenChequeRepository chequeRepository;
    private final GeneralJournalService journalService;
    private final GeneralJournalRepository journalRepository;
    private final VendorRepository vendorRepository;
    private final CustomerRepository customerRepository;
    private final ChartOfAccountRepository accountRepository;
    private final CurrentCompanyContext companyContext;
    private final TaxCodeService taxCodeService;

    public ChequeService(WrittenChequeRepository chequeRepository, GeneralJournalService journalService,
                         GeneralJournalRepository journalRepository, VendorRepository vendorRepository,
                         CustomerRepository customerRepository, ChartOfAccountRepository accountRepository,
                         CurrentCompanyContext companyContext, TaxCodeService taxCodeService) {
        this.chequeRepository = chequeRepository;
        this.journalService = journalService;
        this.journalRepository = journalRepository;
        this.vendorRepository = vendorRepository;
        this.customerRepository = customerRepository;
        this.accountRepository = accountRepository;
        this.companyContext = companyContext;
        this.taxCodeService = taxCodeService;
    }

    public List<WrittenCheque> findAll() {
        return chequeRepository.findAllByCompanyIdOrderByChequeDateDescIdDesc(companyContext.requireCompanyId());
    }

    public Optional<WrittenCheque> findById(Long id) {
        return chequeRepository.findByIdAndCompanyId(id, companyContext.requireCompanyId());
    }

    public WrittenCheque getDraftForEdit(Long id) {
        WrittenCheque cheque = findById(id).orElseThrow(() -> new IllegalArgumentException("Cheque not found"));
        requireStatus(cheque, WrittenChequeStatus.DRAFT, "Only draft cheques can be edited");
        return cheque;
    }

    public WrittenCheque getVoidedForReissue(Long id) {
        WrittenCheque cheque = findById(id).orElseThrow(() -> new IllegalArgumentException("Cheque not found"));
        requireStatus(cheque, WrittenChequeStatus.VOIDED, "Only voided cheques can be reissued");
        return cheque;
    }

    public String nextNumber() {
        return chequeRepository.findTopByCompanyIdOrderByIdDesc(companyContext.requireCompanyId())
                .map(c -> increment(c.getChequeNumber()))
                .orElse("1");
    }

    @Transactional
    public WrittenCheque saveDraft(WrittenCheque cheque, Long reissueOfId) {
        Long companyId = companyContext.requireCompanyId();
        cheque.setCompany(companyContext.requireCompany());
        cheque.setStatus(WrittenChequeStatus.DRAFT);
        if (reissueOfId != null) {
            WrittenCheque original = chequeRepository.findByIdAndCompanyId(reissueOfId, companyId)
                    .orElseThrow(() -> new IllegalArgumentException("Original cheque not found"));
            requireStatus(original, WrittenChequeStatus.VOIDED, "Only voided cheques can be reissued");
            cheque.setReissueOf(original);
        }
        prepareCheque(cheque, companyId);
        return chequeRepository.save(cheque);
    }

    @Transactional
    public WrittenCheque updateDraft(Long id, WrittenCheque submittedCheque) {
        Long companyId = companyContext.requireCompanyId();
        WrittenCheque cheque = chequeRepository.findLockedByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new IllegalArgumentException("Cheque not found"));
        requireStatus(cheque, WrittenChequeStatus.DRAFT, "Only draft cheques can be edited");

        cheque.setChequeNumber(submittedCheque.getChequeNumber());
        cheque.setChequeDate(submittedCheque.getChequeDate());
        cheque.setVendor(submittedCheque.getVendor());
        cheque.setBankAccount(submittedCheque.getBankAccount());
        cheque.setMemo(submittedCheque.getMemo());
        cheque.getExpenses().clear();
        for (ChequeExpense expense : submittedCheque.getExpenses()) {
            expense.setCheque(cheque);
            cheque.getExpenses().add(expense);
        }
        prepareCheque(cheque, companyId);
        return chequeRepository.save(cheque);
    }

    @Transactional
    public void deleteDraft(Long id) {
        Long companyId = companyContext.requireCompanyId();
        WrittenCheque cheque = chequeRepository.findLockedByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new IllegalArgumentException("Cheque not found"));
        requireStatus(cheque, WrittenChequeStatus.DRAFT, "Only draft cheques can be deleted");
        chequeRepository.delete(cheque);
    }

    @Transactional
    public WrittenCheque issue(Long id) {
        Long companyId = companyContext.requireCompanyId();
        WrittenCheque cheque = chequeRepository.findLockedByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new IllegalArgumentException("Cheque not found"));
        requireStatus(cheque, WrittenChequeStatus.DRAFT, "Only draft cheques can be issued");
        Map<String, TaxCode> purchaseTaxCodes = prepareCheque(cheque, companyId);

        GeneralJournal journal = new GeneralJournal();
        journal.setJournalDate(cheque.getChequeDate());
        journal.setNarrative("Cheque " + cheque.getChequeNumber() + " - " + cheque.getVendor().getBusinessName());
        journal.setReference("CHEQUE-" + cheque.getId());
        int line = 1;
        for (ChequeExpense expense : cheque.getExpenses()) {
            JournalEntry entry = new JournalEntry();
            entry.setAccount(expense.getAccount());
            entry.setDebit(expense.getAmount());
            entry.setCredit(BigDecimal.ZERO);
            entry.setDescription(expense.getMemo() == null ? "Cheque expense" : expense.getMemo());
            entry.setLineNumber(line++);
            journal.getEntries().add(entry);
            for (TaxLine taxLine : taxLines(expense, purchaseTaxCodes, companyId)) {
                JournalEntry taxEntry = new JournalEntry();
                taxEntry.setAccount(taxLine.account());
                taxEntry.setDebit(taxLine.amount());
                taxEntry.setCredit(BigDecimal.ZERO);
                taxEntry.setDescription("Purchase tax " + taxLine.code() + " - cheque " + cheque.getChequeNumber());
                taxEntry.setLineNumber(line++);
                journal.getEntries().add(taxEntry);
            }
        }
        JournalEntry credit = new JournalEntry();
        credit.setAccount(cheque.getBankAccount());
        credit.setDebit(BigDecimal.ZERO);
        credit.setCredit(cheque.getAmount());
        credit.setDescription("Cheque " + cheque.getChequeNumber());
        credit.setLineNumber(line);
        journal.getEntries().add(credit);
        GeneralJournal savedJournal = journalService.createJournal(journal);
        journalService.postJournal(savedJournal.getId(), "write-cheque");

        cheque.setStatus(WrittenChequeStatus.ISSUED);
        return chequeRepository.save(cheque);
    }

    @Transactional
    public WrittenCheque voidCheque(Long id, String reason) {
        Long companyId = companyContext.requireCompanyId();
        WrittenCheque cheque = chequeRepository.findLockedByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new IllegalArgumentException("Cheque not found"));
        requireStatus(cheque, WrittenChequeStatus.ISSUED, "Only uncleared issued cheques can be voided");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required to void a cheque");
        }
        String voidReason = reason.trim();
        if (voidReason.length() > 180) {
            throw new IllegalArgumentException("The void reason cannot exceed 180 characters");
        }

        GeneralJournal journal = journalRepository.findByCompanyIdAndReference(companyId, "CHEQUE-" + cheque.getId())
                .orElseThrow(() -> new IllegalStateException("The cheque's posted journal entry was not found"));
        boolean cleared = journal.getEntries().stream()
                .anyMatch(entry -> entry.isCleared()
                        && entry.getAccount().getId().equals(cheque.getBankAccount().getId())
                        && entry.getDebit().signum() == 0
                        && entry.getCredit().compareTo(cheque.getAmount()) == 0);
        if (cleared || cheque.getStatus() == WrittenChequeStatus.CLEARED) {
            throw new IllegalStateException("A cleared cheque cannot be voided");
        }
        if (journal.getStatus() != JournalStatus.POSTED) {
            throw new IllegalStateException("Only a posted cheque journal can be reversed");
        }

        journalService.reverseJournal(journal.getId(), "Cheque voided: " + voidReason);
        cheque.setStatus(WrittenChequeStatus.VOIDED);
        cheque.setVoidedAt(LocalDateTime.now());
        cheque.setVoidReason(voidReason);
        return chequeRepository.save(cheque);
    }

    @Transactional
    public void markClearedByJournal(GeneralJournal journal) {
        Matcher matcher = CHEQUE_REFERENCE.matcher(journal.getReference() == null ? "" : journal.getReference());
        if (!matcher.matches()) {
            return;
        }
        Long chequeId = Long.valueOf(matcher.group(1));
        WrittenCheque cheque = chequeRepository.findLockedByIdAndCompanyId(
                        chequeId, companyContext.requireCompanyId())
                .orElseThrow(() -> new IllegalStateException("The reconciled cheque was not found"));
        if (cheque.getStatus() == WrittenChequeStatus.CLEARED) {
            return;
        }
        boolean bankCreditCleared = journal.getEntries().stream()
                .anyMatch(entry -> entry.isCleared()
                        && entry.getAccount().getId().equals(cheque.getBankAccount().getId())
                        && entry.getDebit().signum() == 0
                        && entry.getCredit().compareTo(cheque.getAmount()) == 0);
        if (!bankCreditCleared) {
            return;
        }
        requireStatus(cheque, WrittenChequeStatus.ISSUED, "Only issued cheques can be cleared");
        cheque.setStatus(WrittenChequeStatus.CLEARED);
        chequeRepository.save(cheque);
    }

    private Map<String, TaxCode> prepareCheque(WrittenCheque cheque, Long companyId) {
        if (cheque.getVendor() == null || cheque.getVendor().getId() == null
                || cheque.getBankAccount() == null || cheque.getBankAccount().getId() == null) {
            throw new IllegalArgumentException("A vendor and bank account are required");
        }
        cheque.setVendor(vendorRepository.findByIdAndCompanyId(cheque.getVendor().getId(), companyId)
                .orElseThrow(() -> new IllegalArgumentException("Vendor not found")));
        cheque.setBankAccount(accountRepository.findByIdAndCompanyId(cheque.getBankAccount().getId(), companyId)
                .orElseThrow(() -> new IllegalArgumentException("Bank account not found")));
        if (cheque.getChequeDate() == null) {
            cheque.setChequeDate(LocalDate.now());
        }
        if (cheque.getChequeNumber() == null || cheque.getChequeNumber().isBlank()) {
            cheque.setChequeNumber(nextNumber());
        }
        if (cheque.getExpenses() == null || cheque.getExpenses().isEmpty()) {
            throw new IllegalArgumentException("At least one expense is required");
        }
        Map<String, TaxCode> purchaseTaxCodes = new HashMap<>();
        for (TaxCode taxCode : taxCodeService.getActiveCodes()) {
            purchaseTaxCodes.put(taxCode.getCode(), taxCode);
        }
        BigDecimal total = BigDecimal.ZERO;
        for (ChequeExpense expense : cheque.getExpenses()) {
            if (expense.getAmount() == null || expense.getAmount().signum() <= 0) {
                throw new IllegalArgumentException("Expense amounts must be positive");
            }
            if (expense.getAccount() == null || expense.getAccount().getId() == null) {
                throw new IllegalArgumentException("An expense account is required");
            }
            expense.setAccount(accountRepository.findByIdAndCompanyId(expense.getAccount().getId(), companyId)
                    .orElseThrow(() -> new IllegalArgumentException("Expense account not found")));
            if (expense.getCustomerJob() != null) {
                if (expense.getCustomerJob().getId() == null) {
                    throw new IllegalArgumentException("Customer/job is invalid");
                }
                expense.setCustomerJob(customerRepository.findByIdAndCompanyId(
                                expense.getCustomerJob().getId(), companyId)
                        .orElseThrow(() -> new IllegalArgumentException("Customer/job not found")));
            }
            expense.setCheque(cheque);
            BigDecimal taxTotal = taxLines(expense, purchaseTaxCodes, companyId).stream()
                    .map(TaxLine::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            total = total.add(expense.getAmount()).add(taxTotal);
        }
        cheque.setAmount(total);
        return purchaseTaxCodes;
    }

    private List<TaxLine> taxLines(ChequeExpense expense, Map<String, TaxCode> purchaseTaxCodes,
                                   Long companyId) {
        if (expense.getTax() == null || expense.getTax().isBlank()) {
            return List.of();
        }
        TaxCode taxCode = purchaseTaxCodes.get(expense.getTax());
        if (taxCode == null) {
            throw new IllegalArgumentException("Purchase tax code not found: " + expense.getTax());
        }
        TaxGroup group = taxCode.getPurchaseTaxGroup();
        if (group == null || group.getTaxItems() == null) {
            return List.of();
        }

        List<TaxLine> lines = new ArrayList<>();
        for (TaxItem item : group.getTaxItems()) {
            if (Boolean.FALSE.equals(item.getForPurchases())) {
                continue;
            }
            BigDecimal amount = expense.getAmount().multiply(item.getRate()).setScale(2, RoundingMode.HALF_UP);
            if (amount.signum() <= 0) {
                continue;
            }
            ChartOfAccount taxAccount = item.getItcAccount() == null
                    ? expense.getAccount()
                    : accountRepository.findByIdAndCompanyId(item.getItcAccount().getId(), companyId)
                            .orElseThrow(() -> new IllegalArgumentException("Purchase tax account not found"));
            lines.add(new TaxLine(taxAccount, item.getCode(), amount));
        }
        return lines;
    }

    private void requireStatus(WrittenCheque cheque, WrittenChequeStatus expected, String message) {
        if (cheque.getStatus() != expected) {
            throw new IllegalStateException(message);
        }
    }

    private String increment(String value) {
        Matcher matcher = Pattern.compile("^(.*?)(\\d+)$").matcher(value.trim());
        if (!matcher.matches()) {
            return value + "-1";
        }
        String prefix = matcher.group(1);
        String digits = matcher.group(2);
        return prefix + String.format("%0" + digits.length() + "d", Long.parseLong(digits) + 1);
    }

    private record TaxLine(ChartOfAccount account, String code, BigDecimal amount) {
    }
}
