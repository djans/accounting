package com.cogitosum.web;

import com.cogitosum.service.DocumentAttachmentService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentAttachmentWebControllerTest {

    @Test
    void downloadUsesAttachmentAndPrivateSecurityHeaders() {
        DocumentAttachmentService attachments = mock(DocumentAttachmentService.class);
        when(attachments.invoiceAttachment(7L, 11L)).thenReturn(Optional.of(
                new DocumentAttachmentService.AttachmentDownload("invoice.pdf", "application/pdf",
                        new byte[] {'%', 'P', 'D', 'F'})));

        ResponseEntity<byte[]> response = new DocumentAttachmentWebController(attachments)
                .downloadInvoice(7L, 11L);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("nosniff", response.getHeaders().getFirst("X-Content-Type-Options"));
        assertTrue(response.getHeaders().getCacheControl().contains("no-store"));
        assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).contains("attachment"));
    }
}
