package com.cogitosum.service;

import com.cogitosum.entity.*;
import com.cogitosum.repository.ChartOfAccountRepository;
import com.cogitosum.repository.CreditCardChargeRepository;
import com.cogitosum.repository.GeneralJournalRepository;
import com.cogitosum.repository.VendorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreditCardChargeServiceTest {
    @Mock private CreditCardChargeRepository charges;
    @Mock private ChartOfAccountRepository accounts;
    @Mock private VendorRepository vendors;
    @Mock private GeneralJournalService journalService;
    @Mock private GeneralJournalRepository journals;
    @Mock private BillService billService;
    @Mock private CurrentCompanyContext companyContext;

    private CreditCardChargeService service;
    private Company company;
    private Vendor vendor;
    private ChartOfAccount expense;
    private ChartOfAccount card;
    private TaxRegime federal;

    @BeforeEach
    void setUp() {
        service = new CreditCardChargeService(
                charges, accounts, vendors, journalService, journals, billService, companyContext);
        company = new Company();
        company.setId(1L);
        vendor = new Vendor();
        vendor.setId(2L);
        vendor.setBusinessName("Test Supplier");
        expense = account(3L, AccountType.EXPENSE);
        card = account(4L, AccountType.LIABILITY);
        federal = new TaxRegime("FED", "Federal GST", new BigDecimal("0.05"),
                BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Test
    void saveDraftCalculatesTotalsWithoutPosting() {
        when(companyContext.requireCompanyId()).thenReturn(1L);
        when(companyContext.requireCompany()).thenReturn(company);
        when(vendors.findByIdAndCompanyId(2L, 1L)).thenReturn(Optional.of(vendor));
        when(accounts.findByIdAndCompanyId(3L, 1L)).thenReturn(Optional.of(expense));
        when(accounts.findByIdAndCompanyId(4L, 1L)).thenReturn(Optional.of(card));
        when(charges.save(any(CreditCardCharge.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreditCardCharge saved = service.saveDraft(
                vendor, LocalDate.of(2026, 2, 1), "Office supplies",
                expense, card, new BigDecimal("105.00"), federal, null);

        assertEquals(CreditCardChargeStatus.DRAFT, saved.getStatus());
        assertEquals(new BigDecimal("105.00"), saved.getTotalAmount());
        assertEquals(new BigDecimal("100.00"), saved.getNetAmount());
        assertEquals(new BigDecimal("5.00"), saved.getTaxAmount());
        verifyNoInteractions(journalService, journals);
    }

    @Test
    void updateDraftRecalculatesTotalsWithoutPosting() {
        CreditCardCharge draft = draftCharge();
        when(companyContext.requireCompanyId()).thenReturn(1L);
        when(vendors.findByIdAndCompanyId(2L, 1L)).thenReturn(Optional.of(vendor));
        when(accounts.findByIdAndCompanyId(3L, 1L)).thenReturn(Optional.of(expense));
        when(accounts.findByIdAndCompanyId(4L, 1L)).thenReturn(Optional.of(card));
        when(charges.findLockedByIdAndCompanyId(10L, 1L)).thenReturn(Optional.of(draft));
        when(charges.save(draft)).thenReturn(draft);

        CreditCardCharge updated = service.updateDraft(
                10L, vendor, LocalDate.of(2026, 2, 2), "Updated charge",
                expense, card, new BigDecimal("210.00"), federal);

        assertEquals(new BigDecimal("200.00"), updated.getNetAmount());
        assertEquals(new BigDecimal("10.00"), updated.getTaxAmount());
        assertEquals(new BigDecimal("210.00"), updated.getTotalAmount());
        verifyNoInteractions(journalService, journals);
    }

    @Test
    void reissueIsSavedAsDraftLinkedToVoidedCharge() {
        CreditCardCharge original = draftCharge();
        original.setStatus(CreditCardChargeStatus.VOIDED);
        when(companyContext.requireCompanyId()).thenReturn(1L);
        when(companyContext.requireCompany()).thenReturn(company);
        when(charges.findByIdAndCompanyId(30L, 1L)).thenReturn(Optional.of(original));
        when(vendors.findByIdAndCompanyId(2L, 1L)).thenReturn(Optional.of(vendor));
        when(accounts.findByIdAndCompanyId(3L, 1L)).thenReturn(Optional.of(expense));
        when(accounts.findByIdAndCompanyId(4L, 1L)).thenReturn(Optional.of(card));
        when(charges.save(any(CreditCardCharge.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreditCardCharge replacement = service.saveDraft(
                vendor, LocalDate.of(2026, 2, 3), "Replacement",
                expense, card, new BigDecimal("105.00"), federal, 30L);

        assertEquals(CreditCardChargeStatus.DRAFT, replacement.getStatus());
        assertSame(original, replacement.getReissueOf());
        verifyNoInteractions(journalService, journals);
    }

    @Test
    void postCreatesAndLinksTheBalancedJournal() {
        CreditCardCharge charge = draftCharge();
        when(companyContext.requireCompanyId()).thenReturn(1L);
        when(billService.getRegimes()).thenReturn(List.of(federal));
        when(charges.findLockedByIdAndCompanyId(10L, 1L)).thenReturn(Optional.of(charge));
        when(charges.save(charge)).thenReturn(charge);
        when(accounts.findByCompanyIdAndAccountNumber(1L, "1300"))
                .thenReturn(Optional.of(account(5L, AccountType.ASSET)));
        GeneralJournal created = new GeneralJournal();
        created.setId(20L);
        GeneralJournal posted = new GeneralJournal();
        posted.setId(20L);
        posted.setStatus(JournalStatus.POSTED);
        when(journalService.createJournal(any(GeneralJournal.class))).thenReturn(created);
        when(journalService.postJournal(20L, "credit-card-charge")).thenReturn(posted);

        CreditCardCharge result = service.post(10L);

        ArgumentCaptor<GeneralJournal> captor = ArgumentCaptor.forClass(GeneralJournal.class);
        verify(journalService).createJournal(captor.capture());
        List<JournalEntry> entries = captor.getValue().getEntries();
        assertEquals(3, entries.size());
        assertEquals(new BigDecimal("100.00"), entries.get(0).getDebit());
        assertEquals(new BigDecimal("5.00"), entries.get(1).getDebit());
        assertEquals(new BigDecimal("105.00"), entries.get(2).getCredit());
        assertSame(posted, result.getJournal());
        assertEquals(CreditCardChargeStatus.POSTED, result.getStatus());
    }

    @Test
    void bulkPostPostsDraftsAndSkipsChargesThatAreNotDrafts() {
        CreditCardCharge draft = draftCharge();
        CreditCardCharge postedCharge = draftCharge();
        postedCharge.setStatus(CreditCardChargeStatus.POSTED);
        when(companyContext.requireCompanyId()).thenReturn(1L);
        when(charges.findAllLockedByIdInAndCompanyId(Set.of(10L, 11L), 1L))
                .thenReturn(List.of(draft, postedCharge));
        when(billService.getRegimes()).thenReturn(List.of(federal));
        when(accounts.findByCompanyIdAndAccountNumber(1L, "1300"))
                .thenReturn(Optional.of(account(5L, AccountType.ASSET)));
        when(charges.save(draft)).thenReturn(draft);
        GeneralJournal created = new GeneralJournal();
        created.setId(20L);
        GeneralJournal postedJournal = new GeneralJournal();
        postedJournal.setId(20L);
        postedJournal.setStatus(JournalStatus.POSTED);
        when(journalService.createJournal(any(GeneralJournal.class))).thenReturn(created);
        when(journalService.postJournal(20L, "credit-card-charge")).thenReturn(postedJournal);

        CreditCardChargeService.BulkActionResult result = service.postDrafts(List.of(10L, 11L));

        assertEquals(new CreditCardChargeService.BulkActionResult(1, 1), result);
        assertEquals(CreditCardChargeStatus.POSTED, draft.getStatus());
        verify(journalService).createJournal(any(GeneralJournal.class));
    }

    @Test
    void bulkDeleteDeletesDraftsAndSkipsChargesThatAreNotDrafts() {
        CreditCardCharge draft = draftCharge();
        CreditCardCharge postedCharge = draftCharge();
        postedCharge.setStatus(CreditCardChargeStatus.POSTED);
        when(companyContext.requireCompanyId()).thenReturn(1L);
        when(charges.findAllLockedByIdInAndCompanyId(Set.of(10L, 11L), 1L))
                .thenReturn(List.of(draft, postedCharge));

        CreditCardChargeService.BulkActionResult result = service.deleteDrafts(List.of(10L, 11L));

        assertEquals(new CreditCardChargeService.BulkActionResult(1, 1), result);
        verify(charges).delete(draft);
        verify(charges, never()).delete(postedCharge);
        verifyNoInteractions(journalService, journals);
    }

    @Test
    void deleteDraftRejectsPostedCharge() {
        CreditCardCharge posted = draftCharge();
        posted.setStatus(CreditCardChargeStatus.POSTED);
        when(companyContext.requireCompanyId()).thenReturn(1L);
        when(charges.findLockedByIdAndCompanyId(10L, 1L)).thenReturn(Optional.of(posted));

        assertThrows(IllegalStateException.class, () -> service.deleteDraft(10L));

        verify(charges, never()).delete(any(CreditCardCharge.class));
    }

    @Test
    void deleteDraftRemovesDraftWithoutJournal() {
        CreditCardCharge draft = draftCharge();
        when(companyContext.requireCompanyId()).thenReturn(1L);
        when(charges.findLockedByIdAndCompanyId(10L, 1L)).thenReturn(Optional.of(draft));

        service.deleteDraft(10L);

        verify(charges).delete(draft);
        verifyNoInteractions(journalService, journals);
    }

    @Test
    void voidPostedChargeReversesJournalAndKeepsReason() {
        CreditCardCharge charge = draftCharge();
        charge.setStatus(CreditCardChargeStatus.POSTED);
        GeneralJournal journal = postedCardJournal();
        charge.setJournal(journal);
        when(companyContext.requireCompanyId()).thenReturn(1L);
        when(charges.findLockedByIdAndCompanyId(10L, 1L)).thenReturn(Optional.of(charge));
        when(charges.save(charge)).thenReturn(charge);

        CreditCardCharge voided = service.voidCharge(10L, "Wrong supplier");

        assertEquals(CreditCardChargeStatus.VOIDED, voided.getStatus());
        assertEquals("Wrong supplier", voided.getVoidReason());
        assertNotNull(voided.getVoidedAt());
        verify(journalService).reverseJournal(20L, "Credit card charge voided: Wrong supplier");
    }

    @Test
    void clearedChargeCannotBeVoided() {
        CreditCardCharge charge = draftCharge();
        charge.setStatus(CreditCardChargeStatus.POSTED);
        GeneralJournal journal = postedCardJournal();
        journal.getEntries().get(0).setCleared(true);
        charge.setJournal(journal);
        when(companyContext.requireCompanyId()).thenReturn(1L);
        when(charges.findLockedByIdAndCompanyId(10L, 1L)).thenReturn(Optional.of(charge));

        assertThrows(IllegalStateException.class, () -> service.voidCharge(10L, "Wrong supplier"));

        verifyNoInteractions(journalService, journals);
    }

    private CreditCardCharge draftCharge() {
        CreditCardCharge charge = new CreditCardCharge();
        charge.setVendor(vendor);
        charge.setChargeDate(LocalDate.of(2026, 2, 1));
        charge.setExpenseAccount(expense);
        charge.setCardAccount(card);
        charge.setTotalAmount(new BigDecimal("105.00"));
        charge.setNetAmount(new BigDecimal("100.00"));
        charge.setTaxAmount(new BigDecimal("5.00"));
        charge.setTaxRegime("FED");
        return charge;
    }

    private GeneralJournal postedCardJournal() {
        GeneralJournal journal = new GeneralJournal();
        journal.setId(20L);
        journal.setStatus(JournalStatus.POSTED);
        JournalEntry credit = new JournalEntry();
        credit.setAccount(card);
        credit.setDebit(BigDecimal.ZERO);
        credit.setCredit(new BigDecimal("105.00"));
        journal.getEntries().add(credit);
        return journal;
    }

    private ChartOfAccount account(Long id, AccountType type) {
        ChartOfAccount account = new ChartOfAccount();
        account.setId(id);
        account.setAccountType(type);
        return account;
    }
}
