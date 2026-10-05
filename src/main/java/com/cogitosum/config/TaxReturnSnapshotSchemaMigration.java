package com.cogitosum.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Connection;

@Component
@Order(1)
public class TaxReturnSnapshotSchemaMigration implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public TaxReturnSnapshotSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) throws Exception {
        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            if (!connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql")) {
                return;
            }
        }
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS tax_return_row_snapshots (
                    id BIGINT NOT NULL AUTO_INCREMENT,
                    period_id BIGINT NOT NULL,
                    display_order INT NOT NULL,
                    description_key VARCHAR(200) NOT NULL,
                    line VARCHAR(20) NULL,
                    amount DECIMAL(19,2) NULL,
                    balance DECIMAL(19,2) NULL,
                    total BOOLEAN NOT NULL,
                    unmapped BOOLEAN NOT NULL,
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_tax_return_snapshot_period_order (period_id, display_order),
                    CONSTRAINT fk_tax_return_snapshot_period FOREIGN KEY (period_id)
                        REFERENCES tax_filing_periods (id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }
}
