package com.cogitosum;

import com.cogitosum.entity.Customer;
import com.cogitosum.entity.CreditNote;
import com.cogitosum.entity.CreditNoteRefund;
import com.cogitosum.entity.CreditNoteAllocation;
import com.cogitosum.entity.CreditNoteStatus;
import com.cogitosum.entity.AccountCategory;
import com.cogitosum.entity.AccountType;
import com.cogitosum.entity.ChartOfAccount;
import com.cogitosum.entity.Invoice;
import com.cogitosum.entity.InvoiceStatus;
import com.cogitosum.entity.LineItem;
import com.cogitosum.entity.PaymentMethod;
import com.cogitosum.entity.UserAccount;
import com.cogitosum.entity.UserRole;
import com.cogitosum.repository.CompanyMembershipRepository;
import com.cogitosum.repository.CreditNoteRepository;
import com.cogitosum.repository.CreditNoteRefundRepository;
import com.cogitosum.repository.CreditNoteAllocationRepository;
import com.cogitosum.repository.CustomerRepository;
import com.cogitosum.repository.ChartOfAccountRepository;
import com.cogitosum.repository.GeneralJournalRepository;
import com.cogitosum.repository.InvoiceRepository;
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
import org.springframework.transaction.annotation.Transactional;
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
    private InvoiceRepository invoiceRepository;

    @Autowired
    private CreditNoteRepository creditNoteRepository;

    @Autowired
    private CreditNoteRefundRepository creditNoteRefundRepository;

    @Autowired
    private CreditNoteAllocationRepository creditNoteAllocationRepository;

    @Autowired
    private ChartOfAccountRepository chartOfAccountRepository;

    @Autowired
    private com.cogitosum.service.ChartOfAccountService chartOfAccountService;

    @Autowired
    private GeneralJournalRepository generalJournalRepository;

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
    @Transactional
    public void createsAndRendersLinkedCreditNotePages() throws Exception {
        Long companyId = companyContext.requireCompanyId();
        Customer customer = new Customer();
        customer.setName("Credit note customer");
        customer.setEmail("credit-note@example.test");
        customer.setBusinessName("Credit Note Customer Ltd.");
        customer.setAddress("1 Main Street");
        customer.setCity("Toronto");
        customer.setProvince("ON");
        customer.setPostalCode("M5V 1A1");
        customer.setCountry("Canada");
        customer.setCompany(companyContext.requireCompany());
        customer = customerRepository.save(customer);

        Invoice invoice = new Invoice();
        invoice.setCompany(companyContext.requireCompany());
        invoice.setCustomer(customer);
        invoice.setInvoiceNumber("INV-CREDIT-1");
        invoice.setInvoiceDate(java.time.LocalDate.now().minusDays(1));
        invoice.setDueDate(java.time.LocalDate.now().plusDays(30));
        invoice.setStatus(InvoiceStatus.SENT);
        invoice.setSubtotal(new java.math.BigDecimal("100.00"));
        invoice.setGstAmount(new java.math.BigDecimal("5.00"));
        invoice.setHstAmount(java.math.BigDecimal.ZERO);
        invoice.setQstAmount(java.math.BigDecimal.ZERO);
        invoice.setTotalAmount(new java.math.BigDecimal("105.00"));
        LineItem line = new LineItem();
        line.setInvoice(invoice);
        line.setDescription("Consulting");
        line.setQuantity(java.math.BigDecimal.ONE);
        line.setUnitPrice(new java.math.BigDecimal("100.00"));
        invoice.setLineItems(java.util.List.of(line));
        invoice = invoiceRepository.save(invoice);

        mockMvc.perform(get("/invoices/{id}", invoice.getId())
                        .with(user("test-admin@example.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("title=\"Download PDF\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("title=\"Duplicate invoice\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("title=\"Cancel invoice\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("title=\"Record payment\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("title=\"Create credit note\"")));

        mockMvc.perform(get("/credit-notes/from-invoice/{invoiceId}/new", invoice.getId())
                        .with(user("test-admin@example.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Create a credit note")));

        mockMvc.perform(post("/credit-notes/from-invoice/{invoiceId}", invoice.getId())
                        .with(user("test-admin@example.test").roles("ADMIN"))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("reason", "Service adjustment")
                        .param("creditNoteDate", java.time.LocalDate.now().toString())
                        .param("descriptions", "Consulting adjustment")
                        .param("quantities", "0.25")
                        .param("unitPrices", "100.00"))
                .andExpect(status().is3xxRedirection());

        CreditNote note = creditNoteRepository
                .findAllByCompanyIdOrderByCreditNoteDateDescIdDesc(companyId)
                .stream().findFirst().orElseThrow();
        mockMvc.perform(get("/credit-notes")
                        .with(user("test-admin@example.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("credit-note-list-table")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("class=\"table-responsive\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(note.getCreditNoteNumber())));

        mockMvc.perform(get("/credit-notes/{id}", note.getId())
                        .with(user("test-admin@example.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(note.getCreditNoteNumber())))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Consulting adjustment")));
    }

    @Test
    @Transactional
    public void draftInvoiceShowsFinalAndDeleteActionsAndReusesDeletedNumber() throws Exception {
        Long companyId = companyContext.requireCompanyId();
        var company = companyContext.requireCompany();
        Customer customer = new Customer();
        customer.setName("Draft invoice customer");
        customer.setEmail("draft-invoice@example.test");
        customer.setBusinessName("Draft Invoice Customer Ltd.");
        customer.setAddress("2 Main Street");
        customer.setCity("Toronto");
        customer.setProvince("ON");
        customer.setPostalCode("M5V 1A1");
        customer.setCountry("Canada");
        customer.setCompany(company);
        customer = customerRepository.save(customer);

        Invoice previous = newDraftInvoice(company, customer, "INV-001");
        invoiceRepository.save(previous);
        Invoice draft = newDraftInvoice(company, customer, "INV-002");
        draft = invoiceRepository.save(draft);

        mockMvc.perform(get("/invoices/{id}", draft.getId())
                        .with(user("test-admin@example.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Final invoice?")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "posted to the ledger, and locked from edits. Its PDF is stored")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Issue final invoice")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("title=\"Download PDF\""))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("title=\"Edit\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("title=\"Delete\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("title=\"Duplicate invoice\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("title=\"Upload attachment\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("title=\"Email invoice\"")));

        mockMvc.perform(post("/invoices/{id}/delete", draft.getId())
                        .with(user("test-admin@example.test").roles("ADMIN"))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().is3xxRedirection());

        org.junit.jupiter.api.Assertions.assertTrue(
                invoiceRepository.findByCompanyIdAndInvoiceNumber(companyId, "INV-002").isEmpty());
        mockMvc.perform(get("/invoices/new")
                        .with(user("test-admin@example.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"INV-002\"")));

        invoiceRepository.saveAndFlush(newDraftInvoice(company, customer, "INV-002"));
        org.junit.jupiter.api.Assertions.assertTrue(
                invoiceRepository.findByCompanyIdAndInvoiceNumber(companyId, "INV-002").isPresent());
    }

    @Test
    @Transactional
    public void paidInvoiceShowsCreditNoteAction() throws Exception {
        var company = companyContext.requireCompany();
        Customer customer = new Customer();
        customer.setName("Paid invoice customer");
        customer.setEmail("paid-invoice@example.test");
        customer.setBusinessName("Paid Invoice Customer Ltd.");
        customer.setAddress("3 Main Street");
        customer.setCity("Toronto");
        customer.setProvince("ON");
        customer.setPostalCode("M5V 1A1");
        customer.setCountry("Canada");
        customer.setCompany(company);
        customer = customerRepository.save(customer);

        Invoice invoice = newDraftInvoice(company, customer, "INV-PAID-1");
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidAmount(new java.math.BigDecimal("100.00"));
        invoice = invoiceRepository.save(invoice);

        mockMvc.perform(get("/invoices/{id}", invoice.getId())
                        .with(user("test-admin@example.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "title=\"Create credit note\"")));
    }

    @Test
    @Transactional
    public void paidInvoiceCreditCanBeRefundedAndPostsCashOutflow() throws Exception {
        var company = companyContext.requireCompany();
        Customer customer = new Customer();
        customer.setName("Refund customer");
        customer.setEmail("credit-refund@example.test");
        customer.setBusinessName("Credit Refund Customer Ltd.");
        customer.setAddress("4 Main Street");
        customer.setCity("Toronto");
        customer.setProvince("ON");
        customer.setPostalCode("M5V 1A1");
        customer.setCountry("Canada");
        customer.setCompany(company);
        customer = customerRepository.save(customer);

        Invoice invoice = newDraftInvoice(company, customer, "INV-CREDIT-REFUND");
        invoice.setStatus(InvoiceStatus.CREDITED);
        invoice.setPaidAmount(new java.math.BigDecimal("100.00"));
        invoice = invoiceRepository.save(invoice);

        CreditNote note = new CreditNote();
        note.setCompany(company);
        note.setInvoice(invoice);
        note.setCreditNoteNumber("CN-REFUND-1");
        note.setCreditNoteDate(java.time.LocalDate.now());
        note.setReason("Returned service");
        note.setStatus(CreditNoteStatus.ISSUED);
        note.setSubtotal(new java.math.BigDecimal("25.00"));
        note.setGstAmount(java.math.BigDecimal.ZERO);
        note.setHstAmount(java.math.BigDecimal.ZERO);
        note.setQstAmount(java.math.BigDecimal.ZERO);
        note.setTotalAmount(new java.math.BigDecimal("25.00"));
        note = creditNoteRepository.save(note);

        ensureTestAccount(company.getId(), "1100", "Accounts receivable",
                AccountCategory.ACCOUNTS_RECEIVABLE);
        ChartOfAccount bankAccount = ensureTestAccount(company.getId(), "1088", "Refund test bank",
                AccountCategory.BANK);

        mockMvc.perform(get("/credit-notes/{id}", note.getId())
                        .with(user("test-admin@example.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Credit available to refund or apply")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "title=\"Record refund\"")));

        mockMvc.perform(post("/credit-notes/{id}/refund", note.getId())
                        .with(user("test-admin@example.test").roles("ADMIN"))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("amount", "25.00")
                        .param("refundDate", java.time.LocalDate.now().toString())
                        .param("bankAccountId", bankAccount.getId().toString())
                        .param("paymentMethod", PaymentMethod.BANK_TRANSFER.name())
                        .param("transactionId", "BANK-REFUND-25")
                        .param("reason", "Refund of issued credit"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/credit-notes/" + note.getId()));

        CreditNoteRefund refund = creditNoteRefundRepository
                .findByCompanyIdAndCreditNoteIdOrderByRefundDateDescIdDesc(company.getId(), note.getId())
                .stream().findFirst().orElseThrow();
        assertEquals(new java.math.BigDecimal("25.00"), refund.getAmount());
        assertTrue(generalJournalRepository.findByCompanyIdAndReference(
                company.getId(), "CREDIT-NOTE-REFUND-" + refund.getId()).isPresent());
        mockMvc.perform(get("/credit-notes/{id}", note.getId())
                        .with(user("test-admin@example.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("BANK-REFUND-25")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("25.00")));
    }

    @Test
    @Transactional
    public void paidInvoiceCreditCanBeAppliedToAnotherInvoiceForTheSameCustomer() throws Exception {
        var company = companyContext.requireCompany();
        Customer customer = new Customer();
        customer.setName("Credit allocation customer");
        customer.setEmail("credit-allocation@example.test");
        customer.setBusinessName("Credit Allocation Customer Ltd.");
        customer.setAddress("5 Main Street");
        customer.setCity("Toronto");
        customer.setProvince("ON");
        customer.setPostalCode("M5V 1A1");
        customer.setCountry("Canada");
        customer.setCompany(company);
        customer = customerRepository.save(customer);

        Invoice sourceInvoice = newDraftInvoice(company, customer, "INV-CREDIT-SOURCE");
        sourceInvoice.setStatus(InvoiceStatus.CREDITED);
        sourceInvoice.setPaidAmount(new java.math.BigDecimal("100.00"));
        sourceInvoice = invoiceRepository.save(sourceInvoice);
        Invoice targetInvoice = newDraftInvoice(company, customer, "INV-CREDIT-TARGET");
        targetInvoice.setStatus(InvoiceStatus.SENT);
        targetInvoice = invoiceRepository.save(targetInvoice);

        CreditNote note = new CreditNote();
        note.setCompany(company);
        note.setInvoice(sourceInvoice);
        note.setCreditNoteNumber("CN-ALLOC-1");
        note.setCreditNoteDate(java.time.LocalDate.now());
        note.setReason("Customer account credit");
        note.setStatus(CreditNoteStatus.ISSUED);
        note.setSubtotal(new java.math.BigDecimal("25.00"));
        note.setGstAmount(java.math.BigDecimal.ZERO);
        note.setHstAmount(java.math.BigDecimal.ZERO);
        note.setQstAmount(java.math.BigDecimal.ZERO);
        note.setTotalAmount(new java.math.BigDecimal("25.00"));
        note = creditNoteRepository.save(note);
        ensureTestAccount(company.getId(), "1089", "Allocation test bank", AccountCategory.BANK);

        mockMvc.perform(get("/credit-notes/{id}", note.getId())
                        .with(user("test-admin@example.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Apply credit to another invoice")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("INV-CREDIT-TARGET")));

        mockMvc.perform(post("/credit-notes/{id}/allocate", note.getId())
                        .with(user("test-admin@example.test").roles("ADMIN"))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("targetInvoiceId", targetInvoice.getId().toString())
                        .param("amount", "25.00")
                        .param("allocationDate", java.time.LocalDate.now().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/credit-notes/" + note.getId()));

        CreditNoteAllocation allocation = creditNoteAllocationRepository
                .findByCompanyIdAndCreditNoteIdOrderByAllocationDateDescIdDesc(company.getId(), note.getId())
                .stream().findFirst().orElseThrow();
        assertEquals(new java.math.BigDecimal("25.00"), allocation.getAmount());
        assertEquals(targetInvoice.getId(), allocation.getTargetInvoice().getId());
        Invoice updatedTarget = invoiceRepository.findByIdAndCompanyId(
                targetInvoice.getId(), company.getId()).orElseThrow();
        assertEquals(InvoiceStatus.PARTIALLY_PAID, updatedTarget.getStatus());

        mockMvc.perform(get("/credit-notes/{id}", note.getId())
                        .with(user("test-admin@example.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("INV-CREDIT-TARGET")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Credit application history")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Record refund"))));

        mockMvc.perform(post("/credit-notes/{id}/allocations/{allocationId}/reverse",
                        note.getId(), allocation.getId())
                        .with(user("test-admin@example.test").roles("ADMIN"))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("reversalDate", java.time.LocalDate.now().toString())
                        .param("reversalReason", "Applied to the wrong invoice"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/credit-notes/" + note.getId()));

        Invoice reopenedTarget = invoiceRepository.findByIdAndCompanyId(
                targetInvoice.getId(), company.getId()).orElseThrow();
        assertEquals(InvoiceStatus.SENT, reopenedTarget.getStatus());
        mockMvc.perform(get("/credit-notes/{id}", note.getId())
                        .with(user("test-admin@example.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Reversed")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Applied to the wrong invoice")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Record refund")));
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
    public void customerFormActionsAreIconsWithTooltips() throws Exception {
        mockMvc.perform(get("/customers/new")
                        .with(user("test-admin@example.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("bi-geo-alt")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("bi-credit-card")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("bi-percent")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("bi-journal-text")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("title=\"Save\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("title=\"Cancel\"")));
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
    public void companyAdministratorCanReachCompanyAdministrationButNotGlobalTools() throws Exception {
        UserAccount companyAdmin = createCompanyAdmin("limited-admin@example.test");

        mockMvc.perform(get("/database/backup")
                        .with(user(companyAdmin.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("SuperAdmin")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("href=\"/database/backup/export\""))));

        mockMvc.perform(get("/database/backup/export")
                        .with(user(companyAdmin.getEmail()).roles("ADMIN")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/database/schema")
                        .with(user(companyAdmin.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/reset").param("operation", "restart")
                        .with(user(companyAdmin.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("id=\"restart-from-scratch-option\""))));

        mockMvc.perform(get("/admin/users")
                        .with(user(companyAdmin.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/database/migrate")
                        .with(user(companyAdmin.getEmail()).roles("ADMIN")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/database/migration")
                        .with(user(companyAdmin.getEmail()).roles("ADMIN")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/database/query")
                        .with(user(companyAdmin.getEmail()).roles("ADMIN")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/monitoring")
                        .with(user(companyAdmin.getEmail()).roles("ADMIN")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/companies/new")
                        .with(user(companyAdmin.getEmail()).roles("ADMIN")))
                .andExpect(status().isForbidden());
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

    private UserAccount createCompanyAdmin(String email) {
        UserAccount account = new UserAccount();
        account.setCompany(companyContext.requireCompany());
        account.setFullName("Limited Company Administrator");
        account.setEmail(email);
        account.setPasswordHash("not-used-in-mock-authentication");
        account.setRole(UserRole.ADMIN);
        account.setEnabled(true);
        UserAccount saved = userAccountRepository.save(account);
        companyMembershipRepository.grant(
                saved.getId(), companyContext.requireCompanyId(), UserRole.ADMIN);
        return saved;
    }

    private Invoice newDraftInvoice(com.cogitosum.entity.Company company, Customer customer, String number) {
        Invoice invoice = new Invoice();
        invoice.setCompany(company);
        invoice.setCustomer(customer);
        invoice.setInvoiceNumber(number);
        invoice.setInvoiceDate(java.time.LocalDate.now());
        invoice.setDueDate(java.time.LocalDate.now().plusDays(30));
        invoice.setStatus(InvoiceStatus.DRAFT);
        invoice.setSubtotal(new java.math.BigDecimal("100.00"));
        invoice.setGstAmount(java.math.BigDecimal.ZERO);
        invoice.setHstAmount(java.math.BigDecimal.ZERO);
        invoice.setQstAmount(java.math.BigDecimal.ZERO);
        invoice.setTotalAmount(new java.math.BigDecimal("100.00"));
        return invoice;
    }

    private ChartOfAccount ensureTestAccount(Long companyId, String accountNumber, String accountName,
                                             AccountCategory category) {
        return chartOfAccountRepository.findByCompanyIdAndAccountNumber(companyId, accountNumber)
                .orElseGet(() -> {
                    ChartOfAccount account = new ChartOfAccount();
                    account.setAccountNumber(accountNumber);
                    account.setAccountName(accountName);
                    account.setAccountType(AccountType.ASSET);
                    account.setCategory(category);
                    account.setDescription(accountName);
                    account.setActive(true);
                    return chartOfAccountService.createAccount(account);
                });
    }
}
