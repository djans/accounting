package com.cogitosum.service;

import com.cogitosum.entity.*;
import com.cogitosum.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChequeServiceTest {

    @Mock private WrittenChequeRepository cheques;
    @Mock private GeneralJournalService journalService;
    @Mock private GeneralJournalRepository journals;
    @Mock private VendorRepository vendors;
    @Mock private CustomerRepository customers;
    @Mock private ChartOfAccountRepository accounts;
    @Mock private CurrentCompanyContext companyContext;
    @Mock private TaxCodeService taxCodeService;

    private ChequeService service;
    private Company company;
    private Vendor vendor;
    private ChartOfAccount bank;
    private ChartOfAccount expenseAccount;

    @BeforeEach
    void setUp() {
        service = new ChequeService(
                cheques, journalService, journals, vendors, customers, accounts, companyContext, taxCodeService);
        company = new Company();
        company.setId(1L);
        vendor = new Vendor();
        vendor.setId(2L);
        vendor.setBusinessName("Test Vendor");
        bank = account(3L);
        expenseAccount = account(4L);
        lenient().when(companyContext.requireCompanyId()).thenReturn(1L);
        lenient().when(companyContext.requireCompany()).thenReturn(company);
        lenient().when(taxCodeService.getActiveCodes()).thenReturn(List.of());
        lenient().when(vendors.findByIdAndCompanyId(2L, 1L)).thenReturn(Optional.of(vendor));
        lenient().when(accounts.findByIdAndCompanyId(3L, 1L)).thenReturn(Optional.of(bank));
        lenient().when(accounts.findByIdAndCompanyId(4L, 1L)).thenReturn(Optional.of(expenseAccount));
    }

    @Test
    void saveDraftStoresChequeWithoutPostingIt() {
        WrittenCheque cheque = cheque();
        when(cheques.save(any(WrittenCheque.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WrittenCheque saved = service.saveDraft(cheque, null);

        assertEquals(WrittenChequeStatus.DRAFT, saved.getStatus());
        assertEquals(new BigDecimal("25.00"), saved.getAmount());
        verify(cheques).save(cheque);
        verifyNoInteractions(journalService, journals);
    }

    @Test
    void findAllUsesNewestChequeDateFirstOrdering() {
        when(cheques.findAllByCompanyIdOrderByChequeDateDescIdDesc(1L)).thenReturn(List.of());

        assertTrue(service.findAll().isEmpty());

        verify(cheques).findAllByCompanyIdOrderByChequeDateDescIdDesc(1L);
    }

    @Test
    void saveDraftAddsPurchaseTaxesToTheChequeTotal() {
        ChartOfAccount itcAccount = account(5L);
        when(accounts.findByIdAndCompanyId(5L, 1L)).thenReturn(Optional.of(itcAccount));
        when(taxCodeService.getActiveCodes()).thenReturn(List.of(taxCode("GST", "0.05000", itcAccount)));
        when(cheques.save(any(WrittenCheque.class))).thenAnswer(invocation -> invocation.getArgument(0));
        WrittenCheque cheque = cheque();
        cheque.getExpenses().get(0).setTax("GST");

        WrittenCheque saved = service.saveDraft(cheque, null);

        assertEquals(new BigDecimal("26.25"), saved.getAmount());
    }

    @Test
    void updateDraftReplacesItsDetailsWithoutPostingIt() {
        WrittenCheque existing = cheque();
        setChequeId(existing, 12L);
        existing.setStatus(WrittenChequeStatus.DRAFT);
        WrittenCheque submitted = cheque();
        submitted.setChequeNumber("102");
        submitted.getExpenses().get(0).setAmount(new BigDecimal("35.00"));
        when(cheques.findLockedByIdAndCompanyId(12L, 1L)).thenReturn(Optional.of(existing));
        when(cheques.save(existing)).thenReturn(existing);

        WrittenCheque updated = service.updateDraft(12L, submitted);

        assertEquals("102", updated.getChequeNumber());
        assertEquals(new BigDecimal("35.00"), updated.getAmount());
        assertEquals(1, updated.getExpenses().size());
        verifyNoInteractions(journalService, journals);
    }

    @Test
    void deleteDraftRemovesOnlyAnUnissuedCheque() {
        WrittenCheque draft = cheque();
        draft.setStatus(WrittenChequeStatus.DRAFT);
        when(cheques.findLockedByIdAndCompanyId(12L, 1L)).thenReturn(Optional.of(draft));

        service.deleteDraft(12L);

        verify(cheques).delete(draft);
        verifyNoInteractions(journalService, journals);
    }

    @Test
    void deleteDraftRejectsAnIssuedCheque() {
        WrittenCheque issued = cheque();
        issued.setStatus(WrittenChequeStatus.ISSUED);
        when(cheques.findLockedByIdAndCompanyId(12L, 1L)).thenReturn(Optional.of(issued));

        assertThrows(IllegalStateException.class, () -> service.deleteDraft(12L));

        verify(cheques, never()).delete(any(WrittenCheque.class));
    }

    @Test
    void issuedChequesCannotBeEdited() {
        WrittenCheque issued = cheque();
        issued.setStatus(WrittenChequeStatus.ISSUED);
        when(cheques.findLockedByIdAndCompanyId(12L, 1L)).thenReturn(Optional.of(issued));

        assertThrows(IllegalStateException.class, () -> service.updateDraft(12L, cheque()));

        verifyNoInteractions(journalService, journals);
    }

    @Test
    void issuePostsTheDraftAndChangesItsStatus() {
        WrittenCheque cheque = cheque();
        setChequeId(cheque, 12L);
        cheque.setStatus(WrittenChequeStatus.DRAFT);
        when(cheques.findLockedByIdAndCompanyId(12L, 1L)).thenReturn(Optional.of(cheque));
        when(cheques.save(cheque)).thenReturn(cheque);
        GeneralJournal journal = new GeneralJournal();
        journal.setId(50L);
        when(journalService.createJournal(any(GeneralJournal.class))).thenReturn(journal);

        WrittenCheque issued = service.issue(12L);

        assertEquals(WrittenChequeStatus.ISSUED, issued.getStatus());
        verify(journalService).postJournal(50L, "write-cheque");
        verify(cheques).save(cheque);
    }

    @Test
    void issuePostsNetExpenseRecoverableTaxAndGrossChequeTotal() {
        ChartOfAccount itcAccount = account(5L);
        when(accounts.findByIdAndCompanyId(5L, 1L)).thenReturn(Optional.of(itcAccount));
        when(taxCodeService.getActiveCodes()).thenReturn(List.of(taxCode("GST", "0.05000", itcAccount)));
        WrittenCheque cheque = cheque();
        setChequeId(cheque, 12L);
        cheque.setStatus(WrittenChequeStatus.DRAFT);
        cheque.getExpenses().get(0).setTax("GST");
        when(cheques.findLockedByIdAndCompanyId(12L, 1L)).thenReturn(Optional.of(cheque));
        when(cheques.save(cheque)).thenReturn(cheque);
        when(journalService.createJournal(any(GeneralJournal.class))).thenAnswer(invocation -> {
            GeneralJournal postedJournal = invocation.getArgument(0);
            postedJournal.setId(50L);
            return postedJournal;
        });

        service.issue(12L);

        org.mockito.ArgumentCaptor<GeneralJournal> journalCaptor =
                org.mockito.ArgumentCaptor.forClass(GeneralJournal.class);
        verify(journalService).createJournal(journalCaptor.capture());
        List<JournalEntry> entries = journalCaptor.getValue().getEntries();
        assertEquals(3, entries.size());
        assertEquals(new BigDecimal("25.00"), entries.get(0).getDebit());
        assertSame(expenseAccount, entries.get(0).getAccount());
        assertEquals(new BigDecimal("1.25"), entries.get(1).getDebit());
        assertSame(itcAccount, entries.get(1).getAccount());
        assertEquals(new BigDecimal("26.25"), entries.get(2).getCredit());
        assertSame(bank, entries.get(2).getAccount());
    }

    @Test
    void voidingAnIssuedChequeReversesTheJournalAndKeepsTheReason() {
        WrittenCheque cheque = cheque();
        setChequeId(cheque, 12L);
        cheque.setStatus(WrittenChequeStatus.ISSUED);
        GeneralJournal journal = journalForCheque(cheque, false);
        when(cheques.findLockedByIdAndCompanyId(12L, 1L)).thenReturn(Optional.of(cheque));
        when(journals.findByCompanyIdAndReference(1L, "CHEQUE-12")).thenReturn(Optional.of(journal));
        when(cheques.save(cheque)).thenReturn(cheque);

        WrittenCheque voided = service.voidCheque(12L, "Incorrect payee");

        assertEquals(WrittenChequeStatus.VOIDED, voided.getStatus());
        assertEquals("Incorrect payee", voided.getVoidReason());
        assertNotNull(voided.getVoidedAt());
        verify(journalService).reverseJournal(journal.getId(), "Cheque voided: Incorrect payee");
    }

    @Test
    void voidingAnIssuedChequeRejectsAReconciledBankEntry() {
        WrittenCheque cheque = cheque();
        setChequeId(cheque, 12L);
        cheque.setStatus(WrittenChequeStatus.ISSUED);
        GeneralJournal journal = journalForCheque(cheque, true);
        when(cheques.findLockedByIdAndCompanyId(12L, 1L)).thenReturn(Optional.of(cheque));
        when(journals.findByCompanyIdAndReference(1L, "CHEQUE-12")).thenReturn(Optional.of(journal));

        assertThrows(IllegalStateException.class, () -> service.voidCheque(12L, "Mistake"));

        verifyNoInteractions(journalService);
    }

    @Test
    void clearedChequesCannotBeVoided() {
        WrittenCheque cheque = cheque();
        setChequeId(cheque, 12L);
        cheque.setStatus(WrittenChequeStatus.CLEARED);
        when(cheques.findLockedByIdAndCompanyId(12L, 1L)).thenReturn(Optional.of(cheque));

        assertThrows(IllegalStateException.class, () -> service.voidCheque(12L, "Mistake"));

        verifyNoInteractions(journals, journalService);
    }

    @Test
    void reconcilingChequeJournalMarksChequeCleared() {
        WrittenCheque cheque = cheque();
        setChequeId(cheque, 12L);
        cheque.setStatus(WrittenChequeStatus.ISSUED);
        GeneralJournal journal = new GeneralJournal();
        journal.setReference("CHEQUE-12");
        JournalEntry bankCredit = new JournalEntry();
        bankCredit.setAccount(bank);
        bankCredit.setDebit(BigDecimal.ZERO);
        bankCredit.setCredit(cheque.getAmount());
        bankCredit.setCleared(true);
        journal.getEntries().add(bankCredit);
        when(cheques.findLockedByIdAndCompanyId(12L, 1L)).thenReturn(Optional.of(cheque));
        when(cheques.save(cheque)).thenReturn(cheque);

        service.markClearedByJournal(journal);

        assertEquals(WrittenChequeStatus.CLEARED, cheque.getStatus());
        verify(cheques).save(cheque);
    }

    @Test
    void replacementDraftLinksToTheVoidedCheque() {
        WrittenCheque original = cheque();
        setChequeId(original, 10L);
        original.setStatus(WrittenChequeStatus.VOIDED);
        WrittenCheque replacement = cheque();
        when(cheques.findByIdAndCompanyId(10L, 1L)).thenReturn(Optional.of(original));
        when(cheques.save(any(WrittenCheque.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WrittenCheque saved = service.saveDraft(replacement, 10L);

        assertSame(original, saved.getReissueOf());
        assertEquals(WrittenChequeStatus.DRAFT, saved.getStatus());
    }

    private WrittenCheque cheque() {
        WrittenCheque cheque = new WrittenCheque();
        cheque.setChequeNumber("101");
        cheque.setChequeDate(LocalDate.of(2026, 1, 15));
        cheque.setVendor(vendor);
        cheque.setBankAccount(bank);
        cheque.setAmount(new BigDecimal("25.00"));
        ChequeExpense expense = new ChequeExpense();
        expense.setAccount(expenseAccount);
        expense.setAmount(new BigDecimal("25.00"));
        cheque.setExpenses(new ArrayList<>(List.of(expense)));
        return cheque;
    }

    private GeneralJournal journalForCheque(WrittenCheque cheque, boolean cleared) {
        GeneralJournal journal = new GeneralJournal();
        journal.setId(50L);
        journal.setStatus(JournalStatus.POSTED);
        journal.setReference("CHEQUE-" + cheque.getId());
        JournalEntry bankEntry = new JournalEntry();
        bankEntry.setAccount(bank);
        bankEntry.setDebit(BigDecimal.ZERO);
        bankEntry.setCredit(cheque.getAmount());
        bankEntry.setCleared(cleared);
        journal.getEntries().add(bankEntry);
        return journal;
    }

    private TaxCode taxCode(String code, String rate, ChartOfAccount itcAccount) {
        TaxItem item = new TaxItem();
        item.setCode(code);
        item.setRate(new BigDecimal(rate));
        item.setForPurchases(true);
        item.setItcAccount(itcAccount);
        TaxGroup group = new TaxGroup();
        group.setTaxItems(List.of(item));
        TaxCode taxCode = new TaxCode();
        taxCode.setCode(code);
        taxCode.setPurchaseTaxGroup(group);
        return taxCode;
    }

    private ChartOfAccount account(Long id) {
        ChartOfAccount account = new ChartOfAccount();
        account.setId(id);
        return account;
    }

    private void setChequeId(WrittenCheque cheque, Long id) {
        ReflectionTestUtils.setField(cheque, "id", id);
    }
}
