package com.cogitosum.service;

import com.cogitosum.entity.Customer;
import com.cogitosum.entity.Company;
import com.cogitosum.entity.Invoice;
import com.cogitosum.entity.InvoiceFinalPdf;
import com.cogitosum.entity.LineItem;
import com.cogitosum.repository.InvoiceFinalPdfRepository;
import com.cogitosum.repository.InvoiceRepository;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoicePdfServiceTest {

    @Mock private LibreOfficePdfConverter pdfConverter;
    @Mock private InvoiceTemplateService invoiceTemplateService;
    @Mock private InvoiceFinalPdfRepository finalPdfRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private CurrentCompanyContext companyContext;

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
                invoiceRepository, companyContext, new InvoiceDocxTemplateRenderer(), pdfConverter,
                invoiceTemplateService, finalPdfRepository);

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

    @Test
    void returnsStoredFinalPdfInsteadOfRenderingWithTheRequestedTemplate() {
        byte[] finalPdf = "%PDF-final".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        Invoice invoice = invoice();
        invoice.setId(24L);
        when(companyContext.requireCompanyId()).thenReturn(7L);
        when(invoiceRepository.findByIdAndCompanyId(24L, 7L)).thenReturn(java.util.Optional.of(invoice));
        when(finalPdfRepository.findById(24L))
                .thenReturn(java.util.Optional.of(new InvoiceFinalPdf(24L, finalPdf)));
        InvoicePdfService service = new InvoicePdfService(
                invoiceRepository, companyContext, new InvoiceDocxTemplateRenderer(), pdfConverter,
                invoiceTemplateService, finalPdfRepository);

        InvoicePdfService.InvoicePdf downloaded = service.downloadInvoice(24L, "custom-fr").orElseThrow();

        assertArrayEquals(finalPdf, downloaded.content());
        assertEquals("invoice-INV-101.pdf", downloaded.filename());
        org.mockito.Mockito.verifyNoInteractions(pdfConverter, invoiceTemplateService);
    }

    @Test
    void insertsCompanyLogoAtTheLogoPlaceholder() throws Exception {
        ByteArrayOutputStream templateBytes = new ByteArrayOutputStream();
        try (XWPFDocument template = new XWPFDocument()) {
            XWPFParagraph paragraph = template.createParagraph();
            XWPFRun run = paragraph.createRun();
            run.setText("{{company.logo}}");
            template.write(templateBytes);
        }

        Company company = new Company();
        company.setLogoData(pngLogo());
        company.setLogoContentType("image/png");
        Invoice invoice = new Invoice();
        invoice.setCompany(company);
        invoice.setLineItems(List.of());
        InvoiceDocxTemplateRenderer renderer = new InvoiceDocxTemplateRenderer(
                new ByteArrayResource(templateBytes.toByteArray()));

        byte[] rendered = renderer.render(invoice, java.util.Locale.CANADA);

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(rendered))) {
            assertEquals(1, document.getAllPictures().size());
            assertFalse(document.getParagraphs().getFirst().getText().contains("{{company.logo}}"));
        }
    }

    @Test
    void insertsCompanyLogoWhenWordSplitsThePlaceholderAcrossRuns() throws Exception {
        ByteArrayOutputStream templateBytes = new ByteArrayOutputStream();
        try (XWPFDocument template = new XWPFDocument()) {
            XWPFParagraph paragraph = template.createParagraph();
            paragraph.createRun().setText("{{company.");
            paragraph.createRun().setText("logo}}");
            template.write(templateBytes);
        }

        Company company = new Company();
        company.setLogoData(pngLogo());
        company.setLogoContentType("image/png");
        Invoice invoice = new Invoice();
        invoice.setCompany(company);
        invoice.setLineItems(List.of());
        InvoiceDocxTemplateRenderer renderer = new InvoiceDocxTemplateRenderer(
                new ByteArrayResource(templateBytes.toByteArray()));

        byte[] rendered = renderer.render(invoice, java.util.Locale.CANADA);

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(rendered))) {
            assertEquals(1, document.getAllPictures().size());
            assertFalse(document.getParagraphs().getFirst().getText().contains("{{company.logo}}"));
        }
    }

    @Test
    void insertsCompanyLogoAtItsPositionWithinOtherParagraphText() throws Exception {
        ByteArrayOutputStream templateBytes = new ByteArrayOutputStream();
        try (XWPFDocument template = new XWPFDocument()) {
            template.createParagraph().createRun().setText("Before {{company.logo}} after");
            template.write(templateBytes);
        }

        Company company = new Company();
        company.setLogoData(pngLogo());
        company.setLogoContentType("image/png");
        Invoice invoice = new Invoice();
        invoice.setCompany(company);
        invoice.setLineItems(List.of());
        InvoiceDocxTemplateRenderer renderer = new InvoiceDocxTemplateRenderer(
                new ByteArrayResource(templateBytes.toByteArray()));

        byte[] rendered = renderer.render(invoice, java.util.Locale.CANADA);

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(rendered))) {
            List<XWPFRun> runs = document.getParagraphs().getFirst().getRuns();
            assertEquals("Before ", runs.getFirst().text());
            assertEquals(1, runs.get(1).getEmbeddedPictures().size());
            assertEquals(" after", runs.get(2).text());
        }
    }

    @Test
    void insertsUploadedLogoIntoTheBuiltInTemplate() throws Exception {
        Invoice invoice = invoice();
        Company company = new Company();
        company.setLogoData(pngLogo());
        company.setLogoContentType("image/png");
        invoice.setCompany(company);
        InvoiceDocxTemplateRenderer renderer = new InvoiceDocxTemplateRenderer();

        byte[] rendered = renderer.render(invoice, java.util.Locale.CANADA);

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(rendered))) {
            assertEquals(1, document.getAllPictures().size());
        }
    }

    @Test
    void insertsUploadedLogoIntoTheFrenchExampleTemplateTable() throws Exception {
        Invoice invoice = invoice();
        Company company = new Company();
        company.setLogoData(pngLogo());
        company.setLogoContentType("image/png");
        invoice.setCompany(company);
        byte[] template = Files.readAllBytes(Path.of("docs", "invoice-template-example_fr.docx"));
        InvoiceDocxTemplateRenderer renderer = new InvoiceDocxTemplateRenderer(
                new ByteArrayResource(template));

        byte[] rendered = renderer.render(invoice, java.util.Locale.CANADA_FRENCH);

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(rendered))) {
            assertEquals(1, document.getAllPictures().size());
            assertTrue(document.getTables().stream()
                    .flatMap(table -> table.getRows().stream())
                    .flatMap(row -> row.getTableCells().stream())
                    .flatMap(cell -> cell.getParagraphs().stream())
                    .flatMap(paragraph -> paragraph.getRuns().stream())
                    .anyMatch(run -> !run.getEmbeddedPictures().isEmpty()));
        }
    }

    @Test
    void rendersCompanyAndCustomerAddressesAsTwoLinesAndKeepsLegacyTokens() throws Exception {
        ByteArrayOutputStream templateBytes = new ByteArrayOutputStream();
        try (XWPFDocument template = new XWPFDocument()) {
            template.createParagraph().createRun().setText("{{company.address_1}}");
            template.createParagraph().createRun().setText("{{company.address_2}}");
            template.createParagraph().createRun().setText("{{customer.address_1}}");
            XWPFParagraph splitToken = template.createParagraph();
            splitToken.createRun().setText("{{customer.address_");
            splitToken.createRun().setText("2}}");
            template.createParagraph().createRun().setText("{{company.address}}");
            template.createParagraph().createRun().setText("{{customer.address}}");
            template.write(templateBytes);
        }

        Company company = new Company();
        company.setAddress("10 Main Street");
        company.setPostalCode("H1A 1A1");
        company.setCity("Montreal");
        company.setProvince("QC");
        company.setCountry("Canada");
        Customer customer = new Customer();
        customer.setAddress("20 Oak Avenue");
        customer.setPostalCode("K1A 0B1");
        customer.setCity("Ottawa");
        customer.setProvince("ON");
        customer.setCountry("Canada");
        Invoice invoice = new Invoice();
        invoice.setCompany(company);
        invoice.setCustomer(customer);
        invoice.setLineItems(List.of());
        InvoiceDocxTemplateRenderer renderer = new InvoiceDocxTemplateRenderer(
                new ByteArrayResource(templateBytes.toByteArray()));

        byte[] rendered = renderer.render(invoice, java.util.Locale.CANADA);

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(rendered))) {
            List<String> paragraphs = document.getParagraphs().stream()
                    .map(XWPFParagraph::getText)
                    .toList();
            assertEquals(List.of(
                    "10 Main Street",
                    "H1A 1A1, Montreal, QC, Canada",
                    "20 Oak Avenue",
                    "K1A 0B1, Ottawa, ON, Canada",
                    "10 Main Street, Montreal, QC, H1A 1A1, Canada",
                    "20 Oak Avenue, Ottawa, ON, K1A 0B1, Canada"), paragraphs);
        }
    }

    private byte[] pngLogo() throws Exception {
        BufferedImage image = new BufferedImage(2, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
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
