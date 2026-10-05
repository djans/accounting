package com.cogitosum.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Connection;

@Component
@Order(1)
public class BankReconciliationReportSchemaMigration implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public BankReconciliationReportSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) throws Exception {
        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            if (!connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql")) {
                return;
            }
        }
        if (tableExists() && !columnExists("register_balance")) {
            jdbcTemplate.execute("ALTER TABLE `bank_reconciliation_sessions` "
                    + "ADD COLUMN `register_balance` DECIMAL(19,2) NULL");
        }
        if (tableExists() && !columnExists("report_lines_captured")) {
            jdbcTemplate.execute("ALTER TABLE `bank_reconciliation_sessions` "
                    + "ADD COLUMN `report_lines_captured` BOOLEAN NOT NULL DEFAULT FALSE");
        }
    }

    private boolean tableExists() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_name = 'bank_reconciliation_sessions'",
                Integer.class);
        return count != null && count > 0;
    }

    private boolean columnExists(String column) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'bank_reconciliation_sessions' "
                        + "AND column_name = ?",
                Integer.class, column);
        return count != null && count > 0;
    }
}
