package com.cogitosum.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class DatabaseBackupServiceTargetImportTest {

    @TempDir
    Path tempDir;

    private JdbcTemplate targetJdbc;
    private DatabaseBackupService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource targetDataSource = new DriverManagerDataSource();
        targetDataSource.setDriverClassName("org.sqlite.JDBC");
        targetDataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("target.sqlite"));
        targetJdbc = new JdbcTemplate(targetDataSource);
        targetJdbc.execute("""
                CREATE TABLE migration_records (
                    id INTEGER PRIMARY KEY,
                    enabled BOOLEAN NOT NULL,
                    payload BLOB NOT NULL
                )
                """);

        DataSource unusedSource = mock(DataSource.class);
        service = new DatabaseBackupService(new JdbcTemplate(unusedSource), unusedSource,
                new ObjectMapper(), mock(EntityManager.class));
    }

    @Test
    void importsBooleanAndBinaryValuesIntoAnotherDatabase() throws Exception {
        byte[] backup = """
                {
                  "format": "cogitosum-accounting-backup",
                  "version": 1,
                  "tables": [{
                    "name": "migration_records",
                    "rows": [{"id": 1, "enabled": "AQ==", "payload": "AAE="}]
                  }]
                }
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        assertEquals(1, service.importDatabaseInto(backup, targetJdbc.getDataSource()));

        assertEquals(Boolean.TRUE,
                targetJdbc.queryForObject("SELECT enabled FROM migration_records WHERE id = 1", Boolean.class));
        byte[] payload = targetJdbc.queryForObject(
                "SELECT payload FROM migration_records WHERE id = 1", byte[].class);
        assertArrayEquals(new byte[]{0, 1}, payload, java.util.Arrays.toString(payload));
    }

    @Test
    void rejectsUnknownColumnsBeforeClearingDestinationRows() throws Exception {
        targetJdbc.update("INSERT INTO migration_records (id, enabled, payload) VALUES (1, TRUE, X'01')");
        byte[] backup = """
                {
                  "format": "cogitosum-accounting-backup",
                  "version": 1,
                  "tables": [{
                    "name": "migration_records",
                    "rows": [{"id": 2, "enabled": true, "payload": "AQ==", "unknown": "value"}]
                  }]
                }
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class,
                () -> service.importDatabaseInto(backup, targetJdbc.getDataSource()));

        assertEquals(1, targetJdbc.queryForObject(
                "SELECT COUNT(*) FROM migration_records WHERE id = 1", Integer.class));
    }
}
