package com.cogitosum.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Connection;

@Component
@Order(0)
public class CompanyPostedJournalEditingMigration implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public CompanyPostedJournalEditingMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) throws Exception {
        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            if (!connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql")) {
                return;
            }
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'companies' "
                        + "AND column_name = 'posted_journal_editing_enabled'",
                Integer.class);
        if (count == null || count == 0) {
            jdbcTemplate.execute("ALTER TABLE `companies` "
                    + "ADD COLUMN `posted_journal_editing_enabled` BOOLEAN NOT NULL DEFAULT FALSE");
        }
    }
}
