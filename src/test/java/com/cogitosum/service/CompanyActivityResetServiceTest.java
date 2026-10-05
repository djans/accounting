package com.cogitosum.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.jdbc.core.JdbcTemplate;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyActivityResetServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private CurrentCompanyContext companyContext;

    private CompanyActivityResetService service;

    @BeforeEach
    void setUp() {
        service = new CompanyActivityResetService(jdbcTemplate, companyContext);
        when(companyContext.requireCompanyId()).thenReturn(27L);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq(27L))).thenReturn(0L);
        when(jdbcTemplate.update(anyString(), eq(27L))).thenReturn(0);
    }

    @Test
    void deletesOnlyCurrentCompanyActivityAndClearsAggregateLedger() {
        CompanyActivityResetService.ActivityCounts counts = service.resetCurrentCompany();

        assertEquals(0, counts.total());
        InOrder order = inOrder(jdbcTemplate);
        order.verify(jdbcTemplate).update("DELETE FROM payments WHERE company_id = ?", 27L);
        order.verify(jdbcTemplate).update("DELETE FROM bill_payments WHERE company_id = ?", 27L);
        order.verify(jdbcTemplate).update("DELETE FROM invoices WHERE company_id = ?", 27L);
        order.verify(jdbcTemplate).update("DELETE FROM bills WHERE company_id = ?", 27L);
        order.verify(jdbcTemplate).update("""
                DELETE FROM cheque_expenses
                WHERE cheque_id IN (SELECT id FROM written_cheques WHERE company_id = ?)
                """, 27L);
        order.verify(jdbcTemplate).update("DELETE FROM written_cheques WHERE company_id = ?", 27L);
        order.verify(jdbcTemplate).update("DELETE FROM credit_card_charges WHERE company_id = ?", 27L);
        order.verify(jdbcTemplate).update("DELETE FROM transfers WHERE company_id = ?", 27L);
        order.verify(jdbcTemplate).update("DELETE FROM tax_filing_periods WHERE company_id = ?", 27L);
        order.verify(jdbcTemplate).update("DELETE FROM fiscal_years WHERE company_id = ?", 27L);
        order.verify(jdbcTemplate).update("""
                UPDATE chart_of_accounts account
                JOIN bank_reconciliation_sessions session
                    ON session.company_id = account.company_id
                    AND session.bank_account_id = account.id
                LEFT JOIN bank_reconciliation_sessions later
                    ON later.company_id = session.company_id
                    AND later.bank_account_id = session.bank_account_id
                    AND (later.statement_date > session.statement_date
                        OR (later.statement_date = session.statement_date AND later.id > session.id))
                SET account.opening_balance = session.ending_balance,
                    account.opening_balance_date = session.statement_date
                WHERE account.company_id = ? AND later.id IS NULL
                """, 27L);
        order.verify(jdbcTemplate).update("DELETE FROM bank_transactions WHERE company_id = ?", 27L);
        order.verify(jdbcTemplate).update("DELETE FROM bank_reconciliation_sessions WHERE company_id = ?", 27L);
        order.verify(jdbcTemplate).update("DELETE FROM general_journals WHERE company_id = ?", 27L);
        order.verify(jdbcTemplate).update("""
                UPDATE general_ledger
                SET debit_balance = 0, credit_balance = 0, balance = 0, last_updated = CURRENT_TIMESTAMP
                WHERE company_id = ?
                """, 27L);
    }

    @Test
    void resetsExistingBankReconciliationsAndCarriesForwardTheLatestBalance() {
        CompanyActivityResetService.BankReconciliationCounts counts =
                service.resetBankReconciliationForCurrentCompany();

        assertEquals(0, counts.importedBankTransactions() + counts.reconciliationSessions());
        InOrder order = inOrder(jdbcTemplate);
        order.verify(jdbcTemplate).update("""
                UPDATE chart_of_accounts account
                JOIN bank_reconciliation_sessions session
                    ON session.company_id = account.company_id
                    AND session.bank_account_id = account.id
                LEFT JOIN bank_reconciliation_sessions later
                    ON later.company_id = session.company_id
                    AND later.bank_account_id = session.bank_account_id
                    AND (later.statement_date > session.statement_date
                        OR (later.statement_date = session.statement_date AND later.id > session.id))
                SET account.opening_balance = session.ending_balance,
                    account.opening_balance_date = session.statement_date
                WHERE account.company_id = ? AND later.id IS NULL
                """, 27L);
        order.verify(jdbcTemplate).update("DELETE FROM bank_transactions WHERE company_id = ?", 27L);
        order.verify(jdbcTemplate).update("DELETE FROM bank_reconciliation_sessions WHERE company_id = ?", 27L);
    }
}
