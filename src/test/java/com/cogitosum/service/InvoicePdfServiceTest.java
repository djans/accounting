package com.cogitosum.service;

import com.cogitosum.entity.Customer;
import com.cogitosum.entity.Invoice;
import com.cogitosum.entity.LineItem;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoicePdfServiceTest {

    @Mock private LibreOfficePdfConverter pdfConverter;

    @Test
    void fillsDocxTemplateBeforeConvertingInvoiceToPdf() throws Exception {
        Invoice invoice = invoice();
        invoice.setInvoiceNumber("../../INV\r\n101");
        invoice.getLineItems().get(0).setDescription("Service <script>alert(1)</script>");
        LineItem secondItem = new LineItem();
        secondItem.setDescription("Second service");
        secondItem.setQuantity(new BigDecimal("2"));
        secondItem.setUnitPrice(new BigDecimal("15.00"));
        secondItem.setTotal(new BigDecimal("30.00"));
        invoice.setLineItems(List.of(invoice.getLineItems().getFirst(), secondItem));
        byte[] expectedPdf = "%PDF-sample".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        when(pdfConverter.convertToPdf(any())).thenReturn(expectedPdf);
        InvoicePdfService service = new InvoicePdfService(
                null, null, new InvoiceDocxTemplateRenderer(), pdfConverter);

        byte[] pdf = service.createInvoicePdf(invoice);

        assertTrue(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).startsWith("%PDF-"));
        assertEquals("invoice-INV-101.pdf", InvoicePdfService.filenameFor(invoice));
        assertEquals("te xt", InvoicePdfService.safeText("te\u0000xt"));

        ArgumentCaptor<byte[]> docxCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(pdfConverter).convertToPdf(docxCaptor.capture());
        try (XWPFDocument rendered = new XWPFDocument(new ByteArrayInputStream(docxCaptor.getValue()))) {
            String text = rendered.getTables().stream()
                    .flatMap(table -> table.getRows().stream())
                    .flatMap(row -> row.getTableCells().stream())
                    .map(cell -> cell.getText())
                    .reduce("", (left, right) -> left + "\n" + right);
            assertTrue(text.contains("INV"));
            assertTrue(text.contains("Acme"));
            assertTrue(text.contains("Service <script>alert(1)</script>"));
            assertTrue(text.contains("Second service"));
            assertFalse(text.contains("{{"));
            assertEquals(3, rendered.getTables().get(2).getRows().size());
        }
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
