package com.cogitosum.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.util.List;

@Component
@Order(0)
public class JournalEntryMetadataMigration implements CommandLineRunner {

    private static final List<ReferenceColumn> REFERENCE_COLUMNS = List.of(
            new ReferenceColumn("customer_id", "idx_journal_entries_customer",
                    "fk_journal_entries_customer", "customers"),
            new ReferenceColumn("vendor_id", "idx_journal_entries_vendor",
                    "fk_journal_entries_vendor", "vendors"),
            new ReferenceColumn("tax_agency_id", "idx_journal_entries_tax_agency",
                    "fk_journal_entries_tax_agency", "tax_agencies"),
            new ReferenceColumn("tax_item_id", "idx_journal_entries_tax_item",
                    "fk_journal_entries_tax_item", "tax_items")
    );

    private final JdbcTemplate jdbcTemplate;

    public JournalEntryMetadataMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) throws Exception {
        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            if (!connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql")) {
                return;
            }
        }
        if (!tableExists("journal_entries")) {
            return;
        }

        for (ReferenceColumn reference : REFERENCE_COLUMNS) {
            ensureColumn(reference.column);
            ensureIndex(reference);
            ensureForeignKey(reference);
        }
    }

    private void ensureColumn(String column) {
        if (!columnExists(column)) {
            jdbcTemplate.execute("ALTER TABLE `journal_entries` ADD COLUMN `" + column + "` BIGINT NULL");
        }
    }

    private void ensureIndex(ReferenceColumn reference) {
        if (!indexExists(reference.indexName)) {
            jdbcTemplate.execute("ALTER TABLE `journal_entries` ADD KEY `" + reference.indexName
                    + "` (`" + reference.column + "`)");
        }
    }

    private void ensureForeignKey(ReferenceColumn reference) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.key_column_usage "
                        + "WHERE table_schema = DATABASE() AND table_name = 'journal_entries' "
                        + "AND column_name = ? AND referenced_table_name = ?",
                Integer.class, reference.column, reference.targetTable);
        if (count == null || count == 0) {
            jdbcTemplate.execute("ALTER TABLE `journal_entries` ADD CONSTRAINT `" + reference.foreignKey
                    + "` FOREIGN KEY (`" + reference.column + "`) REFERENCES `" + reference.targetTable + "` (`id`)");
        }
    }

    private boolean tableExists(String table) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_name = ?",
                Integer.class, table);
        return count != null && count > 0;
    }

    private boolean columnExists(String column) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'journal_entries' AND column_name = ?",
                Integer.class, column);
        return count != null && count > 0;
    }

    private boolean indexExists(String index) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = 'journal_entries' AND index_name = ?",
                Integer.class, index);
        return count != null && count > 0;
    }

    private record ReferenceColumn(String column, String indexName, String foreignKey, String targetTable) {
    }
}
