package com.cogitosum.migration.v1;

import com.cogitosum.repository.BillRepository;
import com.cogitosum.repository.ChartOfAccountRepository;
import com.cogitosum.repository.CustomerRepository;
import com.cogitosum.repository.GeneralJournalRepository;
import com.cogitosum.repository.GeneralLedgerRepository;
import com.cogitosum.repository.InvoiceRepository;
import com.cogitosum.repository.JournalEntryRepository;
import com.cogitosum.repository.VendorRepository;
import com.cogitosum.repository.UserAccountRepository;
import com.cogitosum.entity.Bill;
import com.cogitosum.entity.BillLineItem;
import com.cogitosum.entity.Company;
import com.cogitosum.entity.Invoice;
import com.cogitosum.entity.LineItem;
import com.cogitosum.entity.UserAccount;
import com.cogitosum.entity.ChartOfAccount;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InitialDataMigrationServiceTest {

    @Mock
    private ChartOfAccountRepository accountRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private VendorRepository vendorRepository;
    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private BillRepository billRepository;
    @Mock
    private GeneralJournalRepository journalRepository;
    @Mock
    private JournalEntryRepository journalEntryRepository;
    @Mock
    private GeneralLedgerRepository ledgerRepository;
    @Mock
    private EntityManager entityManager;
    @Mock
    private UserAccountRepository userAccountRepository;

    private ObjectMapper objectMapper;
    private InitialDataMigrationService migrationService;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();

        migrationService = new InitialDataMigrationService(objectMapper, accountRepository, customerRepository,
            vendorRepository, invoiceRepository, billRepository, journalRepository, journalEntryRepository,
            ledgerRepository, entityManager, userAccountRepository);
    }

    @Test
    void previewThenConfirmedImportUsesMappedJpaEntities() throws Exception {
        stubValidImport();
        MigrationPreview preview = migrationService.preview(validPackage(), "admin@example.test");

        assertTrue(preview.isValid());
        assertEquals(2, preview.getRowCounts().get("accounts.csv"));
        assertEquals(2, preview.getRowCounts().get("invoice_lines.csv"));
        assertEquals(2, preview.getRowCounts().get("bill_lines.csv"));
        assertEquals(2, preview.getRowCounts().get("journal_lines.csv"));

        MigrationImportSummary summary =
            migrationService.confirmAndImport(preview.getConfirmationToken(), "admin@example.test");

        assertEquals(13, summary.totalRows());
        verify(accountRepository, org.mockito.Mockito.times(2)).save(any());
        verify(customerRepository).save(any());
        verify(vendorRepository).save(any());
        ArgumentCaptor<Invoice> invoiceCaptor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository).save(invoiceCaptor.capture());
        Invoice importedInvoice = invoiceCaptor.getValue();
        assertEquals(new java.math.BigDecimal("25.00"), importedInvoice.getOpeningPaidAmount());
        assertEquals(List.of("First consulting", "Second consulting"),
            importedInvoice.getLineItems().stream().map(LineItem::getDescription).toList());
        ArgumentCaptor<Bill> billCaptor = ArgumentCaptor.forClass(Bill.class);
        verify(billRepository).save(billCaptor.capture());
        assertEquals(List.of("First supplies", "Second supplies"),
            billCaptor.getValue().getLineItems().stream().map(BillLineItem::getDescription).toList());
        verify(journalRepository).save(any());
        verify(ledgerRepository, org.mockito.Mockito.times(2)).save(any());
        verify(ledgerRepository).saveAll(any());
        verify(entityManager).flush();
    }

    @Test
    void previewReportsFileAndRowForBrokenSourceReference() throws Exception {
        Map<String, String> files = validFiles();
        files.put("invoices.csv", files.get("invoices.csv").replace("customer-1", "missing-customer"));

        MigrationPreview preview = migrationService.preview(zip(files), "admin@example.test");

        assertFalse(preview.isValid());
        assertTrue(preview.getErrors().stream().anyMatch(error ->
            error.fileName().equals("invoices.csv")
                && error.rowNumber() == 2
                && error.message().contains("customer_source_id")));
    }

    @Test
    void previewRejectsNonemptyMigrationDomain() throws Exception {
        stubMigrationUser();
        when(accountRepository.findAllByCompanyIdOrderByAccountNumberAsc(1L)).thenReturn(List.of(new ChartOfAccount()));

        MigrationPreview preview = migrationService.preview(validPackage(), "admin@example.test");

        assertFalse(preview.isValid());
        assertTrue(preview.getErrors().stream().anyMatch(error -> error.fileName().equals("manifest.json")));
    }

    @Test
    void previewRejectsPaidInvoiceWhenPaidAmountDoesNotMatchTotal() throws Exception {
        Map<String, String> files = validFiles();
        files.put("invoices.csv", files.get("invoices.csv").replace(",PARTIALLY_PAID,", ",PAID,"));

        MigrationPreview preview = migrationService.preview(zip(files), "admin@example.test");

        assertFalse(preview.isValid());
        assertTrue(preview.getErrors().stream().anyMatch(error ->
            error.fileName().equals("invoices.csv") && error.message().contains("PAID document")));
    }

    @Test
    void previewRejectsPartiallyPaidBillWithoutPaidAmount() throws Exception {
        Map<String, String> files = validFiles();
        files.put("bills.csv", files.get("bills.csv").replace(",DRAFT,", ",PARTIALLY_PAID,"));

        MigrationPreview preview = migrationService.preview(zip(files), "admin@example.test");

        assertFalse(preview.isValid());
        assertTrue(preview.getErrors().stream().anyMatch(error ->
            error.fileName().equals("bills.csv") && error.message().contains("PARTIALLY_PAID document")));
    }

    @Test
    void previewParsesManifestWithRealJsonParser() throws Exception {
        Map<String, String> files = validFiles();
        files.put("manifest.json", "{\"format\":\"cogitosum-accounting-migration\",\"version\":2}");

        MigrationPreview preview = migrationService.preview(zip(files), "admin@example.test");

        assertFalse(preview.isValid());
        assertTrue(preview.getErrors().stream().anyMatch(error -> error.fileName().equals("manifest.json")));
    }

    private void stubValidImport() {
        when(accountRepository.save(ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(customerRepository.save(ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(vendorRepository.save(ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(invoiceRepository.save(ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(billRepository.save(ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(journalRepository.save(ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(ledgerRepository.save(ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountRepository.findAllByCompanyIdOrderByAccountNumberAsc(1L)).thenReturn(List.of());
        when(customerRepository.findAllByCompanyIdOrderByBusinessNameAsc(1L)).thenReturn(List.of());
        when(vendorRepository.findAllByCompanyIdOrderByBusinessNameAsc(1L)).thenReturn(List.of());
        when(invoiceRepository.findAllByCompanyIdOrderByInvoiceNumberDesc(1L)).thenReturn(List.of());
        when(billRepository.findAllByCompanyIdOrderByBillNumberDesc(1L)).thenReturn(List.of());
        when(journalRepository.findAllByCompanyId(1L)).thenReturn(List.of());
        when(ledgerRepository.findAllByCompanyIdOrderByAccountAccountNumberAsc(1L)).thenReturn(List.of());
        stubMigrationUser();
    }

    private void stubMigrationUser() {
        Company company = new Company();
        company.setId(1L);
        UserAccount user = new UserAccount();
        user.setCompany(company);
        user.setEnabled(true);
        when(userAccountRepository.findByEmail("admin@example.test")).thenReturn(Optional.of(user));
    }

    private byte[] validPackage() throws IOException {
        return zip(validFiles());
    }

    private Map<String, String> validFiles() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("manifest.json", "{\"format\":\"cogitosum-accounting-migration\",\"version\":1}");
        files.put("accounts.csv", """
            source_id,account_number,account_name,account_type,description,is_active
            account-cash,1000,Cash,ASSET,Cash account,true
            account-expense,5000,Office expense,EXPENSE,Office expense,true
            """);
        files.put("customers.csv", """
            source_id,name,email,business_name,address,city,province,postal_code,country,business_number,gst_number
            customer-1,Customer One,customer@example.test,Customer One Inc,1 Main Street,Toronto,ON,M1M 1M1,Canada,,
            """);
        files.put("vendors.csv", """
            source_id,name,email,business_name,address,city,province,postal_code,country,business_number,gst_number,qst_number
            vendor-1,Vendor One,vendor@example.test,Vendor One Inc,2 Main Street,Toronto,ON,M2M 2M2,Canada,,,
            """);
        files.put("invoices.csv", """
            source_id,invoice_number,customer_source_id,invoice_date,due_date,status,subtotal,gst_amount,hst_amount,qst_amount,total_amount,paid_amount,tax_regime,notes
            invoice-1,INV-1,customer-1,2026-01-01,2026-01-31,PARTIALLY_PAID,100.00,0.00,0.00,0.00,100.00,25.00,,
            """);
        files.put("invoice_lines.csv", """
            source_id,invoice_source_id,line_number,description,quantity,unit_price,total
            invoice-line-2,invoice-1,2,Second consulting,1.00,60.00,60.00
            invoice-line-1,invoice-1,1,First consulting,1.00,40.00,40.00
            """);
        files.put("bills.csv", """
            source_id,bill_number,vendor_source_id,bill_date,due_date,status,subtotal,gst_amount,hst_amount,qst_amount,total_amount,paid_amount,notes
            bill-1,BILL-1,vendor-1,2026-01-02,2026-02-01,DRAFT,100.00,0.00,0.00,0.00,100.00,0.00,
            """);
        files.put("bill_lines.csv", """
            source_id,bill_source_id,line_number,expense_account_source_id,description,quantity,unit_price,total
            bill-line-2,bill-1,2,account-expense,Second supplies,1.00,60.00,60.00
            bill-line-1,bill-1,1,account-expense,First supplies,1.00,40.00,40.00
            """);
        files.put("journal_entries.csv", """
            source_id,journal_number,journal_date,narrative,reference,status,posted_date,posted_by
            journal-1,JRN-1,2026-01-01,Opening entry,,POSTED,2026-01-01T09:00:00,admin
            """);
        files.put("journal_lines.csv", """
            source_id,journal_source_id,line_number,account_source_id,debit,credit,cleared,description
            journal-line-1,journal-1,1,account-cash,100.00,0.00,false,Opening debit
            journal-line-2,journal-1,2,account-expense,0.00,100.00,false,Opening credit
            """);
        return files;
    }

    private byte[] zip(Map<String, String> files) throws IOException {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> entry : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            zip.finish();
            return bytes.toByteArray();
        }
    }
}
