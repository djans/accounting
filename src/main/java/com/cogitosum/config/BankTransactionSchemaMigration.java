package com.cogitosum.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.Date;
import java.util.List;

@Component
@Order(0)
public class BankTransactionSchemaMigration implements CommandLineRunner {

    private static final List<ColumnDefinition> COLUMNS = List.of(
            new ColumnDefinition("company_id", "BIGINT NULL"),
            new ColumnDefinition("reconciled", "BOOLEAN NOT NULL DEFAULT FALSE"),
            new ColumnDefinition("reconciliation_session_id", "BIGINT NULL"),
            new ColumnDefinition("reference", "VARCHAR(500) NULL"),
            new ColumnDefinition("source_hash", "VARCHAR(64) NULL"),
            new ColumnDefinition("source_row_hash", "VARCHAR(64) NULL")
    );

    private final JdbcTemplate jdbcTemplate;

    public BankTransactionSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) throws Exception {
        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            if (!connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql")) {
                return;
            }
        }
        if (!tableExists()) {
            return;
        }

        for (ColumnDefinition column : COLUMNS) {
            if (!columnExists(column.name())) {
                jdbcTemplate.execute("ALTER TABLE `bank_transactions` ADD COLUMN `"
                        + column.name() + "` " + column.definition());
            }
        }

        backfillHashes();
        jdbcTemplate.execute("ALTER TABLE `bank_transactions` "
                + "MODIFY COLUMN `source_hash` VARCHAR(64) NOT NULL, "
                + "MODIFY COLUMN `source_row_hash` VARCHAR(64) NOT NULL");

        ensureIndex("idx_bank_transactions_company_account_status",
                "(`company_id`, `bank_account_id`, `reconciled`)");
        ensureIndex("idx_bank_transactions_source_hash",
                "(`company_id`, `bank_account_id`, `source_hash`)");
        ensureIndex("idx_bank_transactions_session", "(`reconciliation_session_id`)");
        ensureForeignKey("reconciliation_session_id", "fk_bank_transactions_session",
                "bank_reconciliation_sessions");
    }

    private void backfillHashes() {
        List<LegacyHashRow> rows = jdbcTemplate.query(
                "SELECT id, transaction_date, description, amount, reference, source_hash, source_row_hash "
                        + "FROM bank_transactions "
                        + "WHERE source_hash IS NULL OR source_hash = '' "
                        + "OR source_row_hash IS NULL OR source_row_hash = ''",
                (result, rowNumber) -> new LegacyHashRow(
                        result.getLong("id"),
                        result.getDate("transaction_date"),
                        result.getString("description"),
                        result.getBigDecimal("amount"),
                        result.getString("reference"),
                        result.getString("source_hash"),
                        result.getString("source_row_hash")));

        for (LegacyHashRow row : rows) {
            if (row.transactionDate() == null || row.description() == null || row.amount() == null) {
                throw new IllegalStateException(
                        "Cannot backfill hashes for bank transaction " + row.id() + " with incomplete data");
            }
            String canonicalRow = canonicalRow(row);
            String sourceHash = isBlank(row.sourceHash())
                    ? sha256("legacy:" + row.id() + ":" + canonicalRow)
                    : row.sourceHash();
            String sourceRowHash = isBlank(row.sourceRowHash())
                    ? sha256(canonicalRow)
                    : row.sourceRowHash();
            jdbcTemplate.update(
                    "UPDATE `bank_transactions` SET `source_hash` = ?, `source_row_hash` = ? WHERE `id` = ?",
                    sourceHash, sourceRowHash, row.id());
        }
    }

    private String canonicalRow(LegacyHashRow row) {
        String reference = row.reference();
        return row.transactionDate().toLocalDate() + "|" + row.description().length() + ":"
                + row.description() + "|" + row.amount().toPlainString() + "|"
                + (reference == null ? -1 : reference.length()) + ":" + (reference == null ? "" : reference);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                hex.append(String.format("%02x", item));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private boolean tableExists() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_name = 'bank_transactions'",
                Integer.class);
        return count != null && count > 0;
    }

    private boolean columnExists(String column) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'bank_transactions' AND column_name = ?",
                Integer.class, column);
        return count != null && count > 0;
    }

    private boolean indexExists(String index) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = 'bank_transactions' AND index_name = ?",
                Integer.class, index);
        return count != null && count > 0;
    }

    private void ensureIndex(String index, String columns) {
        if (!indexExists(index)) {
            jdbcTemplate.execute("ALTER TABLE `bank_transactions` ADD KEY `" + index + "` " + columns);
        }
    }

    private void ensureForeignKey(String column, String constraint, String targetTable) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.key_column_usage "
                        + "WHERE table_schema = DATABASE() AND table_name = 'bank_transactions' "
                        + "AND column_name = ? AND referenced_table_name = ?",
                Integer.class, column, targetTable);
        if (count == null || count == 0) {
            jdbcTemplate.execute("ALTER TABLE `bank_transactions` ADD CONSTRAINT `" + constraint
                    + "` FOREIGN KEY (`" + column + "`) REFERENCES `" + targetTable + "` (`id`)");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record ColumnDefinition(String name, String definition) {
    }

    private record LegacyHashRow(long id, Date transactionDate, String description,
                                 java.math.BigDecimal amount, String reference,
                                 String sourceHash, String sourceRowHash) {
    }
}
