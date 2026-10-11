package com.cogitosum.setup;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseSetupServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void sqliteSchemaAndVersionMigrationInitializeTheDatabase() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            new ResourceDatabasePopulator(new ClassPathResource("schema-sqlite.sql")).populate(connection);
            new ResourceDatabasePopulator(new ClassPathResource(
                    "db/migration/V1_1_0__schema_version_ledger.sql")).populate(connection);

            try (var statement = connection.createStatement();
                 var result = statement.executeQuery(
                         "SELECT version FROM schema_version_history WHERE singleton_id = 1")) {
                assertTrue(result.next());
                assertEquals("1.0.0", result.getString(1));
            }
        }
    }

    @Test
    void createsAnEmptySqliteFileAndPersistsRuntimeConfiguration() throws Exception {
        Path configurationFile = temporaryDirectory.resolve("config/database.properties");
        Path databaseFile = temporaryDirectory.resolve("data/accounting.sqlite");
        DatabaseSetupService service = new DatabaseSetupService(
                new DatabaseConfigurationStore(configurationFile));

        service.configure("sqlite", null, null, null, databaseFile.toString());

        assertTrue(Files.isRegularFile(databaseFile));
        var properties = new DatabaseConfigurationStore(configurationFile).load();
        assertEquals("sqlite", properties.getProperty("app.database.type"));
        assertEquals("org.sqlite.JDBC", properties.getProperty("spring.datasource.driver-class-name"));
        assertEquals("classpath:schema-sqlite.sql",
                properties.getProperty("spring.sql.init.schema-locations"));
    }

    @Test
    void refusesToReconfigureAnExistingSqliteDatabaseWithTables() throws Exception {
        Path configurationFile = temporaryDirectory.resolve("config/database.properties");
        Path databaseFile = temporaryDirectory.resolve("existing.sqlite");
        String url = "jdbc:sqlite:" + databaseFile.toString().replace('\\', '/');
        try (var connection = DriverManager.getConnection(url);
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE existing_data (id INTEGER PRIMARY KEY)");
        }
        DatabaseSetupService service = new DatabaseSetupService(
                new DatabaseConfigurationStore(configurationFile));

        assertThrows(DatabaseSetupException.class,
                () -> service.configure("sqlite", null, null, null, databaseFile.toString()));
        assertTrue(Files.notExists(configurationFile));
    }
}
