package com.cogitosum.service;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseSchemaVersionServiceTest {

    private DatabaseSchemaVersionService service;
    private JdbcDataSource dataSource;

    @BeforeEach
    void setUp() {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:schema-version-test-" + java.util.UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        service = new DatabaseSchemaVersionService(dataSource);
    }

    @Test
    void missingLedgerUsesBaselineAndListsAllKnownVersions() {
        DatabaseSchemaVersionService.Status status = service.getStatus();

        assertEquals("1.0.0", status.currentVersion());
        assertEquals("1.1.0", status.latestVersion());
        assertTrue(status.updateAvailable());
        assertEquals(2, status.versions().size());
        assertEquals("1.1.0", status.pendingMigrations().get(0).version().toString());
    }

    @Test
    void appliesEveryPendingMigrationAndAdvancesLedger() {
        DatabaseSchemaVersionService.Status status = service.applyPendingMigrations();

        assertEquals("1.1.0", status.currentVersion());
        assertTrue(status.upToDate());
        assertFalse(status.updateAvailable());
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
