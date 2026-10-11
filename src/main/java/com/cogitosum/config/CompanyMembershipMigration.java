package com.cogitosum.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
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
        // Backfill legacy databases once, while the new membership table is empty.
        // Repeating it after revocations would silently restore a user's old access.
        boolean hasRoleColumn = jdbcTemplate.execute((ConnectionCallback<Boolean>) connection -> {
            try (var columns = connection.getMetaData().getColumns(
                    connection.getCatalog(), null, "%", "%")) {
                while (columns.next()) {
                    if ("user_company_memberships".equalsIgnoreCase(columns.getString("TABLE_NAME"))
                            && "role".equalsIgnoreCase(columns.getString("COLUMN_NAME"))) {
                        return true;
                    }
                }
            }
            return false;
        });
        String columns = hasRoleColumn ? "(user_id, company_id, role)" : "(user_id, company_id)";
        String select = hasRoleColumn ? "u.id, u.company_id, u.role" : "u.id, u.company_id";
        jdbcTemplate.update(
            "INSERT INTO user_company_memberships " + columns + " SELECT " + select + " FROM app_users u "
                + "WHERE NOT EXISTS (SELECT 1 FROM user_company_memberships)");
    }
}
