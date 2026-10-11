package com.cogitosum.repository.mysql;

import com.cogitosum.repository.jdbc.JdbcCompanyActivityRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.database.type", havingValue = "mysql", matchIfMissing = true)
public class MysqlCompanyActivityRepository extends JdbcCompanyActivityRepository {

    public MysqlCompanyActivityRepository(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    @Override
    public void resetBankReconciliation(Long companyId) {
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
}
