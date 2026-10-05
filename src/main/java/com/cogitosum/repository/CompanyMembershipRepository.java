package com.cogitosum.repository;

import com.cogitosum.entity.Company;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class CompanyMembershipRepository {

    private final JdbcTemplate jdbcTemplate;

    public CompanyMembershipRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isMember(Long userId, Long companyId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user_company_memberships WHERE user_id = ? AND company_id = ?",
            Integer.class, userId, companyId);
        return count != null && count > 0;
    }

    public List<Company> findCompaniesByUserId(Long userId) {
        return jdbcTemplate.query(
            "SELECT c.id, c.name, c.legal_name, c.email, c.phone, c.address, c.city, c.province, "
                + "c.postal_code, c.country, c.business_number, c.gst_number, c.qst_number, c.currency, "
                + "c.default_tax_province, c.fiscal_year_start_month, c.created_at, c.updated_at "
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
                company.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                company.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
                return company;
            }, userId);
    }

    public void grant(Long userId, Long companyId) {
        jdbcTemplate.update(
            "INSERT INTO user_company_memberships (user_id, company_id) "
                + "SELECT ?, ? WHERE NOT EXISTS (SELECT 1 FROM user_company_memberships WHERE user_id = ? AND company_id = ?)",
            userId, companyId, userId, companyId);
    }
}
