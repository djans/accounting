package com.cogitosum.repository.jdbc;

import com.cogitosum.repository.CompanyActivityRepository;
import org.springframework.jdbc.core.JdbcTemplate;

public abstract class JdbcCompanyActivityRepository implements CompanyActivityRepository {

    protected final JdbcTemplate jdbcTemplate;

    protected JdbcCompanyActivityRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ActivityCounts findActivityCounts(Long companyId) {
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

    @Override
    public BankReconciliationCounts findBankReconciliationCounts(Long companyId) {
        return new BankReconciliationCounts(
                count("bank_transactions", companyId),
                count("bank_reconciliation_sessions", companyId));
    }

    @Override
    public void resetActivity(Long companyId) {
        // Keep shared statements portable; bank reconciliation reset SQL is supplied by each engine adapter.
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
        resetBankReconciliation(companyId);
        jdbcTemplate.update("DELETE FROM general_journals WHERE company_id = ?", companyId);
        jdbcTemplate.update("""
                UPDATE general_ledger
                SET debit_balance = 0, credit_balance = 0, balance = 0, last_updated = CURRENT_TIMESTAMP
                WHERE company_id = ?
                """, companyId);
    }

    protected long count(String table, Long companyId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE company_id = ?", Long.class, companyId);
    }
}
