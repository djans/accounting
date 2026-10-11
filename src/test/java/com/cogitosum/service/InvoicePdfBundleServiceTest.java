package com.cogitosum.service;

import com.cogitosum.entity.DocumentAttachment;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InvoicePdfBundleServiceTest {

    private final InvoicePdfService invoicePdfService = mock(InvoicePdfService.class);
    private final DocumentAttachmentService attachmentService = mock(DocumentAttachmentService.class);
    private final InvoicePdfBundleService service =
            new InvoicePdfBundleService(invoicePdfService, attachmentService);

    @Test
    void returnsASingleGeneratedInvoiceAsPdf() {
        byte[] pdf = "%PDF-invoice".getBytes(StandardCharsets.US_ASCII);
        when(invoicePdfService.downloadInvoice(1L))
                .thenReturn(Optional.of(new InvoicePdfService.InvoicePdf(pdf, "invoice-INV-1.pdf")));
        when(attachmentService.listInvoiceAttachments(1L)).thenReturn(List.of());

        InvoicePdfBundleService.Download download = service.download(List.of(1L));

        assertEquals("application/pdf", download.contentType());
        assertEquals("invoice-INV-1.pdf", download.filename());
        assertArrayEquals(pdf, download.content());
    }

    @Test
    void packagesGeneratedAndAttachedPdfsForSeveralInvoicesInZip() throws Exception {
        when(invoicePdfService.downloadInvoice(1L)).thenReturn(Optional.of(
                new InvoicePdfService.InvoicePdf(new byte[]{1}, "invoice-INV-1.pdf")));
        when(invoicePdfService.downloadInvoice(2L)).thenReturn(Optional.of(
                new InvoicePdfService.InvoicePdf(new byte[]{2}, "invoice-INV-2.pdf")));
        DocumentAttachment attachment = new DocumentAttachment();
        attachment.setId(9L);
        attachment.setContentType("application/pdf");
        attachment.setFilename("support.pdf");
        when(attachmentService.listInvoiceAttachments(1L)).thenReturn(List.of(attachment));
        when(attachmentService.listInvoiceAttachments(2L)).thenReturn(List.of());
        when(attachmentService.invoiceAttachment(1L, 9L)).thenReturn(Optional.of(
                new DocumentAttachmentService.AttachmentDownload("support.pdf", "application/pdf", new byte[]{3})));

        InvoicePdfBundleService.Download download = service.download(List.of(1L, 2L));

        assertEquals("application/zip", download.contentType());
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(download.content()))) {
            assertEquals("invoice-INV-1.pdf", zip.getNextEntry().getName());
            assertEquals(1, zip.readAllBytes().length);
            assertEquals("invoice-INV-1/support.pdf", zip.getNextEntry().getName());
            assertEquals(1, zip.readAllBytes().length);
            assertEquals("invoice-INV-2.pdf", zip.getNextEntry().getName());
            assertEquals(1, zip.readAllBytes().length);
        }
    }

    @Test
    void rejectsInvoicesOutsideTheCurrentCompany() {
        when(invoicePdfService.downloadInvoice(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.download(List.of(99L)));
    }
}
