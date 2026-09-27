package com.cogitosum.config;

import com.cogitosum.entity.Company;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.DatabaseMetaData;
import java.sql.Connection;
import java.util.List;

/**
 * Backfills legacy single-company rows before making the tenant columns
 * mandatory.  It never drops a table or deletes a row.
 */
@Component
@Order(1)
public class CompanyOwnershipMigration implements CommandLineRunner {

    private static final List<String> ROOT_TABLES = List.of(
        "app_users", "customers", "vendors", "chart_of_accounts", "invoices", "bills",
        "payments", "bill_payments", "general_journals", "general_ledger",
        "tax_agencies", "tax_items", "tax_groups", "tax_codes",
        "tax_filing_periods", "fiscal_years", "written_cheques",
        "credit_card_charges", "transfers"
    );

    private static final List<UniqueRule> UNIQUE_RULES = List.of(
        new UniqueRule("customers", "email", "uk_customers_company_email"),
        new UniqueRule("vendors", "email", "uk_vendors_company_email"),
        new UniqueRule("chart_of_accounts", "account_number", "uk_chart_of_accounts_company_number"),
        new UniqueRule("invoices", "invoice_number", "uk_invoices_company_number"),
        new UniqueRule("bills", "bill_number", "uk_bills_company_number"),
        new UniqueRule("general_journals", "journal_number", "uk_general_journals_company_number"),
        new UniqueRule("tax_agencies", "code", "uk_tax_agencies_company_code"),
        new UniqueRule("tax_items", "code", "uk_tax_items_company_code"),
        new UniqueRule("tax_groups", "code", "uk_tax_groups_company_code"),
        new UniqueRule("tax_codes", "code", "uk_tax_codes_company_code"),
        new UniqueRule("fiscal_years", "label", "uk_fiscal_years_company_label"),
        new UniqueRule("written_cheques", "cheque_number", "uk_written_cheques_company_number")
    );

    private final JdbcTemplate jdbcTemplate;
    private final BootstrapCompanyProvider bootstrapCompanyProvider;

    public CompanyOwnershipMigration(JdbcTemplate jdbcTemplate,
                                     BootstrapCompanyProvider bootstrapCompanyProvider) {
        this.jdbcTemplate = jdbcTemplate;
        this.bootstrapCompanyProvider = bootstrapCompanyProvider;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            if (!metadata.getDatabaseProductName().toLowerCase().contains("mysql")) {
                return;
            }
        }
        migrateLegacyRows();
    }

    private void migrateLegacyRows() {
        Company bootstrapCompany = null;

        for (String table : ROOT_TABLES) {
            if (!tableExists(table) || !columnExists(table, "company_id")) {
                continue;
            }
            if (hasRowsWithoutCompany(table)) {
                if (bootstrapCompany == null) {
                    bootstrapCompany = bootstrapCompanyProvider.requireBootstrapCompany();
                }
                jdbcTemplate.update("UPDATE `" + table + "` SET company_id = ? WHERE company_id IS NULL",
                        bootstrapCompany.getId());
            }
            jdbcTemplate.execute("ALTER TABLE `" + table + "` MODIFY company_id BIGINT NOT NULL");
            ensureCompanyIndex(table);
            ensureCompanyForeignKey(table);
        }

        for (UniqueRule rule : UNIQUE_RULES) {
            if (tableExists(rule.table) && columnExists(rule.table, rule.column)) {
                replaceLegacyGlobalUnique(rule);
            }
        }
    }

    private boolean hasRowsWithoutCompany(String table) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM `" + table + "` WHERE company_id IS NULL", Integer.class);
        return count != null && count > 0;
    }

    private boolean tableExists(String table) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
            Integer.class, table);
        return count != null && count > 0;
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
            Integer.class, table, column);
        return count != null && count > 0;
    }

    private void ensureCompanyIndex(String table) {
        String indexName = "idx_" + table + "_company";
        if (!indexExists(table, indexName)) {
            jdbcTemplate.execute("ALTER TABLE `" + table + "` ADD KEY `" + indexName + "` (company_id)");
        }
    }

    private void ensureCompanyForeignKey(String table) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.key_column_usage "
                + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = 'company_id' "
                + "AND referenced_table_name = 'companies'",
            Integer.class, table);
        if (count == null || count == 0) {
            jdbcTemplate.execute("ALTER TABLE `" + table + "` ADD CONSTRAINT `fk_" + table
                + "_company` FOREIGN KEY (company_id) REFERENCES companies (id)");
        }
    }

    private void replaceLegacyGlobalUnique(UniqueRule rule) {
        List<String> globalIndexes = jdbcTemplate.queryForList(
            "SELECT s.index_name FROM information_schema.statistics s "
                + "WHERE s.table_schema = DATABASE() AND s.table_name = ? AND s.non_unique = 0 "
                + "AND s.column_name = ? AND s.index_name <> 'PRIMARY' "
                + "AND (SELECT COUNT(*) FROM information_schema.statistics all_columns "
                + "     WHERE all_columns.table_schema = s.table_schema "
                + "       AND all_columns.table_name = s.table_name "
                + "       AND all_columns.index_name = s.index_name) = 1",
            String.class, rule.table, rule.column);
        for (String index : globalIndexes) {
            jdbcTemplate.execute("ALTER TABLE `" + rule.table + "` DROP INDEX `" + index + "`");
        }
        if (!indexExists(rule.table, rule.indexName)) {
            jdbcTemplate.execute("ALTER TABLE `" + rule.table + "` ADD UNIQUE KEY `" + rule.indexName
                + "` (company_id, `" + rule.column + "`)");
        }
    }

    private boolean indexExists(String table, String indexName) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.statistics "
                + "WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?",
            Integer.class, table, indexName);
        return count != null && count > 0;
    }

    private record UniqueRule(String table, String column, String indexName) {
    }
}
