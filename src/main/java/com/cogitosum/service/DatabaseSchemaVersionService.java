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
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(true);
            try {
                Status before = getStatus(connection);
                if (!before.migrationPossible()) {
                    throw new IllegalStateException(before.issue());
                }

                for (SchemaMigration migration : before.pendingMigrations()) {
                    ClassPathResource script = new ClassPathResource(migration.resource());
                    new ResourceDatabasePopulator(script).populate(connection);
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
            boolean requiresBackup) {
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
