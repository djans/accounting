package com.cogitosum.service;

import com.cogitosum.entity.AccountType;
import com.cogitosum.entity.ChartOfAccount;
import com.cogitosum.entity.GeneralJournal;
import com.cogitosum.entity.JournalEntry;
import com.cogitosum.entity.JournalStatus;
import com.cogitosum.repository.ChartOfAccountRepository;
import com.cogitosum.repository.GeneralLedgerRepository;
import com.cogitosum.repository.JournalEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountingReportServiceDatedTest {

    @Mock private GeneralLedgerRepository generalLedgerRepository;
    @Mock private ChartOfAccountRepository chartOfAccountRepository;
    @Mock private JournalEntryRepository journalEntryRepository;
    @Mock private CurrentCompanyContext companyContext;
    @InjectMocks private AccountingReportService service;

    @Test
    void reportsSeparateClosingEntriesAndRespectTheSelectedDates() {
        ChartOfAccount cash = account(1L, "1000", "Cash", AccountType.ASSET);
        ChartOfAccount retainedEarnings = account(2L, "3100", "Retained earnings", AccountType.EQUITY);
        ChartOfAccount revenue = account(3L, "4000", "Revenue", AccountType.REVENUE);
        GeneralJournal sale = journal(10L, "J-10", "SALE-1", LocalDate.of(2026, 3, 1));
        GeneralJournal close = journal(11L, "J-11", "FY-CLOSE-1", LocalDate.of(2026, 3, 31));
        close.setStatus(JournalStatus.REVERSED);
        GeneralJournal reopen = journal(12L, "REV-J-11", "Reversal of J-11", LocalDate.of(2026, 4, 2));
        List<JournalEntry> entries = List.of(
                entry(sale, cash, "100.00", "0.00"),
                entry(sale, revenue, "0.00", "100.00"),
                entry(close, revenue, "100.00", "0.00"),
                entry(close, retainedEarnings, "0.00", "100.00"),
                entry(reopen, retainedEarnings, "100.00", "0.00"),
                entry(reopen, revenue, "0.00", "100.00"));
        when(companyContext.requireCompanyId()).thenReturn(77L);
        when(chartOfAccountRepository.findAllByCompanyIdOrderByAccountNumberAsc(77L))
                .thenReturn(List.of(cash, retainedEarnings, revenue));
        when(journalEntryRepository.findReportEntriesThroughDate(eq(77L), anyList(), any(LocalDate.class)))
                .thenReturn(entries);
        when(journalEntryRepository.findReportEntriesBetweenDates(
                eq(77L), anyList(), any(LocalDate.class), any(LocalDate.class))).thenReturn(entries);

        Map<String, Object> reports = service.getFinancialReports(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 30), LocalDate.of(2026, 4, 30));

        Map<String, Object> beforeClose = castMap(reports.get("trialBalanceBeforeClose"));
        Map<String, Object> afterClose = castMap(reports.get("trialBalance"));
        Map<String, Object> income = castMap(reports.get("incomeStatement"));
        Map<String, Object> balanceSheet = castMap(reports.get("balanceSheet"));
        assertEquals(new BigDecimal("100.00"), beforeClose.get("totalDebits"));
        assertEquals(new BigDecimal("300.00"), afterClose.get("totalDebits"));
        assertEquals(new BigDecimal("100.00"), income.get("netIncome"));
        assertEquals(new BigDecimal("100.00"), balanceSheet.get("totalEquityPlusNetIncome"));
        assertEquals(Boolean.TRUE, balanceSheet.get("balanced"));
    }

    private ChartOfAccount account(Long id, String number, String name, AccountType type) {
        ChartOfAccount account = new ChartOfAccount();
        account.setId(id);
        account.setAccountNumber(number);
        account.setAccountName(name);
        account.setAccountType(type);
        return account;
    }

    private GeneralJournal journal(Long id, String number, String reference, LocalDate date) {
        GeneralJournal journal = new GeneralJournal();
        journal.setId(id);
        journal.setJournalNumber(number);
        journal.setReference(reference);
        journal.setJournalDate(date);
        journal.setStatus(JournalStatus.POSTED);
        return journal;
    }

    private JournalEntry entry(GeneralJournal journal, ChartOfAccount account, String debit, String credit) {
        JournalEntry entry = new JournalEntry();
        entry.setJournal(journal);
        entry.setAccount(account);
        entry.setDebit(new BigDecimal(debit));
        entry.setCredit(new BigDecimal(credit));
        return entry;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }
}
