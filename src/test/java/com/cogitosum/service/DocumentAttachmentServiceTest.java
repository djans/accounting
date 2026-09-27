package com.cogitosum.service;

import com.cogitosum.repository.BillRepository;
import com.cogitosum.repository.DocumentAttachmentContentRepository;
import com.cogitosum.repository.DocumentAttachmentRepository;
import com.cogitosum.repository.InvoiceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class DocumentAttachmentServiceTest {

    @Test
    void rejectsDeclaredContentTypeThatDoesNotMatchSignatureBeforeAnyMutation() {
        DocumentAttachmentService service = service();
        MockMultipartFile fakePdf = new MockMultipartFile("file", "statement.pdf", "application/pdf",
                new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});

        assertThrows(IllegalArgumentException.class, () -> service.addInvoiceAttachment(1L, fakePdf));
    }

    @Test
    void normalizesPathAndForcesTheDetectedExtension() {
        assertEquals("invoice_.jpg", DocumentAttachmentService.normalizedFilename(
                "..\\invoices\\invoice?.exe", ".jpg"));
    }

    private DocumentAttachmentService service() {
        return new DocumentAttachmentService(
                mock(DocumentAttachmentRepository.class),
                mock(DocumentAttachmentContentRepository.class),
                mock(InvoiceRepository.class),
                mock(BillRepository.class),
                mock(CurrentCompanyContext.class),
                10_485_760L);
    }
}
