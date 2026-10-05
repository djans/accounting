package com.cogitosum.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyActivityResetService {

    private final JdbcTemplate jdbcTemplate;
    private final CurrentCompanyContext companyContext;

    public CompanyActivityResetService(JdbcTemplate jdbcTemplate, CurrentCompanyContext companyContext) {
        this.jdbcTemplate = jdbcTemplate;
        this.companyContext = companyContext;
    }

    @Transactional(readOnly = true)
    public ActivityCounts getCurrentCompanyCounts() {
        return counts(companyContext.requireCompanyId());
    }

    @Transactional
    public ActivityCounts resetCurrentCompany() {
        Long companyId = companyContext.requireCompanyId();
        ActivityCounts deleted = counts(companyId);

        jdbcTemplate.update("UPDATE written_cheques SET reissue_of_id = NULL WHERE company_id = ?", companyId);
        jdbcTemplate.update("UPDATE credit_card_charges SET reissue_of_id = NULL WHERE company_id = ?", companyId);
        jdbcTemplate.update("DELETE FROM payments WHERE company_id = ?", companyId);
        jdbcTemplate.update("DELETE FROM bill_payments WHERE company_id = ?", companyId);
        jdbcTemplate.update("DELETE FROM invoices WHERE company_id = ?", companyId);
        jdbcTemplate.update("DELETE FROM bills WHERE company_id = ?", companyId);
        jdbcTemplate.update("""
                DELETE FROM cheque_expenses
                WHERE cheque_id IN (SELECT id FROM written_cheques WHERE company_id = ?)
                """, companyId);
        jdbcTemplate.update("DELETE FROM written_cheques WHERE company_id = ?", companyId);
        jdbcTemplate.update("DELETE FROM credit_card_charges WHERE company_id = ?", companyId);
        jdbcTemplate.update("DELETE FROM transfers WHERE company_id = ?", companyId);
        jdbcTemplate.update("DELETE FROM tax_filing_periods WHERE company_id = ?", companyId);
        jdbcTemplate.update("DELETE FROM fiscal_years WHERE company_id = ?", companyId);
        clearBankReconciliationData(companyId);
        jdbcTemplate.update("DELETE FROM general_journals WHERE company_id = ?", companyId);
        jdbcTemplate.update("""
                UPDATE general_ledger
                SET debit_balance = 0, credit_balance = 0, balance = 0, last_updated = CURRENT_TIMESTAMP
                WHERE company_id = ?
                """, companyId);

        return deleted;
    }

    @Transactional
    public BankReconciliationCounts resetBankReconciliationForCurrentCompany() {
        Long companyId = companyContext.requireCompanyId();
        BankReconciliationCounts deleted = new BankReconciliationCounts(
                count("bank_transactions", companyId),
                count("bank_reconciliation_sessions", companyId));
        clearBankReconciliationData(companyId);
        return deleted;
    }

    private void clearBankReconciliationData(Long companyId) {
        jdbcTemplate.update("""
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
                """, companyId);
        jdbcTemplate.update("DELETE FROM bank_transactions WHERE company_id = ?", companyId);
        jdbcTemplate.update("DELETE FROM bank_reconciliation_sessions WHERE company_id = ?", companyId);
    }

    private ActivityCounts counts(Long companyId) {
        return new ActivityCounts(
                count("invoices", companyId),
                count("payments", companyId),
                count("bills", companyId),
                count("bill_payments", companyId),
                count("written_cheques", companyId),
                count("credit_card_charges", companyId),
                count("transfers", companyId),
                count("general_journals", companyId),
                count("tax_filing_periods", companyId),
                count("fiscal_years", companyId),
                count("bank_transactions", companyId),
                count("bank_reconciliation_sessions", companyId));
    }

    private long count(String table, Long companyId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE company_id = ?", Long.class, companyId);
    }

    public record ActivityCounts(
            long invoices,
            long customerPayments,
            long bills,
            long vendorPayments,
            long cheques,
            long creditCardCharges,
            long transfers,
            long journals,
            long taxFilingPeriods,
            long fiscalYears,
            long importedBankTransactions,
            long reconciliationSessions) {

        public long total() {
            return invoices + customerPayments + bills + vendorPayments + cheques
                    + creditCardCharges + transfers + journals + taxFilingPeriods + fiscalYears
                    + importedBankTransactions + reconciliationSessions;
        }

    }

    public record BankReconciliationCounts(long importedBankTransactions, long reconciliationSessions) {
    }
}
