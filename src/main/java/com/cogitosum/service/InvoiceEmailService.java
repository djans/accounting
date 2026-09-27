package com.cogitosum.service;

import com.cogitosum.entity.Invoice;
import com.cogitosum.entity.InvoiceStatus;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class InvoiceEmailService {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final InvoiceService invoiceService;
    private final InvoicePdfService invoicePdfService;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final boolean enabled;
    private final String smtpHost;
    private final String fromAddress;

    public InvoiceEmailService(InvoiceService invoiceService,
                               InvoicePdfService invoicePdfService,
                               ObjectProvider<JavaMailSender> mailSenderProvider,
                               @Value("${app.invoice-mail.enabled:false}") boolean enabled,
                               @Value("${spring.mail.host:}") String smtpHost,
                               @Value("${app.invoice-mail.from:}") String fromAddress) {
        this.invoiceService = invoiceService;
        this.invoicePdfService = invoicePdfService;
        this.mailSenderProvider = mailSenderProvider;
        this.enabled = enabled;
        this.smtpHost = smtpHost;
        this.fromAddress = fromAddress;
    }

    /**
     * SMTP is deliberately invoked before the invoice workflow changes. A failed
     * handoff leaves a draft unchanged and unposted.
     */
    public Invoice emailInvoice(Long invoiceId, String requestedRecipient) {
        JavaMailSender mailSender = configuredMailSender();
        Invoice invoice = invoiceService.getInvoiceById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new InvoiceDeliveryException("Only draft invoices can be emailed");
        }
        String recipient = recipient(requestedRecipient, invoice);
        InvoicePdfService.InvoicePdf pdf = invoicePdfService.downloadInvoice(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));

        try {
            var message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(fromAddress).toUnicodeString());
            helper.setTo(recipient);
            helper.setSubject("Invoice " + InvoicePdfService.safeText(invoice.getInvoiceNumber()));
            helper.setText("Please find invoice " + InvoicePdfService.safeText(invoice.getInvoiceNumber())
                    + " attached.", false);
            helper.addAttachment(pdf.filename(), new ByteArrayResource(pdf.content()), "application/pdf");
            mailSender.send(message);
        } catch (MessagingException | MailException e) {
            throw new InvoiceDeliveryException(
                    "Invoice email could not be handed to SMTP; the invoice remains draft and unposted.", e);
        }

        return invoiceService.markInvoiceAsSent(invoiceId);
    }

    private JavaMailSender configuredMailSender() {
        if (!enabled) {
            throw new InvoiceDeliveryException("Invoice email is disabled. Set APP_INVOICE_MAIL_ENABLED=true to enable it.");
        }
        if (smtpHost == null || smtpHost.isBlank() || fromAddress == null || fromAddress.isBlank()) {
            throw new InvoiceDeliveryException(
                    "Invoice email is not configured. Set SMTP_HOST and INVOICE_MAIL_FROM; SMTP credentials stay in runtime configuration.");
        }
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            throw new InvoiceDeliveryException("Invoice email is not configured because no SMTP mail sender is available.");
        }
        return sender;
    }

    private String recipient(String requestedRecipient, Invoice invoice) {
        String recipient = requestedRecipient == null || requestedRecipient.isBlank()
                ? invoice.getCustomer().getEmail() : requestedRecipient.trim();
        if (!EMAIL.matcher(recipient).matches()) {
            throw new InvoiceDeliveryException("A valid recipient email address is required");
        }
        return recipient;
    }
}
