package com.cogitosum.repository.jdbc;

import com.cogitosum.entity.Company;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcCompanyMembershipRepositoryTest {

    @Test
    void loadsPostedJournalEditingSettingForAccessibleCompanies() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:company-membership-" + java.util.UUID.randomUUID()
                + ";DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
                CREATE TABLE companies (
                    id BIGINT PRIMARY KEY, name VARCHAR(100), legal_name VARCHAR(100),
                    email VARCHAR(100), phone VARCHAR(30), address VARCHAR(100),
                    city VARCHAR(50), province VARCHAR(50), postal_code VARCHAR(20),
                    country VARCHAR(50), business_number VARCHAR(50), gst_number VARCHAR(50),
                    qst_number VARCHAR(50), currency VARCHAR(3), default_tax_province VARCHAR(2),
                    fiscal_year_start_month INT, posted_journal_editing_enabled BOOLEAN NOT NULL,
                    created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE user_company_memberships (user_id BIGINT, company_id BIGINT)
                """);
        jdbcTemplate.update("""
                INSERT INTO companies (
                    id, name, legal_name, email, phone, address, city, province, postal_code,
                    country, currency, default_tax_province, fiscal_year_start_month,
                    posted_journal_editing_enabled, created_at, updated_at
                ) VALUES (1, 'Company', 'Company Ltd', 'company@example.test', '555-0100', '1 Main St',
                    'Ottawa', 'ON', 'K1A 0B1', 'Canada', 'CAD', 'ON', 1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update(
                "INSERT INTO user_company_memberships (user_id, company_id) VALUES (7, 1)");

        List<Company> companies = new JdbcCompanyMembershipRepository(jdbcTemplate).findCompaniesByUserId(7L);

        assertTrue(companies.getFirst().isPostedJournalEditingEnabled());
    }
}
