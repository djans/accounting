package com.cogitosum.service;

import com.cogitosum.entity.*;
import com.cogitosum.repository.ChartOfAccountRepository;
import com.cogitosum.repository.CreditCardChargeRepository;
import com.cogitosum.repository.GeneralJournalRepository;
import com.cogitosum.repository.VendorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class CreditCardChargeService {
    private final CreditCardChargeRepository chargeRepository;
    private final ChartOfAccountRepository accountRepository;
    private final VendorRepository vendorRepository;
    private final GeneralJournalService journalService;
    private final GeneralJournalRepository journalRepository;
    private final BillService billService;
    private final CurrentCompanyContext companyContext;

    public CreditCardChargeService(CreditCardChargeRepository chargeRepository,
                                   ChartOfAccountRepository accountRepository,
                                   VendorRepository vendorRepository,
                                   GeneralJournalService journalService,
                                   GeneralJournalRepository journalRepository,
                                   BillService billService,
                                   CurrentCompanyContext companyContext) {
        this.chargeRepository = chargeRepository;
        this.accountRepository = accountRepository;
        this.vendorRepository = vendorRepository;
        this.journalService = journalService;
        this.journalRepository = journalRepository;
        this.billService = billService;
        this.companyContext = companyContext;
    }

    public List<CreditCardCharge> findAll() {
        return chargeRepository.findAllByCompanyIdOrderByChargeDateDescIdDesc(companyContext.requireCompanyId());
    }

    public Optional<CreditCardCharge> findById(Long id) {
        return chargeRepository.findByIdAndCompanyId(id, companyContext.requireCompanyId());
    }

    public CreditCardCharge getDraftForEdit(Long id) {
        CreditCardCharge charge = findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Credit card charge not found"));
        requireStatus(charge, CreditCardChargeStatus.DRAFT, "Only draft charges can be edited");
        return charge;
    }

    public CreditCardCharge getVoidedForReissue(Long id) {
        CreditCardCharge charge = findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Credit card charge not found"));
        requireStatus(charge, CreditCardChargeStatus.VOIDED, "Only voided charges can be reissued");
        return charge;
    }

    @Transactional
    public CreditCardCharge saveDraft(Vendor vendor, LocalDate date, String memo,
                                      ChartOfAccount expense, ChartOfAccount card,
                                      BigDecimal total, TaxRegime regime, Long reissueOfId) {
        Long companyId = companyContext.requireCompanyId();
        CreditCardCharge charge = new CreditCardCharge();
        charge.setCompany(companyContext.requireCompany());
        charge.setStatus(CreditCardChargeStatus.DRAFT);
        if (reissueOfId != null) {
            CreditCardCharge original = chargeRepository.findByIdAndCompanyId(reissueOfId, companyId)
                    .orElseThrow(() -> new IllegalArgumentException("Original charge not found"));
            requireStatus(original, CreditCardChargeStatus.VOIDED, "Only voided charges can be reissued");
            charge.setReissueOf(original);
        }
        prepare(charge, vendor, date, memo, expense, card, total, regime, companyId);
        return chargeRepository.save(charge);
    }

    @Transactional
    public CreditCardCharge updateDraft(Long id, Vendor vendor, LocalDate date, String memo,
                                        ChartOfAccount expense, ChartOfAccount card,
                                        BigDecimal total, TaxRegime regime) {
        Long companyId = companyContext.requireCompanyId();
        CreditCardCharge charge = chargeRepository.findLockedByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new IllegalArgumentException("Credit card charge not found"));
        requireStatus(charge, CreditCardChargeStatus.DRAFT, "Only draft charges can be edited");
        prepare(charge, vendor, date, memo, expense, card, total, regime, companyId);
        return chargeRepository.save(charge);
    }

    @Transactional
    public void deleteDraft(Long id) {
        Long companyId = companyContext.requireCompanyId();
        CreditCardCharge charge = chargeRepository.findLockedByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new IllegalArgumentException("Credit card charge not found"));
        requireStatus(charge, CreditCardChargeStatus.DRAFT, "Only draft charges can be deleted");
        chargeRepository.delete(charge);
    }

    @Transactional
    public BulkActionResult deleteDrafts(List<Long> ids) {
        List<CreditCardCharge> selected = findLockedSelected(ids);
        int deleted = 0;
        int skipped = 0;
        for (CreditCardCharge charge : selected) {
            if (charge.getStatus() == CreditCardChargeStatus.DRAFT) {
                chargeRepository.delete(charge);
                deleted++;
            } else {
                skipped++;
            }
        }
        return new BulkActionResult(deleted, skipped);
    }

    @Transactional
    public CreditCardCharge post(Long id) {
        Long companyId = companyContext.requireCompanyId();
        CreditCardCharge charge = chargeRepository.findLockedByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new IllegalArgumentException("Credit card charge not found"));
        requireStatus(charge, CreditCardChargeStatus.DRAFT, "Only draft charges can be posted");
        return postDraft(charge);
    }

    @Transactional
    public BulkActionResult postDrafts(List<Long> ids) {
        List<CreditCardCharge> selected = findLockedSelected(ids);
        int posted = 0;
        int skipped = 0;
        for (CreditCardCharge charge : selected) {
            if (charge.getStatus() == CreditCardChargeStatus.DRAFT) {
                postDraft(charge);
                posted++;
            } else {
                skipped++;
            }
        }
        return new BulkActionResult(posted, skipped);
    }

    private CreditCardCharge postDraft(CreditCardCharge charge) {
        GeneralJournal journal = buildJournal(charge);
        GeneralJournal created = journalService.createJournal(journal);
        GeneralJournal posted = journalService.postJournal(created.getId(), "credit-card-charge");
        charge.setJournal(posted);
        charge.setStatus(CreditCardChargeStatus.POSTED);
        return chargeRepository.save(charge);
    }

    private List<CreditCardCharge> findLockedSelected(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Select at least one credit card charge");
        }
        Set<Long> uniqueIds = new LinkedHashSet<>(ids);
        Long companyId = companyContext.requireCompanyId();
        List<CreditCardCharge> selected =
                chargeRepository.findAllLockedByIdInAndCompanyId(uniqueIds, companyId);
        if (selected.size() != uniqueIds.size()) {
            throw new IllegalArgumentException("One or more selected charges are unavailable");
        }
        return selected;
    }

    @Transactional
    public CreditCardCharge voidCharge(Long id, String reason) {
        Long companyId = companyContext.requireCompanyId();
        CreditCardCharge charge = chargeRepository.findLockedByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new IllegalArgumentException("Credit card charge not found"));
        requireStatus(charge, CreditCardChargeStatus.POSTED, "Only uncleared posted charges can be voided");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required to void a charge");
        }
        String voidReason = reason.trim();
        if (voidReason.length() > 180) {
            throw new IllegalArgumentException("The void reason cannot exceed 180 characters");
        }
        GeneralJournal journal = charge.getJournal() != null
                ? charge.getJournal()
                : journalRepository.findByCompanyIdAndReference(companyId, "CARD-CHARGE-" + charge.getId())
                        .orElseThrow(() -> new IllegalStateException("The charge's posted journal was not found"));
        boolean cleared = journal.getEntries().stream()
                .anyMatch(entry -> entry.isCleared()
                        && entry.getAccount().getId().equals(charge.getCardAccount().getId())
                        && entry.getDebit().signum() == 0
                        && entry.getCredit().compareTo(charge.getTotalAmount()) == 0);
        if (cleared) {
            throw new IllegalStateException("A cleared charge cannot be voided");
        }
        if (journal.getStatus() != JournalStatus.POSTED) {
            throw new IllegalStateException("Only a posted charge journal can be reversed");
        }

        journalService.reverseJournal(journal.getId(), "Credit card charge voided: " + voidReason);
        charge.setStatus(CreditCardChargeStatus.VOIDED);
        charge.setVoidedAt(LocalDateTime.now());
        charge.setVoidReason(voidReason);
        return chargeRepository.save(charge);
    }

    private GeneralJournal buildJournal(CreditCardCharge charge) {
        TaxRegime regime = regimeForCode(charge.getTaxRegime());
        GeneralJournal journal = new GeneralJournal();
        journal.setJournalDate(charge.getChargeDate());
        journal.setNarrative("Credit card charge - " + charge.getVendor().getBusinessName());
        journal.setReference("CARD-CHARGE-" + charge.getId());
        List<JournalEntry> entries = new java.util.ArrayList<>();
        entries.add(entry(charge.getExpenseAccount(), charge.getNetAmount(), BigDecimal.ZERO, charge.getMemo(), 1));
        int line = 2;
        BigDecimal gst = charge.getNetAmount().multiply(regime.gstRate()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal qst = charge.getNetAmount().multiply(regime.qstRate()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal hst = charge.getNetAmount().multiply(regime.hstRate()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal roundingAdjustment = charge.getTaxAmount().subtract(gst.add(qst).add(hst));
        if (hst.signum() > 0) hst = hst.add(roundingAdjustment);
        else if (qst.signum() > 0) qst = qst.add(roundingAdjustment);
        else if (gst.signum() > 0) gst = gst.add(roundingAdjustment);
        if (gst.signum() > 0) entries.add(entry(lookup("1300"), gst, BigDecimal.ZERO, "TPS/GST récupérable", line++));
        if (qst.signum() > 0) entries.add(entry(lookup("1310"), qst, BigDecimal.ZERO, "TVQ/QST récupérable", line++));
        if (hst.signum() > 0) entries.add(entry(lookup("1320"), hst, BigDecimal.ZERO, "HST récupérable", line++));
        entries.add(entry(charge.getCardAccount(), BigDecimal.ZERO, charge.getTotalAmount(), "Credit card payable", line));
        journal.setEntries(entries);
        return journal;
    }

    private void prepare(CreditCardCharge charge, Vendor vendor, LocalDate date, String memo,
                         ChartOfAccount expense, ChartOfAccount card, BigDecimal total,
                         TaxRegime regime, Long companyId) {
        if (regime == null) {
            throw new IllegalArgumentException("A tax regime is required");
        }
        vendor = vendorRepository.findByIdAndCompanyId(requiredId(vendor, "Vendor"), companyId)
                .orElseThrow(() -> new IllegalArgumentException("Vendor not found"));
        expense = accountRepository.findByIdAndCompanyId(requiredId(expense, "Expense account"), companyId)
                .orElseThrow(() -> new IllegalArgumentException("Expense account not found"));
        card = accountRepository.findByIdAndCompanyId(requiredId(card, "Credit card account"), companyId)
                .orElseThrow(() -> new IllegalArgumentException("Credit card account not found"));
        if (total == null || total.signum() <= 0) {
            throw new IllegalArgumentException("The total amount must be greater than zero");
        }
        if (card.getAccountType() != AccountType.LIABILITY) {
            throw new IllegalArgumentException("The credit card account must be a liability account");
        }
        BigDecimal multiplier = BigDecimal.ONE.add(regime.gstRate()).add(regime.hstRate()).add(regime.qstRate());
        BigDecimal gross = total.setScale(2, RoundingMode.HALF_UP);
        BigDecimal net = gross.divide(multiplier, 2, RoundingMode.HALF_UP);
        charge.setVendor(vendor);
        charge.setChargeDate(date == null ? LocalDate.now() : date);
        charge.setMemo(memo);
        charge.setExpenseAccount(expense);
        charge.setCardAccount(card);
        charge.setTotalAmount(gross);
        charge.setNetAmount(net);
        charge.setTaxAmount(gross.subtract(net).setScale(2, RoundingMode.HALF_UP));
        charge.setTaxRegime(regime.code());
    }

    private TaxRegime regimeForCode(String code) {
        return billService.getRegimes().stream()
                .filter(regime -> regime.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("The charge's tax regime is not supported"));
    }

    private ChartOfAccount lookup(String number) {
        return accountRepository.findByCompanyIdAndAccountNumber(companyContext.requireCompanyId(), number)
                .orElseThrow(() -> new IllegalStateException("Required tax account not seeded: " + number));
    }

    private JournalEntry entry(ChartOfAccount account, BigDecimal debit, BigDecimal credit, String description, int line) {
        JournalEntry entry = new JournalEntry();
        entry.setAccount(account);
        entry.setDebit(debit);
        entry.setCredit(credit);
        entry.setDescription(description);
        entry.setLineNumber(line);
        return entry;
    }

    private Long requiredId(Object related, String name) {
        if (related instanceof Vendor vendor && vendor.getId() != null) {
            return vendor.getId();
        }
        if (related instanceof ChartOfAccount account && account.getId() != null) {
            return account.getId();
        }
        throw new IllegalArgumentException(name + " is required");
    }

    private void requireStatus(CreditCardCharge charge, CreditCardChargeStatus expected, String message) {
        if (charge.getStatus() != expected) {
            throw new IllegalStateException(message);
        }
    }

    public record BulkActionResult(int processed, int skipped) {
    }
}
