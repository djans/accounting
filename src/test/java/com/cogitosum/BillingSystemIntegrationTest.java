package com.cogitosum;

import com.cogitosum.entity.Customer;
import com.cogitosum.entity.UserRole;
import com.cogitosum.repository.CompanyMembershipRepository;
import com.cogitosum.repository.CustomerRepository;
import com.cogitosum.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "test-admin@example.test", roles = "ADMIN")
public class BillingSystemIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private CompanyMembershipRepository companyMembershipRepository;

    @Autowired
    private com.cogitosum.service.CurrentCompanyContext companyContext;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setup() {
        customerRepository.deleteAll();
    }

    @Test
    public void testCreateCustomer() throws Exception {
        Customer customer = new Customer();
        customer.setName("John Doe");
        customer.setEmail("john@example.com");
        customer.setBusinessName("Acme Corp");
        customer.setAddress("123 Main St");
        customer.setCity("Toronto");
        customer.setProvince("ON");
        customer.setPostalCode("M5H 2N2");
        customer.setCountry("Canada");
        customer.setGstNumber("123456789RT0001");

        mockMvc.perform(post("/api/customers")
                .with(user("test-admin@example.test").roles("ADMIN"))
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(customer)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.email").value("john@example.com"));
    }

    @Test
    public void testGetAllCustomers() throws Exception {
        mockMvc.perform(get("/api/customers")
                        .with(user("test-admin@example.test").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    public void dashboardShowsCompanyFinancialKpis() throws Exception {
        mockMvc.perform(get("/")
                        .with(user("test-admin@example.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Financial overview")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Revenue")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Net profit")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Cash")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Taxes to remit")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Accounts receivable")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Accounts payable")));
    }

    @Test
    public void testGetCustomerByEmail() throws Exception {
        Customer customer = new Customer();
        customer.setName("Jane Doe");
        customer.setEmail("jane@example.com");
        customer.setBusinessName("Tech Corp");
        customer.setAddress("456 Oak Ave");
        customer.setCity("Vancouver");
        customer.setProvince("BC");
        customer.setPostalCode("V6B 1Y8");
        customer.setCountry("Canada");

        customer.setCompany(companyContext.requireCompany());
        customerRepository.save(customer);

        mockMvc.perform(get("/api/customers/email/jane@example.com")
                        .with(user("test-admin@example.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Jane Doe"));
    }

    @Test
    public void actuatorEndpointsAreAdministratorOnlyAndHealthDetailsAreHidden() throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .with(user("test-admin@example.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("components"))));

        mockMvc.perform(get("/actuator/health")
                        .with(user("accountant@example.test").roles("ACCOUNTANT")))
                .andExpect(status().isForbidden());
    }

    @Test
    public void monitoringPageRendersForAdministrator() throws Exception {
        mockMvc.perform(get("/admin/monitoring")
                        .with(user("test-admin@example.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("System monitoring")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/actuator/metrics")));
    }

    @Test
    public void companyUserPageListsMembershipsForAdministratorsOnly() throws Exception {
        mockMvc.perform(get("/admin/users")
                        .with(user("test-admin@example.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Company user access")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Users with company access")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("test-admin@example.test")));

        mockMvc.perform(get("/admin/users")
                        .with(user("viewer@example.test").roles("VIEWER")))
                .andExpect(status().isForbidden());
    }

    @Test
    public void administratorCanCreateAndRevokeCompanyAccessWithoutDeletingTheAccount() throws Exception {
        mockMvc.perform(post("/admin/users/create")
                        .with(user("test-admin@example.test").roles("ADMIN"))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("fullName", "Company Bookkeeper")
                        .param("email", "company-bookkeeper@example.test")
                        .param("role", "BOOKKEEPER")
                        .param("password", "Initial-password-123!")
                        .param("confirmPassword", "Initial-password-123!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"));

        var created = userAccountRepository.findByEmail("company-bookkeeper@example.test").orElseThrow();
        Long companyId = created.getCompany().getId();
        assertEquals(UserRole.BOOKKEEPER,
                companyMembershipRepository.findRole(created.getId(), companyId).orElseThrow());
        assertTrue(companyMembershipRepository.isMember(created.getId(), companyId));

        mockMvc.perform(post("/admin/users/{userId}/revoke", created.getId())
                        .with(user("test-admin@example.test").roles("ADMIN"))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"));

        assertTrue(userAccountRepository.existsById(created.getId()));
        assertFalse(companyMembershipRepository.isMember(created.getId(), companyId));
    }

    @Test
    public void resetPageCombinesCompanyResetAndRestartFromScratchOptions() throws Exception {
        mockMvc.perform(get("/admin/reset")
                        .with(user("test-admin@example.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Reset activity data")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Restart from scratch")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/admin/database/recreate")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Full restart workflow")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Step 3 — Import initial data")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/database/migration")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"resetOperation\"")));

        mockMvc.perform(get("/admin/reset").param("operation", "restart")
                        .with(user("test-admin@example.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "id=\"company-activity-option\" class=\"reset-operation-panel\" data-reset-operation-panel=\"activity\" hidden=\"hidden\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"restart-from-scratch-option\"")));

        mockMvc.perform(get("/database/migration")
                        .with(user("test-admin@example.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Back to reset and import options")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/database/migration/example-package")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Download sample ZIP")));

        mockMvc.perform(get("/database/migration/documentation")
                        .with(user("test-admin@example.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Back to reset and import options")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Supported data scope")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Download sample ZIP")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("bill tax_regime")));
    }

    @Test
    public void exampleInitialDataPackageDownloadsForAdministrator() throws Exception {
        mockMvc.perform(get("/database/migration/example-package")
                        .with(user("test-admin@example.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("initial-data-migration-v1-example.zip")));
    }

    @Test
    public void testUpdateCustomer() throws Exception {
        Customer customer = new Customer();
        customer.setName("Original Name");
        customer.setEmail("test@example.com");
        customer.setBusinessName("Original Corp");
        customer.setAddress("100 Main St");
        customer.setCity("Montreal");
        customer.setProvince("QC");
        customer.setPostalCode("H2X 1Y5");
        customer.setCountry("Canada");

        customer.setCompany(companyContext.requireCompany());
        Customer savedCustomer = customerRepository.save(customer);

        savedCustomer.setName("Updated Name");

        mockMvc.perform(put("/api/customers/" + savedCustomer.getId())
                .with(user("test-admin@example.test").roles("ADMIN"))
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(savedCustomer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"));
    }

    @Test
    public void testDeleteCustomer() throws Exception {
        Customer customer = new Customer();
        customer.setName("Delete Me");
        customer.setEmail("delete@example.com");
        customer.setBusinessName("Delete Corp");
        customer.setAddress("999 Delete St");
        customer.setCity("Calgary");
        customer.setProvince("AB");
        customer.setPostalCode("T2P 0R7");
        customer.setCountry("Canada");

        customer.setCompany(companyContext.requireCompany());
        Customer savedCustomer = customerRepository.save(customer);

        mockMvc.perform(delete("/api/customers/" + savedCustomer.getId())
                        .with(user("test-admin@example.test").roles("ADMIN"))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isNoContent());
    }
}
