package com.cogitosum.service;

import com.cogitosum.entity.*;
import com.cogitosum.repository.GeneralJournalRepository;
import com.cogitosum.repository.TransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferLifecycleServiceTest {

    @Mock private TransferRepository transfers;
    @Mock private ChartOfAccountService accounts;
    @Mock private GeneralJournalService journalService;
    @Mock private GeneralJournalRepository journals;
    @Mock private CurrentCompanyContext companyContext;

    private TransferService service;
    private Company company;
    private ChartOfAccount from;
    private ChartOfAccount to;

    @BeforeEach
    void setUp() {
        service = new TransferService(transfers, accounts, journalService, journals, companyContext);
        company = new Company();
        company.setId(1L);
        from = account(2L, "1000");
        to = account(3L, "1010");
        lenient().when(companyContext.requireCompanyId()).thenReturn(1L);
        lenient().when(companyContext.requireCompany()).thenReturn(company);
        lenient().when(accounts.getAccountById(2L)).thenReturn(Optional.of(from));
        lenient().when(accounts.getAccountById(3L)).thenReturn(Optional.of(to));
        lenient().when(transfers.save(any(Transfer.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void saveDraftDoesNotPostAndUpdateDraftChangesDetails() {
        Transfer draft = service.saveDraft(2L, 3L, new BigDecimal("25.00"),
                LocalDate.of(2026, 9, 30), "Initial memo");
        ReflectionTestUtils.setField(draft, "id", 10L);
        when(transfers.findLockedByIdAndCompanyId(10L, 1L)).thenReturn(Optional.of(draft));

        Transfer updated = service.updateDraft(10L, 3L, 2L, new BigDecimal("35.00"),
                LocalDate.of(2026, 10, 1), "Updated memo");

        assertEquals(TransferStatus.DRAFT, updated.getStatus());
        assertSame(to, updated.getFromAccount());
        assertSame(from, updated.getToAccount());
        assertEquals(new BigDecimal("35.00"), updated.getAmount());
        assertEquals(LocalDate.of(2026, 10, 1), updated.getTransferDate());
        assertEquals("Updated memo", updated.getNotes());
        assertNull(updated.getJournal());
        verifyNoInteractions(journalService, journals);
    }

    @Test
    void saveDraftRejectsInactiveAccounts() {
        to.setActive(false);

        assertThrows(IllegalArgumentException.class, () -> service.saveDraft(
                2L, 3L, new BigDecimal("25.00"), LocalDate.of(2026, 9, 30), "Inactive account"));

        verify(transfers, never()).save(any(Transfer.class));
    }

    @Test
    void getAllTransfersUsesNewestDateFirstRepositoryOrder() {
        List<Transfer> ordered = List.of(transfer(TransferStatus.POSTED));
        when(transfers.findAllByCompanyIdOrderByTransferDateDescIdDesc(1L)).thenReturn(ordered);

        assertSame(ordered, service.getAllTransfers());

        verify(transfers).findAllByCompanyIdOrderByTransferDateDescIdDesc(1L);
    }

    @Test
    void deleteDraftRemovesOnlyDraftTransfers() {
        Transfer draft = transfer(TransferStatus.DRAFT);
        when(transfers.findLockedByIdAndCompanyId(10L, 1L)).thenReturn(Optional.of(draft));

        service.deleteDraft(10L);

        verify(transfers).delete(draft);
        verifyNoInteractions(journalService, journals);
    }

    @Test
    void deleteDraftRejectsPostedTransfers() {
        Transfer posted = transfer(TransferStatus.POSTED);
        when(transfers.findLockedByIdAndCompanyId(10L, 1L)).thenReturn(Optional.of(posted));

        assertThrows(IllegalStateException.class, () -> service.deleteDraft(10L));

        verify(transfers, never()).delete(any(Transfer.class));
    }

    @Test
    void postTransferCreatesAndLinksPostedJournal() {
        Transfer draft = transfer(TransferStatus.DRAFT);
        ReflectionTestUtils.setField(draft, "id", 10L);
        when(transfers.findLockedByIdAndCompanyId(10L, 1L)).thenReturn(Optional.of(draft));
        GeneralJournal savedJournal = new GeneralJournal();
        savedJournal.setId(20L);
        when(journalService.createJournal(any(GeneralJournal.class))).thenReturn(savedJournal);
        savedJournal.setStatus(JournalStatus.POSTED);
        when(journalService.postJournal(20L, "test-user")).thenReturn(savedJournal);

        Transfer posted = service.postTransfer(10L, "test-user");

        assertEquals(TransferStatus.POSTED, posted.getStatus());
        assertSame(savedJournal, posted.getJournal());
        verify(journalService).createJournal(argThat(journal ->
                journal.getReference().equals("TRANSFER-10")
                        && journal.getJournalDate().equals(draft.getTransferDate())
                        && journal.getEntries().size() == 2));
    }

    @Test
    void voidTransferReversesJournalAndKeepsTransferRecord() {
        Transfer posted = transfer(TransferStatus.POSTED);
        GeneralJournal original = new GeneralJournal();
        original.setId(20L);
        original.setStatus(JournalStatus.POSTED);
        posted.setJournal(original);
        when(journalService.reverseJournal(20L, "Transfer reversed: Duplicate entry"))
                .thenReturn(new GeneralJournal());
        when(transfers.findLockedByIdAndCompanyId(10L, 1L)).thenReturn(Optional.of(posted));
        when(journals.findByIdAndCompanyId(20L, 1L)).thenReturn(Optional.of(original));

        Transfer reversed = service.voidTransfer(10L, "Duplicate entry");

        assertEquals(TransferStatus.REVERSED, reversed.getStatus());
        assertEquals("Duplicate entry", reversed.getVoidReason());
        assertNotNull(reversed.getVoidedAt());
        verify(journalService).reverseJournal(20L, "Transfer reversed: Duplicate entry");
        verify(transfers, never()).delete(any(Transfer.class));
    }

    @Test
    void reconciledTransferCannotBeReversed() {
        Transfer posted = transfer(TransferStatus.POSTED);
        GeneralJournal original = new GeneralJournal();
        original.setId(20L);
        original.setStatus(JournalStatus.POSTED);
        JournalEntry clearedEntry = new JournalEntry();
        clearedEntry.setCleared(true);
        original.setEntries(List.of(clearedEntry));
        posted.setJournal(original);
        when(transfers.findLockedByIdAndCompanyId(10L, 1L)).thenReturn(Optional.of(posted));
        when(journals.findByIdAndCompanyId(20L, 1L)).thenReturn(Optional.of(original));

        assertThrows(IllegalStateException.class, () -> service.voidTransfer(10L, "Duplicate entry"));

        verifyNoInteractions(journalService);
        verify(transfers, never()).save(any(Transfer.class));
    }

    private Transfer transfer(TransferStatus status) {
        Transfer transfer = new Transfer();
        transfer.setCompany(company);
        transfer.setFromAccount(from);
        transfer.setToAccount(to);
        transfer.setAmount(new BigDecimal("25.00"));
        transfer.setTransferDate(LocalDate.of(2026, 9, 30));
        transfer.setStatus(status);
        return transfer;
    }

    private ChartOfAccount account(Long id, String number) {
        ChartOfAccount account = new ChartOfAccount();
        account.setId(id);
        account.setAccountNumber(number);
        account.setAccountType(AccountType.ASSET);
        account.setActive(true);
        return account;
    }
}
