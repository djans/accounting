package com.cogitosum.service;

import com.cogitosum.setup.DatabaseConfigurationStore;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseMigrationServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void logsTechnicalStackTraceAndRedactsConfiguredPasswords() throws Exception {
        DataSource sourceDataSource = mock(DataSource.class);
        Connection sourceConnection = mock(Connection.class);
        DatabaseMetaData sourceMetadata = mock(DatabaseMetaData.class);
        when(sourceDataSource.getConnection()).thenReturn(sourceConnection);
        when(sourceConnection.getMetaData()).thenReturn(sourceMetadata);
        when(sourceMetadata.getDatabaseProductName()).thenReturn("MySQL");

        DatabaseBackupService backupService = mock(DatabaseBackupService.class);
        when(backupService.exportDatabase()).thenThrow(new IllegalStateException(
                "Failed for jdbc:mysql://source/db?password=source-secret"));
        DatabaseSchemaVersionService schemaVersionService = mock(DatabaseSchemaVersionService.class);
        when(schemaVersionService.getStatus()).thenReturn(new DatabaseSchemaVersionService.Status(
                "1.1.0", "1.1.0", List.of(), List.of(), true, true, null));
        DataSourceProperties dataSourceProperties = new DataSourceProperties();
        dataSourceProperties.setPassword("source-secret");
        DatabaseMigrationService service = new DatabaseMigrationService(
                sourceDataSource,
                backupService,
                new DatabaseSchemaResetService(sourceDataSource, schemaVersionService),
                schemaVersionService,
                new DatabaseConfigurationStore(tempDir.resolve("database.properties")),
                dataSourceProperties,
                new ObjectMapper());

        Logger logger = (Logger) LoggerFactory.getLogger(DatabaseMigrationService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
        logger.addAppender(appender);
        try {
            assertThrows(DatabaseMigrationService.MigrationException.class,
                    () -> service.migrateToSqlite(tempDir.resolve("target.sqlite").toString()));

            ILoggingEvent technicalError = appender.list.stream()
                    .filter(event -> event.getFormattedMessage().contains("technical failure"))
                    .findFirst()
                    .orElseThrow();
            assertEquals("ERROR", technicalError.getMDCPropertyMap().get("logCategory"));
            assertTrue(technicalError.getFormattedMessage().contains("IllegalStateException"));
            assertTrue(technicalError.getFormattedMessage().contains("DatabaseMigrationService.migrate"));
            assertTrue(technicalError.getFormattedMessage().contains("password=****"));
            assertFalse(technicalError.getFormattedMessage().contains("source-secret"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void exposesSourceMysqlDetailsAndMasksCredentialsInsideUrl() throws Exception {
        DataSource sourceDataSource = mock(DataSource.class);
        Connection sourceConnection = mock(Connection.class);
        DatabaseMetaData sourceMetadata = mock(DatabaseMetaData.class);
        when(sourceDataSource.getConnection()).thenReturn(sourceConnection);
        when(sourceConnection.getMetaData()).thenReturn(sourceMetadata);
        when(sourceMetadata.getDatabaseProductName()).thenReturn("MySQL");

        DataSourceProperties dataSourceProperties = new DataSourceProperties();
        dataSourceProperties.setUrl("jdbc:mysql://db.example.test:3306/accounting?user=admin&password=secret");
        dataSourceProperties.setUsername("accounting_admin");
        dataSourceProperties.setPassword("secret");
        DatabaseSchemaVersionService schemaVersionService = mock(DatabaseSchemaVersionService.class);
        DatabaseMigrationService service = new DatabaseMigrationService(
                sourceDataSource,
                mock(DatabaseBackupService.class),
                new DatabaseSchemaResetService(sourceDataSource, schemaVersionService),
                schemaVersionService,
                new DatabaseConfigurationStore(tempDir.resolve("database.properties")),
                dataSourceProperties,
                new ObjectMapper());

        DatabaseMigrationService.MysqlConnectionDetails details =
                service.getSourceMysqlConnectionDetails();

        assertEquals("jdbc:mysql://db.example.test:3306/accounting?user=****&password=****",
                details.jdbcUrl());
        assertEquals("accounting_admin", details.username());
        assertEquals("secret", details.password());
    }

    @Test
    void migratesMysqlSourceIntoCreatedSqliteFileAndSavesRuntimeSettings() throws Exception {
        DataSource sourceDataSource = mock(DataSource.class);
        Connection sourceConnection = mock(Connection.class);
        DatabaseMetaData sourceMetadata = mock(DatabaseMetaData.class);
        when(sourceDataSource.getConnection()).thenReturn(sourceConnection);
        when(sourceConnection.getMetaData()).thenReturn(sourceMetadata);
        when(sourceMetadata.getDatabaseProductName()).thenReturn("MySQL");

        DatabaseBackupService backupService = mock(DatabaseBackupService.class);
        when(backupService.exportDatabase()).thenReturn("""
                {
                  "format": "cogitosum-accounting-backup",
                  "version": 1,
                  "tables": [{"name": "companies", "rows": []}]
                }
                """.getBytes(StandardCharsets.UTF_8));
        when(backupService.importDatabaseInto(any(byte[].class), any(DataSource.class))).thenReturn(3);

        DatabaseSchemaVersionService schemaVersionService = mock(DatabaseSchemaVersionService.class);
        DatabaseSchemaVersionService.Status currentSchema = new DatabaseSchemaVersionService.Status(
                "1.1.0", "1.1.0", List.of(), List.of(), true, true, null);
        when(schemaVersionService.getStatus()).thenReturn(currentSchema);
        when(schemaVersionService.applyPendingMigrations(any(Connection.class))).thenReturn(currentSchema);

        DatabaseSchemaResetService schemaResetService =
                new DatabaseSchemaResetService(sourceDataSource, schemaVersionService);
        Path configurationFile = tempDir.resolve("database.properties");
        DatabaseConfigurationStore configurationStore = new DatabaseConfigurationStore(configurationFile);
        DataSourceProperties dataSourceProperties = new DataSourceProperties();
        DatabaseMigrationService service = new DatabaseMigrationService(
                sourceDataSource, backupService, schemaResetService, schemaVersionService,
                configurationStore, dataSourceProperties, new ObjectMapper());
        Path targetFile = tempDir.resolve("new-target.sqlite");

        DatabaseMigrationService.MigrationResult result =
                service.migrateToSqlite(targetFile.toString());

        assertEquals("mysql", result.sourceType());
        assertEquals("sqlite", result.targetType());
        assertEquals(3, result.importedRows());
        assertTrue(Files.isRegularFile(targetFile));
        assertEquals("sqlite", configurationStore.load().getProperty("app.database.type"));
        assertEquals("org.sqlite.JDBC",
                configurationStore.load().getProperty("spring.datasource.driver-class-name"));

        DriverManagerDataSource target = new DriverManagerDataSource();
        target.setDriverClassName("org.sqlite.JDBC");
        target.setUrl("jdbc:sqlite:" + targetFile.toString().replace('\\', '/'));
        try (Connection connection = target.getConnection();
             var tables = connection.getMetaData().getTables(null, null, "companies", new String[]{"TABLE"})) {
            assertTrue(tables.next());
        }
    }

    @Test
    void refusesDestinationWithUnrelatedObjectsWithoutChangingIt() throws Exception {
        DataSource sourceDataSource = mock(DataSource.class);
        Connection sourceConnection = mock(Connection.class);
        DatabaseMetaData sourceMetadata = mock(DatabaseMetaData.class);
        when(sourceDataSource.getConnection()).thenReturn(sourceConnection);
        when(sourceConnection.getMetaData()).thenReturn(sourceMetadata);
        when(sourceMetadata.getDatabaseProductName()).thenReturn("MySQL");

        DatabaseBackupService backupService = mock(DatabaseBackupService.class);
        when(backupService.exportDatabase()).thenReturn("""
                {
                  "format": "cogitosum-accounting-backup",
                  "version": 1,
                  "tables": [{"name": "companies", "rows": []}]
                }
                """.getBytes(StandardCharsets.UTF_8));
        DatabaseSchemaVersionService schemaVersionService = mock(DatabaseSchemaVersionService.class);
        when(schemaVersionService.getStatus()).thenReturn(new DatabaseSchemaVersionService.Status(
                "1.1.0", "1.1.0", List.of(), List.of(), true, true, null));

        Path targetFile = tempDir.resolve("unrelated.sqlite");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + targetFile);
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE user_data (id INTEGER PRIMARY KEY)");
            statement.execute("INSERT INTO user_data (id) VALUES (17)");
        }

        DatabaseConfigurationStore configurationStore =
                new DatabaseConfigurationStore(tempDir.resolve("database.properties"));
        DatabaseMigrationService service = new DatabaseMigrationService(
                sourceDataSource,
                backupService,
                new DatabaseSchemaResetService(sourceDataSource, schemaVersionService),
                schemaVersionService,
                configurationStore,
                new DataSourceProperties(),
                new ObjectMapper());

        DatabaseMigrationService.MigrationException exception = assertThrows(
                DatabaseMigrationService.MigrationException.class,
                () -> service.migrateToSqlite(targetFile.toString()));

        assertEquals("database.engineMigration.error.unknownTargetObjects", exception.messageKey());
        assertFalse(configurationStore.isConfigured());
        assertEquals(1, new JdbcTemplate(sqliteDataSource(targetFile))
                .queryForObject("SELECT COUNT(*) FROM user_data WHERE id = 17", Integer.class));
    }

    private DriverManagerDataSource sqliteDataSource(Path path) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setUrl("jdbc:sqlite:" + path.toString().replace('\\', '/'));
        return dataSource;
    }
}
