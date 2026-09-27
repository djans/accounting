package com.cogitosum.service;

import com.cogitosum.entity.*;
import com.cogitosum.repository.BankReconciliationSessionRepository;
import com.cogitosum.repository.BankTransactionRepository;
import com.cogitosum.repository.ChartOfAccountRepository;
import com.cogitosum.repository.GeneralLedgerRepository;
import com.cogitosum.repository.JournalEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BankReconciliationServiceTest {

    @Mock private BankTransactionRepository bankTransactions;
    @Mock private BankReconciliationSessionRepository sessions;
    @Mock private ChartOfAccountRepository accounts;
    @Mock private GeneralLedgerRepository ledgers;
    @Mock private JournalEntryRepository journalEntries;
    @Mock private CurrentCompanyContext companyContext;

    private BankReconciliationService service;
    private Company company;
    private ChartOfAccount account;

    @BeforeEach
    void setUp() {
        service = new BankReconciliationService(
                bankTransactions, sessions, accounts, ledgers, journalEntries, companyContext);
        company = new Company();
        company.setId(1L);
        account = new ChartOfAccount();
        account.setId(10L);
        account.setCategory(AccountCategory.BANK);
        account.setOpeningBalance(new BigDecimal("100.00"));
        lenient().when(companyContext.requireCompanyId()).thenReturn(1L);
        lenient().when(companyContext.requireCompany()).thenReturn(company);
    }

    @Test
    void importCsvAcceptsUtf8BomQuotedFieldsAndParenthesizedNegativeAmounts() {
        when(accounts.findByIdAndCompanyId(10L, 1L)).thenReturn(Optional.of(account));
        when(bankTransactions.existsByCompanyIdAndBankAccountIdAndSourceHash(eq(1L), eq(10L), anyString()))
                .thenReturn(false);
        when(bankTransactions.findByCompanyIdAndBankAccountIdAndSourceRowHashIn(eq(1L), eq(10L), anyCollection()))
                .thenReturn(List.of());

        String csv = "\uFEFFDate,Description,Amount,Reference\r\n"
                + "2026-01-02,\"Coffee, meeting\",(12.50),\"REF, 1\"\r\n"
                + "2026-01-03,\"Deposit \"\"A\"\"\",100.00,\r\n";
        BankReconciliationService.ImportResult result = service.importCsv(
                10L, csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertEquals(2, result.importedCount());
        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<Iterable<BankTransaction>> captured =
                (ArgumentCaptor) ArgumentCaptor.forClass(Iterable.class);
        verify(bankTransactions).saveAll(captured.capture());
        List<BankTransaction> imported = new java.util.ArrayList<>();
        captured.getValue().forEach(imported::add);
        assertEquals(new BigDecimal("-12.50"), imported.get(0).getAmount());
        assertEquals("Coffee, meeting", imported.get(0).getDescription());
        assertEquals("REF, 1", imported.get(0).getReference());
        assertEquals("Deposit \"A\"", imported.get(1).getDescription());
        assertFalse(imported.get(0).isReconciled());
        assertEquals(result.sourceHash(), imported.get(0).getSourceHash());
    }

    @Test
    void importCsvRejectsInvalidRowsBeforeWritingAnything() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                service.importCsv(10L, "Date,Description,Amount\n2026-01-01,Invalid,12.345\n"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        assertTrue(exception.getMessage().contains("invalid Amount"));
        verifyNoInteractions(bankTransactions);
    }

    @Test
    void importCsvRejectsRowsAlreadyImportedForThisCompanyAndAccount() {
        when(accounts.findByIdAndCompanyId(10L, 1L)).thenReturn(Optional.of(account));
        when(bankTransactions.existsByCompanyIdAndBankAccountIdAndSourceHash(eq(1L), eq(10L), anyString()))
                .thenReturn(false);
        when(bankTransactions.findByCompanyIdAndBankAccountIdAndSourceRowHashIn(eq(1L), eq(10L), anyCollection()))
                .thenReturn(List.of(new BankTransaction()));

        assertThrows(IllegalArgumentException.class, () ->
                service.importCsv(10L, "Date,Description,Amount\n2026-01-01,Duplicate,12.00\n"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        verify(bankTransactions, never()).saveAll(any());
    }

    @Test
    void reconciliationValidatesTotalsThenClearsOneToOneMatchesAtomically() {
        BankTransaction transaction = new BankTransaction();
        transaction.setId(101L);
        transaction.setAmount(new BigDecimal("25.00"));
        transaction.setReconciled(false);

        GeneralJournal journal = new GeneralJournal();
        journal.setStatus(JournalStatus.POSTED);
        JournalEntry entry = new JournalEntry();
        entry.setId(201L);
        entry.setAccount(account);
        entry.setJournal(journal);
        entry.setDebit(new BigDecimal("25.00"));
        entry.setCredit(BigDecimal.ZERO);
        entry.setCleared(false);

        when(accounts.findLockedByIdAndCompanyId(10L, 1L)).thenReturn(Optional.of(account));
        when(bankTransactions.findByCompanyIdAndBankAccountIdAndReconciledFalseOrderByTransactionDateAscIdAsc(1L, 10L))
                .thenReturn(List.of(transaction));
        when(journalEntries.findByIdInAndJournalCompanyId(List.of(201L), 1L)).thenReturn(List.of(entry));
        when(sessions.findFirstByCompanyIdAndBankAccountIdOrderByStatementDateDescIdDesc(1L, 10L))
                .thenReturn(Optional.empty());
        when(sessions.save(any(BankReconciliationSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BankReconciliationSession session = service.reconcile(
                10L, LocalDate.of(2026, 1, 31), new BigDecimal("125.00"), Map.of(101L, 201L));

        assertEquals(new BigDecimal("100.00"), session.getOpeningBalance());
        assertEquals(new BigDecimal("25.00"), session.getTransactionTotal());
        assertEquals(new BigDecimal("125.00"), session.getEndingBalance());
        assertTrue(transaction.isReconciled());
        assertSame(session, transaction.getReconciliationSession());
        assertTrue(entry.isCleared());
        verify(bankTransactions).saveAll(List.of(transaction));
        verify(journalEntries).saveAll(List.of(entry));
    }

    @Test
    void reconciliationRejectsAnEndingBalanceThatDoesNotTieToImportedTransactions() {
        BankTransaction transaction = new BankTransaction();
        transaction.setId(101L);
        transaction.setAmount(new BigDecimal("25.00"));

        GeneralJournal journal = new GeneralJournal();
        journal.setStatus(JournalStatus.POSTED);
        JournalEntry entry = new JournalEntry();
        entry.setId(201L);
        entry.setAccount(account);
        entry.setJournal(journal);
        entry.setDebit(new BigDecimal("25.00"));
        entry.setCredit(BigDecimal.ZERO);

        when(accounts.findLockedByIdAndCompanyId(10L, 1L)).thenReturn(Optional.of(account));
        when(bankTransactions.findByCompanyIdAndBankAccountIdAndReconciledFalseOrderByTransactionDateAscIdAsc(1L, 10L))
                .thenReturn(List.of(transaction));
        when(journalEntries.findByIdInAndJournalCompanyId(List.of(201L), 1L)).thenReturn(List.of(entry));
        when(sessions.findFirstByCompanyIdAndBankAccountIdOrderByStatementDateDescIdDesc(1L, 10L))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.reconcile(
                10L, LocalDate.of(2026, 1, 31), new BigDecimal("126.00"), Map.of(101L, 201L)));

        verify(sessions, never()).save(any());
        verify(bankTransactions, never()).saveAll(any());
        verify(journalEntries, never()).saveAll(any());
    }
}
