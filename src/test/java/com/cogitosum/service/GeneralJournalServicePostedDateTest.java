package com.cogitosum.service;

import com.cogitosum.entity.GeneralJournal;
import com.cogitosum.entity.ChartOfAccount;
import com.cogitosum.entity.AccountType;
import com.cogitosum.entity.Company;
import com.cogitosum.entity.GeneralLedger;
import com.cogitosum.entity.JournalEntry;
import com.cogitosum.entity.JournalStatus;
import com.cogitosum.repository.ChartOfAccountRepository;
import com.cogitosum.repository.GeneralJournalRepository;
import com.cogitosum.repository.GeneralLedgerRepository;
import com.cogitosum.service.GeneralLedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeneralJournalServicePostedDateTest {

    @Mock private GeneralJournalRepository journalRepository;
    @Mock private GeneralLedgerRepository ledgerRepository;
    @Mock private ChartOfAccountRepository accountRepository;
    @Mock private GeneralLedgerService ledgerService;
    @Mock private FiscalYearService fiscalYearService;
    @Mock private CurrentCompanyContext companyContext;

    private GeneralJournalService service;
    private Company company;

    @BeforeEach
    void setUp() {
        service = new GeneralJournalService();
        ReflectionTestUtils.setField(service, "generalJournalRepository", journalRepository);
        ReflectionTestUtils.setField(service, "generalLedgerRepository", ledgerRepository);
        ReflectionTestUtils.setField(service, "generalLedgerService", ledgerService);
        ReflectionTestUtils.setField(service, "chartOfAccountRepository", accountRepository);
        ReflectionTestUtils.setField(service, "fiscalYearService", fiscalYearService);
        ReflectionTestUtils.setField(service, "companyContext", companyContext);
        company = new Company();
        company.setPostedJournalEditingEnabled(true);
        lenient().when(companyContext.requireCompany()).thenReturn(company);
        lenient().when(companyContext.requireCompanyId()).thenReturn(7L);
    }

    @Test
    void changesOnlyTheDateOfAPostedJournalInAnOpenPeriod() {
        LocalDate originalDate = LocalDate.of(2026, 9, 29);
        LocalDate newDate = LocalDate.of(2026, 9, 30);
        GeneralJournal journal = postedJournal(originalDate);
        JournalEntry originalEntry = journal.getEntries().get(0);
        when(journalRepository.findLockedByIdAndCompanyId(41L, 7L)).thenReturn(Optional.of(journal));
        when(fiscalYearService.isLocked(originalDate)).thenReturn(false);
        when(fiscalYearService.isLocked(newDate)).thenReturn(false);
        when(journalRepository.save(journal)).thenReturn(journal);

        GeneralJournalService.PostedJournalDateChange change =
                service.updatePostedJournalDate(41L, newDate);

        assertEquals(originalDate, change.previousDate());
        assertEquals(newDate, change.newDate());
        assertEquals(newDate, journal.getJournalDate());
        assertEquals("JL-41", journal.getJournalNumber());
        assertEquals("Original narrative", journal.getNarrative());
        assertEquals("SOURCE-41", journal.getReference());
        assertEquals(JournalStatus.POSTED, journal.getStatus());
        assertEquals(LocalDateTime.of(2026, 9, 29, 12, 0), journal.getPostedDate());
        assertSame(originalEntry, journal.getEntries().get(0));
        verify(journalRepository).save(journal);
    }

    @Test
    void refusesDateOnlyUpdateForNonPostedJournals() {
        GeneralJournal journal = postedJournal(LocalDate.of(2026, 9, 29));
        journal.setStatus(JournalStatus.DRAFT);
        when(journalRepository.findLockedByIdAndCompanyId(41L, 7L)).thenReturn(Optional.of(journal));

        assertThrows(IllegalStateException.class,
                () -> service.updatePostedJournalDate(41L, LocalDate.of(2026, 9, 30)));

        verify(journalRepository, never()).save(journal);
    }

    @Test
    void refusesChangingDatesInClosedFiscalYears() {
        LocalDate originalDate = LocalDate.of(2026, 9, 29);
        GeneralJournal journal = postedJournal(originalDate);
        when(journalRepository.findLockedByIdAndCompanyId(41L, 7L)).thenReturn(Optional.of(journal));
        when(fiscalYearService.isLocked(originalDate)).thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> service.updatePostedJournalDate(41L, LocalDate.of(2026, 9, 30)));

        assertEquals(originalDate, journal.getJournalDate());
        verify(journalRepository, never()).save(journal);
    }

    @Test
    void refusesPostedJournalChangesWhenCompanyFlagIsDisabled() {
        company.setPostedJournalEditingEnabled(false);
        GeneralJournal journal = postedJournal(LocalDate.of(2026, 9, 29));
        when(journalRepository.findLockedByIdAndCompanyId(41L, 7L)).thenReturn(Optional.of(journal));
        GeneralJournal replacement = new GeneralJournal();
        replacement.setJournalDate(LocalDate.of(2026, 9, 30));

        assertThrows(IllegalStateException.class,
                () -> service.updatePostedJournalDate(41L, replacement.getJournalDate()));
        assertThrows(IllegalStateException.class, () -> service.updateJournal(41L, replacement));

        verify(journalRepository, never()).save(journal);
        verifyNoInteractions(ledgerRepository);
    }

    @Test
    void identifiesManuallyEnteredJournalsForCopy() {
        assertTrue(service.isDirectlyEntered(new GeneralJournal()));

        GeneralJournal generated = new GeneralJournal();
        generated.setReference("CARD-CHARGE-17");
        assertFalse(service.isDirectlyEntered(generated));

        generated.setReference("Reversal of JL-41");
        assertFalse(service.isDirectlyEntered(generated));
    }

    @Test
    void fullyUpdatesAnyPostedJournalAndRecalculatesLedgerBalances() {
        LocalDate journalDate = LocalDate.of(2026, 9, 29);
        GeneralJournal journal = postedJournal(journalDate);
        journal.setReference("TRANSFER-15");
        ChartOfAccount oldDebitAccount = account(11L);
        ChartOfAccount oldCreditAccount = account(12L);
        ChartOfAccount newDebitAccount = account(13L);
        ChartOfAccount newCreditAccount = account(14L);
        JournalEntry clearedDebit = entry(oldDebitAccount, "100.00", "0.00");
        clearedDebit.setCleared(true);
        JournalEntry oldCredit = entry(oldCreditAccount, "0.00", "100.00");
        journal.getEntries().clear();
        journal.getEntries().add(clearedDebit);
        journal.getEntries().add(oldCredit);

        GeneralLedger oldDebitLedger = ledger(oldDebitAccount, "100.00", "0.00");
        GeneralLedger oldCreditLedger = ledger(oldCreditAccount, "0.00", "100.00");
        GeneralLedger newDebitLedger = ledger(newDebitAccount, "0.00", "0.00");
        GeneralLedger newCreditLedger = ledger(newCreditAccount, "0.00", "0.00");
        when(journalRepository.findLockedByIdAndCompanyId(41L, 7L)).thenReturn(Optional.of(journal));
        when(accountRepository.findByIdAndCompanyId(13L, 7L)).thenReturn(Optional.of(newDebitAccount));
        when(accountRepository.findByIdAndCompanyId(14L, 7L)).thenReturn(Optional.of(newCreditAccount));
        when(ledgerRepository.findByCompanyIdAndAccountId(7L, 11L)).thenReturn(Optional.of(oldDebitLedger));
        when(ledgerRepository.findByCompanyIdAndAccountId(7L, 12L)).thenReturn(Optional.of(oldCreditLedger));
        when(ledgerRepository.findByCompanyIdAndAccountId(7L, 13L)).thenReturn(Optional.of(newDebitLedger));
        when(ledgerRepository.findByCompanyIdAndAccountId(7L, 14L)).thenReturn(Optional.of(newCreditLedger));
        when(journalRepository.save(journal)).thenReturn(journal);

        GeneralJournal replacement = new GeneralJournal();
        replacement.setJournalDate(journalDate);
        replacement.setNarrative("Corrected transfer");
        replacement.setReference("TRANSFER-15-CORRECTED");
        replacement.getEntries().add(entry(account(13L), "150.00", "0.00"));
        replacement.getEntries().add(entry(account(14L), "0.00", "150.00"));

        GeneralJournal updated = service.updateJournal(41L, replacement);

        assertSame(journal, updated);
        assertEquals("Corrected transfer", journal.getNarrative());
        assertEquals("TRANSFER-15-CORRECTED", journal.getReference());
        assertEquals(JournalStatus.POSTED, journal.getStatus());
        assertEquals(LocalDateTime.of(2026, 9, 29, 12, 0), journal.getPostedDate());
        assertEquals(2, journal.getEntries().size());
        assertFalse(journal.getEntries().get(0).isCleared());
        assertEquals(new BigDecimal("0.00"), oldDebitLedger.getDebitBalance());
        assertEquals(new BigDecimal("0.00"), oldCreditLedger.getCreditBalance());
        assertEquals(new BigDecimal("150.00"), newDebitLedger.getDebitBalance());
        assertEquals(new BigDecimal("150.00"), newCreditLedger.getCreditBalance());
        verify(ledgerRepository).save(oldDebitLedger);
        verify(ledgerRepository).save(oldCreditLedger);
        verify(ledgerRepository).save(newDebitLedger);
        verify(ledgerRepository).save(newCreditLedger);
    }

    @Test
    void refusesFullUpdateWhenThePostedJournalIsInAClosedFiscalYear() {
        LocalDate closedDate = LocalDate.of(2026, 9, 29);
        GeneralJournal journal = postedJournal(closedDate);
        when(journalRepository.findLockedByIdAndCompanyId(41L, 7L)).thenReturn(Optional.of(journal));
        when(fiscalYearService.isLocked(closedDate)).thenReturn(true);

        GeneralJournal replacement = new GeneralJournal();
        replacement.setJournalDate(closedDate);

        assertThrows(IllegalStateException.class, () -> service.updateJournal(41L, replacement));

        verifyNoInteractions(ledgerRepository);
        verify(journalRepository, never()).save(journal);
    }

    private GeneralJournal postedJournal(LocalDate date) {
        GeneralJournal journal = new GeneralJournal();
        journal.setId(41L);
        journal.setJournalNumber("JL-41");
        journal.setJournalDate(date);
        journal.setNarrative("Original narrative");
        journal.setReference("SOURCE-41");
        journal.setStatus(JournalStatus.POSTED);
        journal.setPostedDate(LocalDateTime.of(2026, 9, 29, 12, 0));
        journal.getEntries().add(new JournalEntry());
        return journal;
    }

    private ChartOfAccount account(Long id) {
        ChartOfAccount account = new ChartOfAccount();
        account.setId(id);
        account.setAccountType(AccountType.ASSET);
        return account;
    }

    private JournalEntry entry(ChartOfAccount account, String debit, String credit) {
        JournalEntry entry = new JournalEntry();
        entry.setAccount(account);
        entry.setDebit(new BigDecimal(debit));
        entry.setCredit(new BigDecimal(credit));
        return entry;
    }

    private GeneralLedger ledger(ChartOfAccount account, String debit, String credit) {
        GeneralLedger ledger = new GeneralLedger();
        ledger.setAccount(account);
        ledger.setDebitBalance(new BigDecimal(debit));
        ledger.setCreditBalance(new BigDecimal(credit));
        return ledger;
    }
}
