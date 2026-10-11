package com.cogitosum.repository.sqlite;

import com.cogitosum.repository.jdbc.JdbcCompanyActivityRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.database.type", havingValue = "sqlite")
public class SqliteCompanyActivityRepository extends JdbcCompanyActivityRepository {

    public SqliteCompanyActivityRepository(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    @Override
    public void resetBankReconciliation(Long companyId) {
        jdbcTemplate.update("""
                UPDATE chart_of_accounts
                SET opening_balance = (
                        SELECT session.ending_balance
                        FROM bank_reconciliation_sessions session
                        WHERE session.company_id = chart_of_accounts.company_id
                          AND session.bank_account_id = chart_of_accounts.id
                        ORDER BY session.statement_date DESC, session.id DESC
                        LIMIT 1
                    ),
                    opening_balance_date = (
                        SELECT session.statement_date
                        FROM bank_reconciliation_sessions session
                        WHERE session.company_id = chart_of_accounts.company_id
                          AND session.bank_account_id = chart_of_accounts.id
                        ORDER BY session.statement_date DESC, session.id DESC
                        LIMIT 1
                    )
                WHERE company_id = ?
                  AND EXISTS (
                        SELECT 1 FROM bank_reconciliation_sessions session
                        WHERE session.company_id = chart_of_accounts.company_id
                          AND session.bank_account_id = chart_of_accounts.id
                    )
                """, companyId);
        jdbcTemplate.update("DELETE FROM bank_transactions WHERE company_id = ?", companyId);
        jdbcTemplate.update("DELETE FROM bank_reconciliation_sessions WHERE company_id = ?", companyId);
    }
}
