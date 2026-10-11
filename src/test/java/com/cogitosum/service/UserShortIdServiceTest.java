package com.cogitosum.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserShortIdServiceTest {

    @Test
    void assignsUniqueSevenCharacterIdsWithSequenceNumbersAndShortSurnamePadding() {
        var database = new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .build();
        try {
            JdbcTemplate jdbc = new JdbcTemplate(database);
            jdbc.execute("CREATE TABLE app_users ("
                + "id BIGINT PRIMARY KEY, full_name VARCHAR(255) NOT NULL, email VARCHAR(255) NOT NULL, "
                + "short_id VARCHAR(7) UNIQUE)");
            jdbc.update("INSERT INTO app_users (id, full_name, email) VALUES "
                + "(1, 'Daniel Jans', 'one@example.test'), "
                + "(2, 'Daniel Jans', 'two@example.test'), "
                + "(3, 'Li Bo', 'three@example.test'), "
                + "(4, 'Daniel', 'four@example.test')");

            UserShortIdService shortIds = new UserShortIdService(jdbc);

            assertEquals("JAND001", shortIds.getOrCreate("one@example.test"));
            assertEquals("JAND002", shortIds.getOrCreate("two@example.test"));
            assertEquals("BOLL001", shortIds.getOrCreate("three@example.test"));
            assertEquals("DAND001", shortIds.getOrCreate("four@example.test"));
            shortIds.assignMissingShortIds();
            assertEquals(4, jdbc.queryForObject(
                "SELECT COUNT(DISTINCT short_id) FROM app_users", Integer.class));
        } finally {
            database.shutdown();
        }
    }
}
