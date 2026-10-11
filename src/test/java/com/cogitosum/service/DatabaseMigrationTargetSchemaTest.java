package com.cogitosum.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseMigrationTargetSchemaTest {

    @TempDir
    Path tempDir;

    @Test
    void createsCurrentSqliteSchemaAndVersionLedgerForMigrationTarget() throws Exception {
        DataSource dataSource = sqliteDataSource(tempDir.resolve("migration-target.sqlite"));
        DatabaseSchemaVersionService versionService = new DatabaseSchemaVersionService(dataSource);
        DatabaseSchemaResetService resetService =
                new DatabaseSchemaResetService(dataSource, versionService);

        try (Connection connection = dataSource.getConnection()) {
            assertEquals(0, resetService.recreateSchema(connection));
            assertTrue(versionService.applyPendingMigrations(connection).upToDate());
        }
    }

    private DriverManagerDataSource sqliteDataSource(Path path) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setUrl("jdbc:sqlite:" + path);
        return dataSource;
    }
}
