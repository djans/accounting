package com.cogitosum.repository.jdbc;

import com.cogitosum.entity.Company;
import com.cogitosum.entity.UserRole;
import com.cogitosum.repository.CompanyMembershipRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Repository;

import java.util.Locale;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcCompanyMembershipRepository implements CompanyMembershipRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcCompanyMembershipRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean isMember(Long userId, Long companyId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user_company_memberships WHERE user_id = ? AND company_id = ?",
            Integer.class, userId, companyId);
        return count != null && count > 0;
    }

    @Override
    public List<Company> findCompaniesByUserId(Long userId) {
        return jdbcTemplate.query(
            "SELECT c.id, c.name, c.legal_name, c.email, c.phone, c.address, c.city, c.province, "
                + "c.postal_code, c.country, c.business_number, c.gst_number, c.qst_number, c.currency, "
            + "c.default_tax_province, c.fiscal_year_start_month, c.posted_journal_editing_enabled, "
            + "c.created_at, c.updated_at "
            + "FROM companies c JOIN user_company_memberships m ON m.company_id = c.id "
            + "WHERE m.user_id = ? ORDER BY c.name, c.id",
            (rs, rowNum) -> {
                Company company = new Company();
                company.setId(rs.getLong("id"));
                company.setName(rs.getString("name"));
                company.setLegalName(rs.getString("legal_name"));
                company.setEmail(rs.getString("email"));
                company.setPhone(rs.getString("phone"));
                company.setAddress(rs.getString("address"));
                company.setCity(rs.getString("city"));
                company.setProvince(rs.getString("province"));
                company.setPostalCode(rs.getString("postal_code"));
                company.setCountry(rs.getString("country"));
                company.setBusinessNumber(rs.getString("business_number"));
                company.setGstNumber(rs.getString("gst_number"));
                company.setQstNumber(rs.getString("qst_number"));
                company.setCurrency(rs.getString("currency"));
                company.setDefaultTaxProvince(rs.getString("default_tax_province"));
                company.setFiscalYearStartMonth(rs.getInt("fiscal_year_start_month"));
                company.setPostedJournalEditingEnabled(rs.getBoolean("posted_journal_editing_enabled"));
                company.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                company.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
                return company;
            }, userId);
    }

    @Override
    public Optional<UserRole> findRole(Long userId, Long companyId) {
        String roleColumn = hasColumn("user_company_memberships", "role") ? "m.role" : "u.role";
        List<String> values = jdbcTemplate.query(
            "SELECT " + roleColumn + " FROM user_company_memberships m "
                + "JOIN app_users u ON u.id = m.user_id "
                + "WHERE m.user_id = ? AND m.company_id = ?",
            (rs, rowNum) -> rs.getString(1), userId, companyId);
        return values.stream().findFirst().map(this::normalizeRole);
    }

    @Override
    public List<Member> findMembersByCompanyId(Long companyId) {
        String roleColumn = hasColumn("user_company_memberships", "role") ? "m.role" : "u.role";
        String shortIdColumn = hasColumn("app_users", "short_id") ? "u.short_id" : "NULL";
        return jdbcTemplate.query(
            "SELECT u.id, u.full_name, u.email, u.enabled, " + roleColumn + " AS membership_role, "
                + shortIdColumn + " AS short_id "
                + "FROM app_users u JOIN user_company_memberships m ON m.user_id = u.id "
                + "WHERE m.company_id = ? ORDER BY u.full_name, u.email",
            (rs, rowNum) -> new Member(
                rs.getLong("id"),
                rs.getString("full_name"),
                rs.getString("email"),
                rs.getBoolean("enabled"),
                normalizeRole(rs.getString("membership_role")),
                rs.getString("short_id")),
            companyId);
    }

    @Override
    public void grant(Long userId, Long companyId, UserRole role) {
        // This INSERT ... SELECT has no FROM clause; verify or adapt it for each new database engine.
        if (!hasColumn("user_company_memberships", "role")) {
            jdbcTemplate.update(
                "INSERT INTO user_company_memberships (user_id, company_id) "
                    + "SELECT ?, ? WHERE NOT EXISTS "
                    + "(SELECT 1 FROM user_company_memberships WHERE user_id = ? AND company_id = ?)",
                userId, companyId, userId, companyId);
            return;
        }
        jdbcTemplate.update(
            "INSERT INTO user_company_memberships (user_id, company_id, role) "
                + "SELECT ?, ?, ? WHERE NOT EXISTS (SELECT 1 FROM user_company_memberships "
                + "WHERE user_id = ? AND company_id = ?)",
            userId, companyId, role.name(), userId, companyId);
    }

    @Override
    public void updateRole(Long userId, Long companyId, UserRole role) {
        jdbcTemplate.update(
            "UPDATE user_company_memberships SET role = ? WHERE user_id = ? AND company_id = ?",
            role.name(), userId, companyId);
    }

    @Override
    public void revoke(Long userId, Long companyId) {
        jdbcTemplate.update(
            "DELETE FROM user_company_memberships WHERE user_id = ? AND company_id = ?",
            userId, companyId);
    }

    private boolean hasColumn(String tableName, String columnName) {
        return jdbcTemplate.execute((ConnectionCallback<Boolean>) connection -> {
            try (var columns = connection.getMetaData().getColumns(
                    connection.getCatalog(), null, "%", "%")) {
                while (columns.next()) {
                    if (tableName.equalsIgnoreCase(columns.getString("TABLE_NAME"))
                            && columnName.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) {
                        return true;
                    }
                }
            }
            return false;
        });
    }

    private UserRole normalizeRole(String role) {
        if ("VIEWER".equalsIgnoreCase(role)) {
            return UserRole.AUDITOR;
        }
        return UserRole.valueOf(role.toUpperCase(Locale.ROOT));
    }
}
