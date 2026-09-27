package com.cogitosum.service;

import com.cogitosum.entity.Invoice;
import com.cogitosum.entity.InvoiceStatus;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvoiceEmailServiceTest {

    @Test
    void disabledMailDoesNotLoadOrMutateTheDraft() {
        InvoiceService invoices = mock(InvoiceService.class);
        InvoiceEmailService service = new InvoiceEmailService(invoices, mock(InvoicePdfService.class),
                null, false, "smtp.example.test", "accounts@example.test");

        assertThrows(InvoiceDeliveryException.class, () -> service.emailInvoice(8L, "customer@example.test"));

        verify(invoices, never()).getInvoiceById(any());
        verify(invoices, never()).markInvoiceAsSent(any());
    }

    @Test
    void smtpFailureLeavesDraftUnchangedAndUnposted() {
        InvoiceService invoices = mock(InvoiceService.class);
        InvoicePdfService pdfs = mock(InvoicePdfService.class);
        JavaMailSender sender = mock(JavaMailSender.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("INV-8");
        invoice.setStatus(InvoiceStatus.DRAFT);
        com.cogitosum.entity.Customer customer = new com.cogitosum.entity.Customer();
        customer.setEmail("customer@example.test");
        invoice.setCustomer(customer);

        when(provider.getIfAvailable()).thenReturn(sender);
        when(invoices.getInvoiceById(8L)).thenReturn(Optional.of(invoice));
        when(pdfs.downloadInvoice(8L)).thenReturn(Optional.of(
                new InvoicePdfService.InvoicePdf(new byte[] {'%', 'P', 'D', 'F'}, "invoice-INV-8.pdf")));
        when(sender.createMimeMessage()).thenReturn(
                new jakarta.mail.internet.MimeMessage(jakarta.mail.Session.getInstance(new java.util.Properties())));
        org.mockito.Mockito.doThrow(new MailSendException("SMTP unavailable")).when(sender)
                .send(any(jakarta.mail.internet.MimeMessage.class));

        InvoiceEmailService service = new InvoiceEmailService(invoices, pdfs, provider,
                true, "smtp.example.test", "accounts@example.test");

        assertThrows(InvoiceDeliveryException.class, () -> service.emailInvoice(8L, null));

        verify(invoices, never()).markInvoiceAsSent(any());
    }
}
