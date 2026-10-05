package com.cogitosum.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(2)
public class CompanyMembershipMigration implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public CompanyMembershipMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        jdbcTemplate.update(
            "INSERT INTO user_company_memberships (user_id, company_id) "
                + "SELECT u.id, u.company_id FROM app_users u "
                + "WHERE NOT EXISTS (SELECT 1 FROM user_company_memberships m "
                + "WHERE m.user_id = u.id AND m.company_id = u.company_id)");
    }
}
