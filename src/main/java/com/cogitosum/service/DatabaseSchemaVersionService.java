package com.cogitosum.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class DatabaseSchemaVersionService {

    public static final SchemaVersion BASELINE_VERSION = new SchemaVersion(1, 0, 0);
    public static final String VERSION_TABLE = "schema_version_history";

    private static final List<SchemaMigration> MIGRATIONS = List.of(
            new SchemaMigration(
                    new SchemaVersion(1, 1, 0),
                    "database.schema.version.1.1.0.title",
                    "database.schema.version.1.1.0.description",
                    "db/migration/V1_1_0__schema_version_ledger.sql",
                    false),
            new SchemaMigration(
                    new SchemaVersion(1, 2, 0),
                    "database.schema.version.1.2.0.title",
                    "database.schema.version.1.2.0.description",
                    "db/migration/V1_2_0__invoice_tax_regime.sql",
                    false,
                    "invoices",
                    "tax_regime"),
            new SchemaMigration(
                    new SchemaVersion(1, 3, 0),
                    "database.schema.version.1.3.0.title",
                    "database.schema.version.1.3.0.description",
                    "db/migration/V1_3_0__company_scoped_roles_and_short_ids.sql",
                    false,
                    "user_company_memberships",
                    "role"),
            new SchemaMigration(
                    new SchemaVersion(1, 4, 0),
                    "database.schema.version.1.4.0.title",
                    "database.schema.version.1.4.0.description",
                    "db/migration/V1_4_0__bank_import_categorization.sql",
                    false),
            new SchemaMigration(
                    new SchemaVersion(1, 5, 0),
                    "database.schema.version.1.5.0.title",
                    "database.schema.version.1.5.0.description",
                    "db/migration/V1_5_0__company_logos_and_document_corrections.sql",
                    false),
            new SchemaMigration(
                    new SchemaVersion(1, 6, 0),
                    "database.schema.version.1.6.0.title",
                    "database.schema.version.1.6.0.description",
                    "db/migration/V1_6_0__company_invoice_templates.sql",
                    false),
            new SchemaMigration(
                    new SchemaVersion(1, 7, 0),
                    "database.schema.version.1.7.0.title",
                    "database.schema.version.1.7.0.description",
                    "db/migration/V1_7_0__invoice_final_pdfs.sql",
                    false),
            new SchemaMigration(
                    new SchemaVersion(1, 8, 0),
                    "database.schema.version.1.8.0.title",
                    "database.schema.version.1.8.0.description",
                    "db/migration/V1_8_0__credit_notes.sql",
                    false),
            new SchemaMigration(
                    new SchemaVersion(1, 9, 0),
                    "database.schema.version.1.9.0.title",
                    "database.schema.version.1.9.0.description",
                    "db/migration/V1_9_0__credit_note_refunds.sql",
                    false),
            new SchemaMigration(
                    new SchemaVersion(1, 10, 0),
                    "database.schema.version.1.10.0.title",
                    "database.schema.version.1.10.0.description",
                    "db/migration/V1_10_0__credit_note_allocations.sql",
                    false));

    private final DataSource dataSource;

    public DatabaseSchemaVersionService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Status getStatus() {
        try (Connection connection = dataSource.getConnection()) {
            return getStatus(connection);
        } catch (SQLException exception) {
            throw new DataAccessResourceFailureException(
                    "Could not determine the database schema version.", exception);
        }
    }

    public synchronized Status applyPendingMigrations() {
        try (Connection connection = dataSource.getConnection()) {
            return applyPendingMigrations(connection);
        } catch (SQLException exception) {
            throw new DataAccessResourceFailureException(
                    "Could not apply the database schema migrations.", exception);
        }
    }

    public synchronized Status applyPendingMigrations(Connection connection) {
        try {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(true);
            try {
                Status before = getStatus(connection);
                if (!before.migrationPossible()) {
                    throw new IllegalStateException(before.issue());
                }

                for (SchemaMigration migration : before.pendingMigrations()) {
                    if (migration.version().equals(new SchemaVersion(1, 3, 0))) {
                        applyCompanyRolesAndShortIds(connection);
                    } else if (migration.version().equals(new SchemaVersion(1, 4, 0))) {
                        applyBankImportCategorization(connection);
                    } else if (migration.version().equals(new SchemaVersion(1, 5, 0))) {
                        applyCompanyLogosAndDocumentCorrections(connection);
                    } else if (migration.version().equals(new SchemaVersion(1, 6, 0))) {
                        applyCompanyInvoiceTemplates(connection);
                    } else if (migration.version().equals(new SchemaVersion(1, 7, 0))) {
                        applyInvoiceFinalPdfs(connection);
                    } else if (migration.version().equals(new SchemaVersion(1, 8, 0))) {
                        applyCreditNotes(connection);
                    } else if (migration.version().equals(new SchemaVersion(1, 9, 0))) {
                        applyCreditNoteRefunds(connection);
                    } else if (migration.version().equals(new SchemaVersion(1, 10, 0))) {
                        applyCreditNoteAllocations(connection);
                    } else if (!migration.columnAlreadyExists() || !columnExists(
                            connection, migration.idempotencyTable(), migration.idempotencyColumn())) {
                        ClassPathResource script = new ClassPathResource(migration.resource());
                        new ResourceDatabasePopulator(script).populate(connection);
                    }
                    updateVersion(connection, migration.version());
                }
                return getStatus(connection);
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException exception) {
            throw new DataAccessResourceFailureException(
                    "Could not apply the database schema migrations.", exception);
        }
    }

    public void ensureCompanyProfileColumnsBeforeAuthentication() {
        try (Connection connection = dataSource.getConnection()) {
            applyCompanyLogosAndDocumentCorrections(connection);
        } catch (SQLException exception) {
            throw new DataAccessResourceFailureException(
                    "Could not ensure company profile columns are available for authentication.", exception);
        }
    }

    private Status getStatus(Connection connection) throws SQLException {
        String currentVersion = readCurrentVersion(connection);
        SchemaVersion parsedCurrent;
        try {
            parsedCurrent = SchemaVersion.parse(currentVersion);
        } catch (IllegalArgumentException exception) {
            return status(currentVersion, null, exception.getMessage());
        }

        boolean knownVersion = parsedCurrent.equals(BASELINE_VERSION)
                || MIGRATIONS.stream().anyMatch(migration -> migration.version().equals(parsedCurrent));
        if (!knownVersion) {
            return status(currentVersion, parsedCurrent,
                    "The database version is not in this application's migration history.");
        }

        SchemaVersion latest = latestVersion();
        if (parsedCurrent.compareTo(latest) > 0) {
            return status(currentVersion, parsedCurrent,
                    "The database schema is newer than this application supports.");
        }

        List<SchemaMigration> pending = MIGRATIONS.stream()
                .filter(migration -> migration.version().compareTo(parsedCurrent) > 0)
                .sorted(Comparator.comparing(SchemaMigration::version))
                .toList();
        for (SchemaMigration migration : pending) {
            if (!new ClassPathResource(migration.resource()).exists()) {
                return new Status(currentVersion, latest.toString(), versionHistory(currentVersion, parsedCurrent),
                        pending, false, false,
                        "A required migration script is missing: " + migration.resource());
            }
        }

        return new Status(currentVersion, latest.toString(), versionHistory(currentVersion, parsedCurrent),
                pending, true, pending.isEmpty(), null);
    }

    private Status status(String currentVersion, SchemaVersion parsedCurrent, String issue) {
        SchemaVersion latest = latestVersion();
        return new Status(currentVersion, latest.toString(), versionHistory(currentVersion, parsedCurrent),
                List.of(), false, false, issue);
    }

    private String readCurrentVersion(Connection connection) throws SQLException {
        if (!versionTableExists(connection)) {
            return BASELINE_VERSION.toString();
        }
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT version FROM " + VERSION_TABLE + " WHERE singleton_id = 1")) {
            return result.next() ? result.getString(1) : BASELINE_VERSION.toString();
        }
    }

    private boolean versionTableExists(Connection connection) throws SQLException {
        boolean sqlite = connection.getMetaData().getDatabaseProductName()
                .toLowerCase(Locale.ROOT).contains("sqlite");
        String catalog = sqlite ? null : connection.getCatalog();
        try (ResultSet tables = connection.getMetaData().getTables(catalog, null, "%",
                new String[]{"TABLE"})) {
            while (tables.next()) {
                if (VERSION_TABLE.equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void updateVersion(Connection connection, SchemaVersion version) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE " + VERSION_TABLE + " SET version = ?, applied_at = CURRENT_TIMESTAMP "
                        + "WHERE singleton_id = 1")) {
            statement.setString(1, version.toString());
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException(
                        "Migration " + version + " completed without a version ledger row.");
            }
        }
    }

    private boolean columnExists(Connection connection, String table, String column) throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(
                connection.getCatalog(), null, "%", "%")) {
            while (columns.next()) {
                if (table.equalsIgnoreCase(columns.getString("TABLE_NAME"))
                        && column.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void applyCompanyRolesAndShortIds(Connection connection) throws SQLException {
        boolean hasMembershipRole = columnExists(connection, "user_company_memberships", "role");
        boolean hasShortId = columnExists(connection, "app_users", "short_id");
        boolean hasShortIdIndex = hasShortIdIndex(connection);
        if (!hasMembershipRole) {
            execute(connection, "ALTER TABLE user_company_memberships "
                    + "ADD COLUMN role VARCHAR(32) NOT NULL DEFAULT 'AUDITOR'");
        }
        if (!hasMembershipRole || !hasShortId || !hasShortIdIndex) {
            execute(connection, "UPDATE user_company_memberships SET role = COALESCE(("
                    + "SELECT CASE WHEN app_users.role = 'VIEWER' THEN 'AUDITOR' ELSE app_users.role END "
                    + "FROM app_users WHERE app_users.id = user_company_memberships.user_id), 'AUDITOR')");
        }
        if (!hasShortId) {
            execute(connection, "ALTER TABLE app_users ADD COLUMN short_id VARCHAR(7)");
        }
        if (!hasShortIdIndex) {
            execute(connection, "CREATE UNIQUE INDEX uk_app_users_short_id ON app_users (short_id)");
        }
    }

    private void applyBankImportCategorization(Connection connection) throws SQLException {
        if (tableExists(connection, "bank_transactions")) {
            addColumnIfMissing(connection, "bank_transactions", "fit_id", "VARCHAR(255) NULL");
            addColumnIfMissing(connection, "bank_transactions", "suggested_category_id", "BIGINT NULL");
            addColumnIfMissing(connection, "bank_transactions", "categorization_rule_id", "BIGINT NULL");
            addColumnIfMissing(connection, "bank_transactions", "category_reviewed", "BOOLEAN NOT NULL DEFAULT FALSE");
        }
        boolean sqlite = connection.getMetaData().getDatabaseProductName()
                .toLowerCase(Locale.ROOT).contains("sqlite");
        if (!tableExists(connection, "bank_categorization_rules")) {
            String id = sqlite ? "id INTEGER PRIMARY KEY AUTOINCREMENT"
                    : "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY";
            execute(connection, "CREATE TABLE bank_categorization_rules ("
                    + id + ", company_id BIGINT NOT NULL, name VARCHAR(120) NOT NULL, "
                    + "match_text VARCHAR(255) NOT NULL, priority INTEGER NOT NULL, "
                    + "target_account_id BIGINT NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE, "
                    + "created_at " + (sqlite ? "DATETIME" : "DATETIME(6)") + " NOT NULL, "
                    + "CONSTRAINT fk_bank_categorization_rules_company FOREIGN KEY (company_id) REFERENCES companies (id), "
                    + "CONSTRAINT fk_bank_categorization_rules_account FOREIGN KEY (target_account_id) "
                    + "REFERENCES chart_of_accounts (id))");
        }
        createIndexIfMissing(connection, "idx_bank_categorization_rules_company_priority",
                "bank_categorization_rules", "company_id, priority, id");
        if (tableExists(connection, "bank_transactions")) {
            createIndexIfMissing(connection, "idx_bank_transactions_suggested_category",
                    "bank_transactions", "company_id, suggested_category_id");
            if (!sqlite) {
                ensureForeignKey(connection, "bank_transactions", "suggested_category_id",
                        "chart_of_accounts", "fk_bank_transactions_suggested_category");
                ensureForeignKey(connection, "bank_transactions", "categorization_rule_id",
                        "bank_categorization_rules", "fk_bank_transactions_categorization_rule");
            }
        }
    }

    private void applyCompanyLogosAndDocumentCorrections(Connection connection) throws SQLException {
        boolean sqlite = connection.getMetaData().getDatabaseProductName()
                .toLowerCase(Locale.ROOT).contains("sqlite");
        String binaryType = sqlite ? "BLOB" : "MEDIUMBLOB";
        String dateTimeType = sqlite ? "DATETIME" : "DATETIME(6)";
        addColumnIfMissing(connection, "companies", "logo_data", binaryType + " NULL");
        addColumnIfMissing(connection, "companies", "logo_content_type", "VARCHAR(100) NULL");
        addColumnIfMissing(connection, "invoices", "cancellation_reason", "VARCHAR(1000) NULL");
        addColumnIfMissing(connection, "invoices", "cancelled_at", dateTimeType + " NULL");
        addColumnIfMissing(connection, "bills", "cancellation_reason", "VARCHAR(1000) NULL");
        addColumnIfMissing(connection, "bills", "cancelled_at", dateTimeType + " NULL");
    }

    private void applyCompanyInvoiceTemplates(Connection connection) throws SQLException {
        String databaseProduct = connection.getMetaData().getDatabaseProductName()
                .toLowerCase(Locale.ROOT);
        boolean sqlite = databaseProduct.contains("sqlite");
        String binaryType = databaseProduct.contains("mysql") ? "MEDIUMBLOB" : "BLOB";
        String createTable = "CREATE TABLE IF NOT EXISTS invoice_templates ("
                + "id VARCHAR(36) NOT NULL PRIMARY KEY, "
                + "company_id BIGINT NOT NULL, "
                + "name VARCHAR(120) NOT NULL, "
                + "language_tag VARCHAR(35) NOT NULL, "
                + "original_filename VARCHAR(255) NOT NULL, "
                + "template_data " + binaryType + " NOT NULL, "
                + "created_at " + (sqlite ? "DATETIME" : "DATETIME(6)") + " NOT NULL, "
                + "CONSTRAINT uk_invoice_templates_company_language_name "
                + "UNIQUE (company_id, language_tag, name), "
                + "CONSTRAINT fk_invoice_templates_company FOREIGN KEY (company_id) "
                + "REFERENCES companies (id) ON DELETE CASCADE)";
        execute(connection, createTable);
        createIndexIfMissing(connection, "idx_invoice_templates_company", "invoice_templates", "company_id");
    }

    private void applyInvoiceFinalPdfs(Connection connection) throws SQLException {
        String databaseProduct = connection.getMetaData().getDatabaseProductName()
                .toLowerCase(Locale.ROOT);
        boolean sqlite = databaseProduct.contains("sqlite");
        String invoiceIdType = sqlite ? "INTEGER" : "BIGINT";
        String binaryType = databaseProduct.contains("mysql") ? "LONGBLOB" : "BLOB";
        String dateTimeType = sqlite ? "DATETIME" : "DATETIME(6)";
        execute(connection, "CREATE TABLE IF NOT EXISTS invoice_final_pdfs ("
                + "invoice_id " + invoiceIdType + " NOT NULL PRIMARY KEY, "
                + "pdf_data " + binaryType + " NOT NULL, "
                + "created_at " + dateTimeType + " NOT NULL, "
                + "CONSTRAINT fk_invoice_final_pdfs_invoice FOREIGN KEY (invoice_id) "
                + "REFERENCES invoices (id) ON DELETE CASCADE)");
    }

    private void applyCreditNotes(Connection connection) throws SQLException {
        String databaseProduct = connection.getMetaData().getDatabaseProductName()
                .toLowerCase(Locale.ROOT);
        boolean sqlite = databaseProduct.contains("sqlite");
        String idDefinition = sqlite ? "INTEGER NOT NULL PRIMARY KEY"
                : "BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY";
        String dateTimeType = sqlite ? "DATETIME" : "DATETIME(6)";
        String companyIdType = sqlite ? "INTEGER" : "BIGINT";
        execute(connection, "CREATE TABLE IF NOT EXISTS credit_notes ("
                + "id " + idDefinition + ", "
                + "company_id " + companyIdType + " NOT NULL, "
                + "invoice_id " + (sqlite ? "INTEGER" : "BIGINT") + " NOT NULL, "
                + "credit_note_number VARCHAR(255) NOT NULL, "
                + "credit_note_date DATE NOT NULL, "
                + "status VARCHAR(32) NOT NULL, "
                + "reason VARCHAR(1000) NOT NULL, "
                + "subtotal DECIMAL(19, 2) NOT NULL, "
                + "gst_amount DECIMAL(19, 2) NOT NULL, "
                + "hst_amount DECIMAL(19, 2) NOT NULL, "
                + "qst_amount DECIMAL(19, 2) NOT NULL, "
                + "total_amount DECIMAL(19, 2) NOT NULL, "
                + "issued_at " + dateTimeType + " NULL, "
                + "created_at " + dateTimeType + " NOT NULL, "
                + "updated_at " + dateTimeType + " NOT NULL, "
                + "CONSTRAINT uk_credit_notes_company_number UNIQUE (company_id, credit_note_number), "
                + "CONSTRAINT fk_credit_notes_company FOREIGN KEY (company_id) REFERENCES companies (id), "
                + "CONSTRAINT fk_credit_notes_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id))");
        execute(connection, "CREATE TABLE IF NOT EXISTS credit_note_line_items ("
                + "id " + idDefinition + ", "
                + "credit_note_id " + (sqlite ? "INTEGER" : "BIGINT") + " NOT NULL, "
                + "description VARCHAR(500) NOT NULL, "
                + "line_number INTEGER NOT NULL, "
                + "quantity DECIMAL(19, 2) NOT NULL, "
                + "unit_price DECIMAL(19, 2) NOT NULL, "
                + "total DECIMAL(19, 2) NOT NULL, "
                + "CONSTRAINT fk_credit_note_lines_note FOREIGN KEY (credit_note_id) "
                + "REFERENCES credit_notes (id) ON DELETE CASCADE)");
        createIndexIfMissing(connection, "idx_credit_notes_company", "credit_notes", "company_id");
        createIndexIfMissing(connection, "idx_credit_notes_invoice", "credit_notes", "invoice_id");
        createIndexIfMissing(connection, "idx_credit_note_lines_note", "credit_note_line_items", "credit_note_id");
    }

    private void applyCreditNoteRefunds(Connection connection) throws SQLException {
        String databaseProduct = connection.getMetaData().getDatabaseProductName()
                .toLowerCase(Locale.ROOT);
        boolean sqlite = databaseProduct.contains("sqlite");
        String idDefinition = sqlite ? "INTEGER NOT NULL PRIMARY KEY"
                : "BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY";
        String idType = sqlite ? "INTEGER" : "BIGINT";
        String dateTimeType = sqlite ? "DATETIME" : "DATETIME(6)";
        execute(connection, "CREATE TABLE IF NOT EXISTS credit_note_refunds ("
                + "id " + idDefinition + ", "
                + "company_id " + idType + " NOT NULL, "
                + "credit_note_id " + idType + " NOT NULL, "
                + "bank_account_id " + idType + " NOT NULL, "
                + "amount DECIMAL(19, 2) NOT NULL, "
                + "refund_date DATE NOT NULL, "
                + "payment_method VARCHAR(32) NOT NULL, "
                + "transaction_id VARCHAR(255) NULL, "
                + "reason VARCHAR(1000) NOT NULL, "
                + "created_at " + dateTimeType + " NOT NULL, "
                + "updated_at " + dateTimeType + " NOT NULL, "
                + "CONSTRAINT uk_credit_note_refunds_company_reference "
                + "UNIQUE (company_id, transaction_id), "
                + "CONSTRAINT fk_credit_note_refunds_company FOREIGN KEY (company_id) "
                + "REFERENCES companies (id), "
                + "CONSTRAINT fk_credit_note_refunds_note FOREIGN KEY (credit_note_id) "
                + "REFERENCES credit_notes (id), "
                + "CONSTRAINT fk_credit_note_refunds_bank FOREIGN KEY (bank_account_id) "
                + "REFERENCES chart_of_accounts (id))");
        createIndexIfMissing(connection, "idx_credit_note_refunds_note",
                "credit_note_refunds", "credit_note_id");
        createIndexIfMissing(connection, "idx_credit_note_refunds_company_date",
                "credit_note_refunds", "company_id, refund_date");
    }

    private void applyCreditNoteAllocations(Connection connection) throws SQLException {
        String databaseProduct = connection.getMetaData().getDatabaseProductName()
                .toLowerCase(Locale.ROOT);
        boolean sqlite = databaseProduct.contains("sqlite");
        String idDefinition = sqlite ? "INTEGER NOT NULL PRIMARY KEY"
                : "BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY";
        String idType = sqlite ? "INTEGER" : "BIGINT";
        String dateTimeType = sqlite ? "DATETIME" : "DATETIME(6)";
        execute(connection, "CREATE TABLE IF NOT EXISTS credit_note_allocations ("
                + "id " + idDefinition + ", "
                + "company_id " + idType + " NOT NULL, "
                + "credit_note_id " + idType + " NOT NULL, "
                + "target_invoice_id " + idType + " NOT NULL, "
                + "amount DECIMAL(19, 2) NOT NULL, "
                + "allocation_date DATE NOT NULL, "
                + "reversal_date DATE NULL, "
                + "reversal_reason VARCHAR(1000) NULL, "
                + "created_at " + dateTimeType + " NOT NULL, "
                + "updated_at " + dateTimeType + " NOT NULL, "
                + "CONSTRAINT fk_credit_note_allocations_company FOREIGN KEY (company_id) "
                + "REFERENCES companies (id), "
                + "CONSTRAINT fk_credit_note_allocations_note FOREIGN KEY (credit_note_id) "
                + "REFERENCES credit_notes (id), "
                + "CONSTRAINT fk_credit_note_allocations_target FOREIGN KEY (target_invoice_id) "
                + "REFERENCES invoices (id))");
        createIndexIfMissing(connection, "idx_credit_note_allocations_note",
                "credit_note_allocations", "credit_note_id");
        createIndexIfMissing(connection, "idx_credit_note_allocations_target",
                "credit_note_allocations", "target_invoice_id");
        createIndexIfMissing(connection, "idx_credit_note_allocations_company_date",
                "credit_note_allocations", "company_id, allocation_date");
    }

    private boolean tableExists(Connection connection, String table) throws SQLException {
        try (ResultSet tables = connection.getMetaData().getTables(connection.getCatalog(), null, "%",
                new String[]{"TABLE"})) {
            while (tables.next()) {
                if (table.equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void addColumnIfMissing(Connection connection, String table, String column, String definition)
            throws SQLException {
        if (!columnExists(connection, table, column)) {
            execute(connection, "ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    private void createIndexIfMissing(Connection connection, String index, String table, String columns)
            throws SQLException {
        String actualTable = findTableName(connection, table);
        if (actualTable == null) {
            return;
        }
        try (ResultSet indexes = connection.getMetaData().getIndexInfo(
                connection.getCatalog(), null, actualTable, false, false)) {
            while (indexes.next()) {
                if (index.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) {
                    return;
                }
            }
        }
        execute(connection, "CREATE INDEX " + index + " ON " + table + " (" + columns + ")");
    }

    private void ensureForeignKey(Connection connection, String table, String column, String targetTable,
                                  String constraint) throws SQLException {
        String actualTable = findTableName(connection, table);
        if (actualTable == null) {
            return;
        }
        try (ResultSet keys = connection.getMetaData().getImportedKeys(
                connection.getCatalog(), null, actualTable)) {
            while (keys.next()) {
                if (column.equalsIgnoreCase(keys.getString("FKCOLUMN_NAME"))
                        && targetTable.equalsIgnoreCase(keys.getString("PKTABLE_NAME"))) {
                    return;
                }
            }
        }
        execute(connection, "ALTER TABLE " + table + " ADD CONSTRAINT " + constraint
                + " FOREIGN KEY (" + column + ") REFERENCES " + targetTable + " (id)");
    }

    private String findTableName(Connection connection, String table) throws SQLException {
        try (ResultSet tables = connection.getMetaData().getTables(connection.getCatalog(), null, "%",
                new String[]{"TABLE"})) {
            while (tables.next()) {
                String actualName = tables.getString("TABLE_NAME");
                if (table.equalsIgnoreCase(actualName)) {
                    return actualName;
                }
            }
        }
        return null;
    }

    private boolean hasShortIdIndex(Connection connection) throws SQLException {
        var metadata = connection.getMetaData();
        try (ResultSet tables = metadata.getTables(connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                if ("app_users".equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                    try (ResultSet indexes = metadata.getIndexInfo(
                            connection.getCatalog(), tables.getString("TABLE_SCHEM"),
                            tables.getString("TABLE_NAME"), false, false)) {
                        while (indexes.next()) {
                            if ("uk_app_users_short_id".equalsIgnoreCase(indexes.getString("INDEX_NAME"))
                                    && "short_id".equalsIgnoreCase(indexes.getString("COLUMN_NAME"))
                                    && !indexes.getBoolean("NON_UNIQUE")) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private List<VersionEntry> versionHistory(String currentVersion, SchemaVersion parsedCurrent) {
        List<VersionEntry> versions = new ArrayList<>();
        versions.add(new VersionEntry(
                BASELINE_VERSION.toString(),
                "database.schema.version.baseline.title",
                "database.schema.version.baseline.description",
                parsedCurrent != null && parsedCurrent.equals(BASELINE_VERSION),
                parsedCurrent != null && parsedCurrent.compareTo(BASELINE_VERSION) >= 0,
                false));
        for (SchemaMigration migration : MIGRATIONS) {
            boolean applied = parsedCurrent != null
                    && parsedCurrent.compareTo(migration.version()) >= 0;
            versions.add(new VersionEntry(
                    migration.version().toString(),
                    migration.titleKey(),
                    migration.descriptionKey(),
                    parsedCurrent != null && parsedCurrent.equals(migration.version()),
                    applied,
                    migration.requiresBackup()));
        }
        boolean versionKnown = parsedCurrent != null && (parsedCurrent.equals(BASELINE_VERSION)
                || MIGRATIONS.stream().anyMatch(migration -> migration.version().equals(parsedCurrent)));
        if (!versionKnown) {
            versions.add(new VersionEntry(currentVersion, "database.schema.version.unrecognized.title",
                    "database.schema.version.unrecognized.description",
                    true, false, true));
        }
        return List.copyOf(versions);
    }

    private SchemaVersion latestVersion() {
        return MIGRATIONS.stream()
                .map(SchemaMigration::version)
                .max(Comparator.naturalOrder())
                .orElse(BASELINE_VERSION);
    }

    public record SchemaMigration(
            SchemaVersion version,
            String titleKey,
            String descriptionKey,
            String resource,
            boolean requiresBackup,
            String idempotencyTable,
            String idempotencyColumn) {
        public SchemaMigration(SchemaVersion version, String titleKey, String descriptionKey, String resource,
                              boolean requiresBackup) {
            this(version, titleKey, descriptionKey, resource, requiresBackup, null, null);
        }

        private boolean columnAlreadyExists() {
            return idempotencyTable != null && idempotencyColumn != null;
        }
    }

    public record VersionEntry(
            String version,
            String titleKey,
            String descriptionKey,
            boolean current,
            boolean applied,
            boolean requiresBackup) {
    }

    public record Status(
            String currentVersion,
            String latestVersion,
            List<VersionEntry> versions,
            List<SchemaMigration> pendingMigrations,
            boolean migrationPossible,
            boolean upToDate,
            String issue) {

        public boolean updateAvailable() {
            return !upToDate && migrationPossible && !pendingMigrations.isEmpty();
        }

        public boolean requiresBackup() {
            return pendingMigrations.stream().anyMatch(SchemaMigration::requiresBackup);
        }
    }
}
