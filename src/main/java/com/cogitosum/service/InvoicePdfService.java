package com.cogitosum.service;

import com.cogitosum.entity.Invoice;
import com.cogitosum.repository.InvoiceRepository;
import java.text.Normalizer;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.i18n.LocaleContextHolder;

@Service
public class InvoicePdfService {

    private final InvoiceRepository invoiceRepository;
    private final CurrentCompanyContext companyContext;
    private final InvoiceDocxTemplateRenderer templateRenderer;
    private final LibreOfficePdfConverter pdfConverter;

    public InvoicePdfService(InvoiceRepository invoiceRepository, CurrentCompanyContext companyContext,
                             InvoiceDocxTemplateRenderer templateRenderer, LibreOfficePdfConverter pdfConverter) {
        this.invoiceRepository = invoiceRepository;
        this.companyContext = companyContext;
        this.templateRenderer = templateRenderer;
        this.pdfConverter = pdfConverter;
    }

    @Transactional(readOnly = true)
    public Optional<InvoicePdf> downloadInvoice(Long invoiceId) {
        return invoiceRepository.findByIdAndCompanyId(invoiceId, companyContext.requireCompanyId())
                .map(invoice -> new InvoicePdf(createInvoicePdf(invoice), filenameFor(invoice)));
    }

    public byte[] createInvoicePdf(Invoice invoice) {
        byte[] docx = templateRenderer.render(invoice, LocaleContextHolder.getLocale());
        return pdfConverter.convertToPdf(docx);
    }

    public static String filenameFor(Invoice invoice) {
        String number = invoice == null ? null : invoice.getInvoiceNumber();
        String normalized = safeText(number).replaceAll("[^A-Za-z0-9._-]+", "-")
                .replaceAll("[-.]{2,}", "-")
                .replaceAll("^[.-]+|[.-]+$", "");
        if (normalized.isBlank()) {
            normalized = "invoice";
        }
        return "invoice-" + normalized.substring(0, Math.min(normalized.length(), 80)) + ".pdf";
    }

    static String safeText(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ")
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ');
        StringBuilder safe = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char character = normalized.charAt(i);
            safe.append(character >= 32 && character <= 126 ? character : ' ');
        }
        return safe.toString().replaceAll("\\s+", " ").trim();
    }

    public record InvoicePdf(byte[] content, String filename) {
    }
}
