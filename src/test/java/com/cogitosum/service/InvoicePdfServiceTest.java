package com.cogitosum.service;

import com.cogitosum.entity.Customer;
import com.cogitosum.entity.Invoice;
import com.cogitosum.entity.LineItem;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvoicePdfServiceTest {

    @Test
    void createsPdfWithSanitizedFilenameAndUntrustedText() {
        Invoice invoice = invoice();
        invoice.setInvoiceNumber("../../INV\r\n101");
        invoice.getLineItems().get(0).setDescription("Service <script>alert(1)</script>");

        byte[] pdf = new InvoicePdfService(null, null).createInvoicePdf(invoice);

        assertTrue(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).startsWith("%PDF-"));
        assertEquals("invoice-INV-101.pdf", InvoicePdfService.filenameFor(invoice));
        assertEquals("te xt", InvoicePdfService.safeText("te\u0000xt"));
    }

    private Invoice invoice() {
        Customer customer = new Customer();
        customer.setBusinessName("Acme");
        customer.setName("A. Customer");
        customer.setEmail("customer@example.test");
        customer.setAddress("1 Main Street");
        customer.setCity("Montreal");
        customer.setProvince("QC");
        customer.setPostalCode("H1H 1H1");

        LineItem item = new LineItem();
        item.setDescription("Service");
        item.setQuantity(BigDecimal.ONE);
        item.setUnitPrice(new BigDecimal("100.00"));
        item.setTotal(new BigDecimal("100.00"));

        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("INV-101");
        invoice.setCustomer(customer);
        invoice.setInvoiceDate(LocalDate.of(2026, 1, 1));
        invoice.setDueDate(LocalDate.of(2026, 1, 31));
        invoice.setLineItems(List.of(item));
        invoice.setSubtotal(new BigDecimal("100.00"));
        invoice.setGstAmount(new BigDecimal("5.00"));
        invoice.setHstAmount(BigDecimal.ZERO);
        invoice.setQstAmount(BigDecimal.ZERO);
        invoice.setTotalAmount(new BigDecimal("105.00"));
        return invoice;
    }
}
