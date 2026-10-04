package com.cogitosum.service;

import com.cogitosum.entity.*;
import com.cogitosum.repository.BankReconciliationLineRepository;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BankReconciliationServiceTest {

    @Mock private BankTransactionRepository bankTransactions;
    @Mock private BankReconciliationSessionRepository sessions;
    @Mock private BankReconciliationLineRepository reconciliationLines;
    @Mock private ChartOfAccountRepository accounts;
    @Mock private GeneralLedgerRepository ledgers;
    @Mock private JournalEntryRepository journalEntries;
    @Mock private ChequeService chequeService;
    @Mock private CurrentCompanyContext companyContext;

    private BankReconciliationService service;
    private Company company;
    private ChartOfAccount account;

    @BeforeEach
    void setUp() {
        service = new BankReconciliationService(
                bankTransactions, sessions, reconciliationLines, accounts, ledgers, journalEntries,
                chequeService, companyContext);
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
    void reconciliationUsesSelectedJournalEntriesAndClearsThemAtomically() {
        GeneralJournal journal = new GeneralJournal();
        journal.setJournalDate(LocalDate.of(2026, 1, 30));
        journal.setStatus(JournalStatus.POSTED);
        JournalEntry deposit = new JournalEntry();
        deposit.setId(201L);
        deposit.setAccount(account);
        deposit.setJournal(journal);
        deposit.setDebit(new BigDecimal("25.00"));
        deposit.setCredit(BigDecimal.ZERO);
        JournalEntry bankFee = new JournalEntry();
        bankFee.setId(202L);
        bankFee.setAccount(account);
        bankFee.setJournal(journal);
        bankFee.setDebit(BigDecimal.ZERO);
        bankFee.setCredit(new BigDecimal("5.00"));

        when(accounts.findLockedByIdAndCompanyId(10L, 1L)).thenReturn(Optional.of(account));
        when(journalEntries.findByIdInAndJournalCompanyId(List.of(201L, 202L), 1L))
                .thenReturn(List.of(deposit, bankFee));
        when(sessions.findFirstByCompanyIdAndBankAccountIdOrderByStatementDateDescIdDesc(1L, 10L))
                .thenReturn(Optional.empty());
        when(sessions.save(any(BankReconciliationSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BankReconciliationSession session = service.reconcile(
                10L, LocalDate.of(2026, 1, 31), new BigDecimal("120.00"), List.of(201L, 202L));

        assertEquals(new BigDecimal("100.00"), session.getOpeningBalance());
        assertEquals(new BigDecimal("20.00"), session.getTransactionTotal());
        assertEquals(new BigDecimal("120.00"), session.getEndingBalance());
        assertEquals(new BigDecimal("100.00"), session.getRegisterBalance());
        assertTrue(session.isReportLinesCaptured());
        assertTrue(deposit.isCleared());
        assertTrue(bankFee.isCleared());
        verifyNoInteractions(bankTransactions);
        verify(journalEntries).saveAll(List.of(deposit, bankFee));
        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<Iterable<BankReconciliationLine>> capturedLines =
                (ArgumentCaptor) ArgumentCaptor.forClass(Iterable.class);
        verify(reconciliationLines).saveAll(capturedLines.capture());
        List<BankReconciliationLine> savedLines = new java.util.ArrayList<>();
        capturedLines.getValue().forEach(savedLines::add);
        assertEquals(2, savedLines.size());
        assertEquals(201L, savedLines.get(0).getJournalEntryId());
        assertEquals(new BigDecimal("25.00"), savedLines.get(0).getAmount());
        verify(chequeService, times(2)).markClearedByJournal(journal);
    }

    @Test
    void reconciliationSnapshotKeepsTransferReferenceAndCounterparty() {
        GeneralJournal journal = new GeneralJournal();
        journal.setJournalDate(LocalDate.of(2026, 1, 30));
        journal.setStatus(JournalStatus.POSTED);
        journal.setReference("TRANSFER-15");
        journal.setNarrative("Transfer from 1001 to 1440");

        JournalEntry bankEntry = new JournalEntry();
        bankEntry.setId(203L);
        bankEntry.setAccount(account);
        bankEntry.setJournal(journal);
        bankEntry.setDebit(new BigDecimal("25.00"));
        bankEntry.setCredit(BigDecimal.ZERO);

        ChartOfAccount destination = new ChartOfAccount();
        destination.setId(11L);
        destination.setAccountName("Term Deposit");
        JournalEntry destinationEntry = new JournalEntry();
        destinationEntry.setAccount(destination);
        destinationEntry.setJournal(journal);
        journal.setEntries(List.of(bankEntry, destinationEntry));

        when(accounts.findLockedByIdAndCompanyId(10L, 1L)).thenReturn(Optional.of(account));
        when(journalEntries.findByIdInAndJournalCompanyId(List.of(203L), 1L))
                .thenReturn(List.of(bankEntry));
        when(sessions.findFirstByCompanyIdAndBankAccountIdOrderByStatementDateDescIdDesc(1L, 10L))
                .thenReturn(Optional.empty());
        when(sessions.save(any(BankReconciliationSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.reconcile(10L, LocalDate.of(2026, 1, 31),
                new BigDecimal("125.00"), List.of(203L));

        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<Iterable<BankReconciliationLine>> captured =
                (ArgumentCaptor) ArgumentCaptor.forClass(Iterable.class);
        verify(reconciliationLines).saveAll(captured.capture());
        BankReconciliationLine line = captured.getValue().iterator().next();
        assertEquals(BankReconciliationLineType.TRANSFER, line.getTransactionType());
        assertEquals("TRANSFER-15", line.getDocumentNumber());
        assertEquals("Term Deposit", line.getName());
    }

    @Test
    void legacyReportRecoveryUsesClearedEntriesOnlyFromTheStatementPeriod() {
        account.setOpeningBalanceDate(LocalDate.of(2026, 6, 30));
        BankReconciliationSession session = new BankReconciliationSession();
        session.setId(32L);
        session.setCompany(company);
        session.setBankAccount(account);
        session.setStatementDate(LocalDate.of(2026, 9, 30));
        session.setCompletedAt(LocalDate.of(2026, 10, 2).atTime(10, 0));

        GeneralJournal eligibleJournal = new GeneralJournal();
        eligibleJournal.setJournalDate(LocalDate.of(2026, 9, 8));
        eligibleJournal.setPostedDate(LocalDate.of(2026, 9, 8).atStartOfDay());
        eligibleJournal.setStatus(JournalStatus.POSTED);
        eligibleJournal.setReference("TRANSFER-15");
        eligibleJournal.setNarrative("Transfer from 1001 to 1440");
        JournalEntry eligible = new JournalEntry();
        eligible.setId(301L);
        eligible.setLineNumber(1);
        eligible.setAccount(account);
        eligible.setJournal(eligibleJournal);
        eligible.setCleared(true);
        eligible.setDebit(BigDecimal.ZERO);
        eligible.setCredit(new BigDecimal("25.00"));

        GeneralJournal laterJournal = new GeneralJournal();
        laterJournal.setJournalDate(LocalDate.of(2026, 9, 12));
        laterJournal.setPostedDate(LocalDate.of(2026, 10, 2).atTime(11, 16));
        laterJournal.setStatus(JournalStatus.POSTED);
        laterJournal.setReference("TRANSFER-16");
        JournalEntry postedAfterReconciliation = new JournalEntry();
        postedAfterReconciliation.setId(302L);
        postedAfterReconciliation.setLineNumber(1);
        postedAfterReconciliation.setAccount(account);
        postedAfterReconciliation.setJournal(laterJournal);
        postedAfterReconciliation.setCleared(true);
        postedAfterReconciliation.setDebit(BigDecimal.ZERO);
        postedAfterReconciliation.setCredit(new BigDecimal("25.00"));

        when(sessions.findByCompanyIdAndBankAccountIdOrderByStatementDateDescIdDesc(1L, 10L))
                .thenReturn(List.of());
        when(journalEntries.findByJournalCompanyIdAndAccountIdAndClearedAndJournalStatus(
                1L, 10L, true, JournalStatus.POSTED))
                .thenReturn(List.of(eligible, postedAfterReconciliation));

        List<BankReconciliationLine> recovered = service.recoverLegacyReportLines(session);

        assertEquals(1, recovered.size());
        assertEquals(301L, recovered.get(0).getJournalEntryId());
        assertEquals("TRANSFER-15", recovered.get(0).getDocumentNumber());
    }

    @Test
    void reconciliationRejectsAnEndingBalanceThatDoesNotTieToSelectedEntries() {
        GeneralJournal journal = new GeneralJournal();
        journal.setJournalDate(LocalDate.of(2026, 1, 30));
        journal.setStatus(JournalStatus.POSTED);
        JournalEntry entry = new JournalEntry();
        entry.setId(201L);
        entry.setAccount(account);
        entry.setJournal(journal);
        entry.setDebit(new BigDecimal("25.00"));
        entry.setCredit(BigDecimal.ZERO);

        when(accounts.findLockedByIdAndCompanyId(10L, 1L)).thenReturn(Optional.of(account));
        when(journalEntries.findByIdInAndJournalCompanyId(List.of(201L), 1L)).thenReturn(List.of(entry));
        when(sessions.findFirstByCompanyIdAndBankAccountIdOrderByStatementDateDescIdDesc(1L, 10L))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.reconcile(
                10L, LocalDate.of(2026, 1, 31), new BigDecimal("126.00"), List.of(201L)));

        verify(sessions, never()).save(any());
        verify(journalEntries, never()).saveAll(any());
    }

    @Test
    void reconciliationRejectsEntriesDatedAfterTheStatement() {
        GeneralJournal futureJournal = new GeneralJournal();
        futureJournal.setJournalDate(LocalDate.of(2026, 2, 1));
        futureJournal.setStatus(JournalStatus.POSTED);
        JournalEntry entry = new JournalEntry();
        entry.setId(201L);
        entry.setAccount(account);
        entry.setJournal(futureJournal);
        entry.setDebit(new BigDecimal("25.00"));
        entry.setCredit(BigDecimal.ZERO);

        when(accounts.findLockedByIdAndCompanyId(10L, 1L)).thenReturn(Optional.of(account));
        when(journalEntries.findByIdInAndJournalCompanyId(List.of(201L), 1L)).thenReturn(List.of(entry));

        assertThrows(IllegalArgumentException.class, () -> service.reconcile(
                10L, LocalDate.of(2026, 1, 31), new BigDecimal("125.00"), List.of(201L)));

        verify(sessions, never()).save(any());
        verify(journalEntries, never()).saveAll(any());
    }

    @Test
    void reconciliationAllowsAnEmptyStatementWhenTheBalanceIsUnchanged() {
        when(accounts.findLockedByIdAndCompanyId(10L, 1L)).thenReturn(Optional.of(account));
        when(sessions.findFirstByCompanyIdAndBankAccountIdOrderByStatementDateDescIdDesc(1L, 10L))
                .thenReturn(Optional.empty());
        when(sessions.save(any(BankReconciliationSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BankReconciliationSession session = service.reconcile(
                10L, LocalDate.of(2026, 1, 31), new BigDecimal("100.00"), List.of());

        assertEquals(BigDecimal.ZERO.setScale(2), session.getTransactionTotal());
        assertEquals(new BigDecimal("100.00"), session.getEndingBalance());
        verify(journalEntries, never()).findByIdInAndJournalCompanyId(anyList(), anyLong());
        verify(journalEntries, never()).saveAll(any());
    }
}
