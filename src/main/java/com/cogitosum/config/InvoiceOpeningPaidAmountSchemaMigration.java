package com.cogitosum.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Connection;

@Component
@Order(0)
public class InvoiceOpeningPaidAmountSchemaMigration implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public InvoiceOpeningPaidAmountSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) throws Exception {
        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            if (!connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql")) {
                return;
            }
        }

        Integer columnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'invoices'
                  AND COLUMN_NAME = 'opening_paid_amount'
                """, Integer.class);
        if (columnCount != null && columnCount == 0) {
            jdbcTemplate.execute("""
                    ALTER TABLE invoices
                    ADD COLUMN opening_paid_amount DECIMAL(19, 2) NOT NULL DEFAULT 0
                    """);
            jdbcTemplate.execute("""
                    UPDATE invoices i
                    SET opening_paid_amount = GREATEST(
                        COALESCE(i.paid_amount, 0) - COALESCE((
                            SELECT SUM(p.amount)
                            FROM payments p
                            WHERE p.invoice_id = i.id
                              AND p.company_id = i.company_id
                              AND p.status IN ('PENDING', 'COMPLETED')
                        ), 0),
                        0
                    )
                    """);
        }
    }
}
