package com.cogitosum.service;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseSchemaVersionServiceTest {

    private DatabaseSchemaVersionService service;
    private JdbcDataSource dataSource;

    @BeforeEach
    void setUp() throws java.sql.SQLException {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:schema-version-test-" + java.util.UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE invoices (id BIGINT PRIMARY KEY)");
            statement.execute("CREATE TABLE bills (id BIGINT PRIMARY KEY)");
            statement.execute("CREATE TABLE companies (id BIGINT PRIMARY KEY)");
            statement.execute("CREATE TABLE chart_of_accounts (id BIGINT PRIMARY KEY)");
            statement.execute("CREATE TABLE bank_transactions (id BIGINT PRIMARY KEY, company_id BIGINT)");
            statement.execute("CREATE TABLE app_users (id BIGINT PRIMARY KEY, role VARCHAR(32) NOT NULL)");
            statement.execute("CREATE TABLE user_company_memberships ("
                + "user_id BIGINT NOT NULL, company_id BIGINT NOT NULL, PRIMARY KEY (user_id, company_id))");
        }
        service = new DatabaseSchemaVersionService(dataSource);
    }

    @Test
    void missingLedgerUsesBaselineAndListsAllKnownVersions() {
        DatabaseSchemaVersionService.Status status = service.getStatus();

        assertEquals("1.0.0", status.currentVersion());
        assertEquals("1.10.0", status.latestVersion());
        assertTrue(status.updateAvailable());
        assertEquals(11, status.versions().size());
        assertEquals("1.1.0", status.pendingMigrations().get(0).version().toString());
    }

    @Test
    void appliesEveryPendingMigrationAndAdvancesLedger() {
        DatabaseSchemaVersionService.Status status = service.applyPendingMigrations();

        assertEquals("1.10.0", status.currentVersion());
        assertTrue(status.upToDate());
        assertFalse(status.updateAvailable());
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.executeQuery("SELECT tax_regime FROM invoices");
            statement.executeQuery("SELECT cancellation_reason, cancelled_at FROM invoices");
            statement.executeQuery("SELECT cancellation_reason, cancelled_at FROM bills");
            statement.executeQuery("SELECT logo_data, logo_content_type FROM companies");
            statement.executeQuery("SELECT id, company_id, template_data FROM invoice_templates");
            statement.executeQuery("SELECT invoice_id, pdf_data, created_at FROM invoice_final_pdfs");
            statement.executeQuery("SELECT fit_id, suggested_category_id, categorization_rule_id, category_reviewed "
                    + "FROM bank_transactions");
            statement.executeQuery("SELECT match_text FROM bank_categorization_rules");
            statement.executeQuery("SELECT credit_note_number, invoice_id, status FROM credit_notes");
            statement.executeQuery("SELECT description, quantity, unit_price FROM credit_note_line_items");
            statement.executeQuery("SELECT amount, refund_date, bank_account_id FROM credit_note_refunds");
            statement.executeQuery("SELECT amount, allocation_date, reversal_date, reversal_reason, "
                    + "target_invoice_id FROM credit_note_allocations");
        } catch (java.sql.SQLException exception) {
            throw new AssertionError(exception);
        }
    }

    @Test
    void ensuresCompanyColumnsAreAvailableBeforeAuthenticationWithoutAdvancingTheLedger() throws Exception {
        service.ensureCompanyProfileColumnsBeforeAuthentication();

        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.executeQuery("SELECT logo_data, logo_content_type FROM companies");
            statement.executeQuery("SELECT cancellation_reason, cancelled_at FROM invoices");
            statement.executeQuery("SELECT cancellation_reason, cancelled_at FROM bills");
        }
        assertEquals("1.0.0", service.getStatus().currentVersion());
    }

    @Test
    void recognizesInvoiceTaxRegimeAlreadyPresentInFreshSchema() throws java.sql.SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("ALTER TABLE invoices ADD tax_regime VARCHAR(30)");
            statement.execute("ALTER TABLE app_users ADD short_id VARCHAR(7)");
            statement.execute("ALTER TABLE user_company_memberships "
                    + "ADD role VARCHAR(32) NOT NULL DEFAULT 'AUDITOR'");
            statement.execute("CREATE UNIQUE INDEX uk_app_users_short_id ON app_users (short_id)");
        }

        DatabaseSchemaVersionService.Status status = service.applyPendingMigrations();

        assertEquals("1.10.0", status.currentVersion());
        assertTrue(status.upToDate());
    }

    @Test
    void appliesInvoiceTaxRegimeMigrationOnSqlite() throws Exception {
        Path database = Files.createTempFile("schema-version-test-", ".sqlite");
        try {
            SQLiteDataSource sqliteDataSource = new SQLiteDataSource();
            sqliteDataSource.setUrl("jdbc:sqlite:" + database);
            try (var connection = sqliteDataSource.getConnection();
                 var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE invoices (id INTEGER PRIMARY KEY)");
                statement.execute("CREATE TABLE bills (id INTEGER PRIMARY KEY)");
                statement.execute("CREATE TABLE companies (id INTEGER PRIMARY KEY)");
                statement.execute("CREATE TABLE chart_of_accounts (id INTEGER PRIMARY KEY)");
                statement.execute("CREATE TABLE bank_transactions (id INTEGER PRIMARY KEY, company_id BIGINT)");
                statement.execute("CREATE TABLE app_users (id INTEGER PRIMARY KEY, role VARCHAR(32) NOT NULL)");
                statement.execute("CREATE TABLE user_company_memberships ("
                    + "user_id INTEGER NOT NULL, company_id INTEGER NOT NULL, PRIMARY KEY (user_id, company_id))");
            }

            DatabaseSchemaVersionService sqliteService = new DatabaseSchemaVersionService(sqliteDataSource);
            DatabaseSchemaVersionService.Status status = sqliteService.applyPendingMigrations();

            assertEquals("1.10.0", status.currentVersion());
            assertTrue(status.upToDate());
            try (var connection = sqliteDataSource.getConnection();
                 var statement = connection.createStatement()) {
                statement.executeQuery("SELECT tax_regime FROM invoices");
                statement.executeQuery("SELECT cancellation_reason, cancelled_at FROM invoices");
                statement.executeQuery("SELECT cancellation_reason, cancelled_at FROM bills");
                statement.executeQuery("SELECT logo_data, logo_content_type FROM companies");
                statement.executeQuery("SELECT id, company_id, template_data FROM invoice_templates");
                statement.executeQuery("SELECT invoice_id, pdf_data, created_at FROM invoice_final_pdfs");
                statement.executeQuery("SELECT fit_id, suggested_category_id, categorization_rule_id, category_reviewed "
                        + "FROM bank_transactions");
                statement.executeQuery("SELECT credit_note_number, invoice_id, status FROM credit_notes");
                statement.executeQuery("SELECT description, quantity, unit_price FROM credit_note_line_items");
            }
        } finally {
            Files.deleteIfExists(database);
        }
    }

    @Test
    void migratesLegacyAccountRolesIntoEachCompanyMembership() throws java.sql.SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("INSERT INTO app_users (id, role) VALUES (1, 'ADMIN'), (2, 'VIEWER')");
            statement.execute("INSERT INTO user_company_memberships (user_id, company_id) "
                + "VALUES (1, 10), (2, 10)");
        }

        DatabaseSchemaVersionService.Status status = service.applyPendingMigrations();

        assertEquals("1.10.0", status.currentVersion());
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var results = statement.executeQuery(
                 "SELECT role FROM user_company_memberships ORDER BY user_id")) {
            results.next();
            assertEquals("ADMIN", results.getString(1));
            results.next();
            assertEquals("AUDITOR", results.getString(1));
        }
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.executeQuery("SELECT short_id FROM app_users");
        }
    }

    @Test
    void completesAPartiallyAppliedCompanyRoleMigration() throws java.sql.SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("ALTER TABLE user_company_memberships "
                    + "ADD role VARCHAR(32) NOT NULL DEFAULT 'AUDITOR'");
            statement.execute("INSERT INTO app_users (id, role) VALUES (1, 'ADMIN')");
            statement.execute("INSERT INTO user_company_memberships (user_id, company_id) VALUES (1, 10)");
        }

        DatabaseSchemaVersionService.Status status = service.applyPendingMigrations();

        assertEquals("1.10.0", status.currentVersion());
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var results = statement.executeQuery(
                 "SELECT role FROM user_company_memberships WHERE user_id = 1")) {
            results.next();
            assertEquals("ADMIN", results.getString(1));
        }
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.executeQuery("SELECT short_id FROM app_users");
        }
    }

    @Test
    void blocksVersionsOutsideTheKnownMigrationHistory() {
        service.applyPendingMigrations();
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE schema_version_history SET version = '2.0.0'");
        } catch (java.sql.SQLException exception) {
            throw new AssertionError(exception);
        }

        DatabaseSchemaVersionService.Status status = service.getStatus();

        assertFalse(status.migrationPossible());
        assertFalse(status.upToDate());
        assertTrue(status.issue().contains("not in this application's migration history"));
        assertTrue(status.versions().stream().anyMatch(version -> version.version().equals("2.0.0")));
    }
}
