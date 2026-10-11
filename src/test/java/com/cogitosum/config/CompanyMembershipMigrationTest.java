package com.cogitosum.config;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompanyMembershipMigrationTest {

    @Test
    void backfillsLegacyMembershipsOnlyWhileTheMembershipTableIsEmpty() {
        var database = new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .build();
        try {
            JdbcTemplate jdbc = new JdbcTemplate(database);
            jdbc.execute("CREATE TABLE app_users (id BIGINT PRIMARY KEY, company_id BIGINT NOT NULL, role VARCHAR(32) NOT NULL)");
            jdbc.execute("CREATE TABLE user_company_memberships ("
                + "user_id BIGINT NOT NULL, company_id BIGINT NOT NULL, role VARCHAR(32) NOT NULL DEFAULT 'AUDITOR', "
                + "PRIMARY KEY (user_id, company_id))");
            jdbc.update("INSERT INTO app_users (id, company_id, role) VALUES "
                + "(1, 10, 'ADMIN'), (2, 20, 'BOOKKEEPER')");

            CompanyMembershipMigration migration = new CompanyMembershipMigration(jdbc);
            migration.run();

            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM user_company_memberships", Integer.class));
            assertEquals("ADMIN", jdbc.queryForObject(
                "SELECT role FROM user_company_memberships WHERE user_id = 1", String.class));
            assertEquals("BOOKKEEPER", jdbc.queryForObject(
                "SELECT role FROM user_company_memberships WHERE user_id = 2", String.class));

            jdbc.update("DELETE FROM user_company_memberships WHERE user_id = 1 AND company_id = 10");
            migration.run();

            assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_company_memberships WHERE user_id = 1", Integer.class));
            assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_company_memberships WHERE user_id = 2", Integer.class));
        } finally {
            database.shutdown();
        }
    }
}
