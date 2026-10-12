package com.cogitosum.web;

import com.cogitosum.entity.InvoiceStatus;
import com.cogitosum.entity.Invoice;
import com.cogitosum.entity.Customer;
import com.cogitosum.service.CustomerService;
import com.cogitosum.service.InvoicePdfBundleService;
import com.cogitosum.service.InvoicePdfService;
import com.cogitosum.service.InvoiceService;
import com.cogitosum.service.InvoiceTemplateService;
import com.cogitosum.service.TaxFilingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.List;
import java.util.Optional;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InvoiceWebControllerTest {

    private InvoiceService invoiceService;
    private CustomerService customerService;
    private TaxFilingService taxFilingService;
    private InvoicePdfService invoicePdfService;
    private InvoicePdfBundleService invoicePdfBundleService;
    private InvoiceTemplateService invoiceTemplateService;
    private InvoiceWebController controller;

    @BeforeEach
    void setUp() {
        invoiceService = mock(InvoiceService.class);
        customerService = mock(CustomerService.class);
        taxFilingService = mock(TaxFilingService.class);
        invoicePdfService = mock(InvoicePdfService.class);
        invoicePdfBundleService = mock(InvoicePdfBundleService.class);
        invoiceTemplateService = mock(InvoiceTemplateService.class);
        when(taxFilingService.getDateFilterPeriods(any(LocalDate.class))).thenReturn(
                new TaxFilingService.FilterPeriodRanges(
                        new TaxFilingService.DateRange("CRA", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31)),
                        new TaxFilingService.DateRange("CRA", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30))));
        controller = new InvoiceWebController();
        ReflectionTestUtils.setField(controller, "invoiceService", invoiceService);
        ReflectionTestUtils.setField(controller, "customerService", customerService);
        ReflectionTestUtils.setField(controller, "taxFilingService", taxFilingService);
        ReflectionTestUtils.setField(controller, "invoicePdfService", invoicePdfService);
        ReflectionTestUtils.setField(controller, "invoicePdfBundleService", invoicePdfBundleService);
        ReflectionTestUtils.setField(controller, "invoiceTemplateService", invoiceTemplateService);
    }

    @Test
    void listsAllInvoicesWhenNoStatusIsSelected() {
        when(invoiceService.getAllInvoices()).thenReturn(List.of());
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.list(null, model);

        assertEquals("invoices/list", view);
        assertEquals(InvoiceStatus.values().length, ((InvoiceStatus[]) model.get("invoiceStatuses")).length);
        assertNull(model.get("selectedStatus"));
        org.junit.jupiter.api.Assertions.assertNotNull(model.get("customers"));
        org.junit.jupiter.api.Assertions.assertNotNull(model.get("dateFilterPeriods"));
        verify(invoiceService).getAllInvoices();
        verify(invoiceService, never()).getInvoicesByStatus(any());
    }

    @Test
    void filtersInvoicesBySelectedStatus() {
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.list(InvoiceStatus.PAID, model);

        assertEquals("invoices/list", view);
        assertEquals(InvoiceStatus.PAID, model.get("selectedStatus"));
        verify(invoiceService).getInvoicesByStatus(InvoiceStatus.PAID);
        verify(invoiceService, never()).getAllInvoices();
    }

    @Test
    void showsTemplateSelectionBeforeDownloadingAnInvoice() {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("INV-4");
        when(invoiceService.getInvoiceById(4L)).thenReturn(Optional.of(invoice));
        List<InvoiceTemplateService.TemplateOption> options = List.of(
                new InvoiceTemplateService.TemplateOption("standard", "Standard invoice", "en", "default.docx", true));
        when(invoiceTemplateService.getCurrentCompanyOptions()).thenReturn(options);
        when(invoiceTemplateService.suggestedTemplateId(options, org.springframework.context.i18n.LocaleContextHolder.getLocale()))
                .thenReturn("standard");
        ExtendedModelMap model = new ExtendedModelMap();

        Object result = controller.selectTemplateForPdf(4L, model);

        assertEquals("invoices/pdf-template-choice", result);
        assertEquals(Boolean.FALSE, model.get("bulkDownload"));
        assertEquals(4L, model.get("invoiceId"));
        assertEquals(options, model.get("templates"));
    }

    @Test
    void downloadsTheStoredFinalPdfWithoutShowingTemplateChoice() {
        byte[] pdf = "%PDF-final".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        when(invoiceService.getInvoiceById(4L)).thenReturn(Optional.of(new Invoice()));
        when(invoicePdfService.downloadStoredInvoice(4L))
                .thenReturn(Optional.of(new InvoicePdfService.InvoicePdf(pdf, "invoice-4.pdf")));

        Object result = controller.selectTemplateForPdf(4L, new ExtendedModelMap());

        org.springframework.http.ResponseEntity<?> response =
                (org.springframework.http.ResponseEntity<?>) result;
        assertEquals(org.springframework.http.MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
        org.junit.jupiter.api.Assertions.assertArrayEquals(pdf, (byte[]) response.getBody());
        verify(invoiceTemplateService, never()).getCurrentCompanyOptions();
    }

    @Test
    void createsFinalInvoiceUsingTheSelectedPdfTemplate() {
        Customer customer = new Customer();
        customer.setId(5L);
        Invoice saved = new Invoice();
        saved.setId(15L);
        saved.setInvoiceNumber("INV-15");
        when(customerService.getCustomerById(5L)).thenReturn(Optional.of(customer));
        when(invoiceService.createInvoice(any(Invoice.class), eq("QC"), eq(true), eq("french")))
                .thenReturn(saved);
        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

        String result = controller.create(5L, "QC", null, "INV-15",
                LocalDate.of(2026, 10, 12), LocalDate.of(2026, 11, 11),
                List.of("Consulting"), List.of(java.math.BigDecimal.ONE),
                List.of(new java.math.BigDecimal("100.00")), true, "french", attributes);

        assertEquals("redirect:/invoices/15", result);
        verify(invoiceService).createInvoice(any(Invoice.class), eq("QC"), eq(true), eq("french"));
    }

    @Test
    void selectedTemplateIsPassedToTheInvoicePdfRenderer() {
        byte[] pdf = "%PDF-sample".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        when(invoicePdfService.downloadInvoice(4L, "french"))
                .thenReturn(Optional.of(new InvoicePdfService.InvoicePdf(pdf, "invoice-4.pdf")));

        Object result = controller.pdf(4L, "french");

        org.springframework.http.ResponseEntity<?> response =
                (org.springframework.http.ResponseEntity<?>) result;
        assertEquals(org.springframework.http.MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
        verify(invoicePdfService).downloadInvoice(4L, "french");
    }

    @Test
    void showsTemplateSelectionForBulkInvoiceDownloads() {
        when(invoiceService.getInvoiceById(4L)).thenReturn(Optional.of(new Invoice()));
        List<InvoiceTemplateService.TemplateOption> options = List.of(
                new InvoiceTemplateService.TemplateOption("standard", "Standard invoice", "en", "default.docx", true));
        when(invoiceTemplateService.getCurrentCompanyOptions()).thenReturn(options);
        when(invoiceTemplateService.suggestedTemplateId(options, org.springframework.context.i18n.LocaleContextHolder.getLocale()))
                .thenReturn("standard");
        ExtendedModelMap model = new ExtendedModelMap();

        String result = controller.selectTemplateForPdfs(List.of(4L), model);

        assertEquals("invoices/pdf-template-choice", result);
        assertEquals(Boolean.TRUE, model.get("bulkDownload"));
        assertEquals(List.of(4L), model.get("invoiceIds"));
    }
}
