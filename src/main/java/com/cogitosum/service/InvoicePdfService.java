package com.cogitosum.service;

import com.cogitosum.entity.Invoice;
import com.cogitosum.entity.InvoiceFinalPdf;
import com.cogitosum.repository.InvoiceFinalPdfRepository;
import com.cogitosum.repository.InvoiceRepository;
import com.cogitosum.config.LoggingCategory;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.i18n.LocaleContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class InvoicePdfService {

    private static final Logger log = LoggerFactory.getLogger(InvoicePdfService.class);

    private final InvoiceRepository invoiceRepository;
    private final CurrentCompanyContext companyContext;
    private final InvoiceDocxTemplateRenderer templateRenderer;
    private final LibreOfficePdfConverter pdfConverter;
    private final InvoiceTemplateService invoiceTemplateService;
    private final InvoiceFinalPdfRepository finalPdfRepository;

    public InvoicePdfService(InvoiceRepository invoiceRepository, CurrentCompanyContext companyContext,
                             InvoiceDocxTemplateRenderer templateRenderer, LibreOfficePdfConverter pdfConverter,
                             InvoiceTemplateService invoiceTemplateService,
                             InvoiceFinalPdfRepository finalPdfRepository) {
        this.invoiceRepository = invoiceRepository;
        this.companyContext = companyContext;
        this.templateRenderer = templateRenderer;
        this.pdfConverter = pdfConverter;
        this.invoiceTemplateService = invoiceTemplateService;
        this.finalPdfRepository = finalPdfRepository;
    }

    @Transactional(readOnly = true)
    public Optional<InvoicePdf> downloadInvoice(Long invoiceId) {
        return invoiceRepository.findByIdAndCompanyId(invoiceId, companyContext.requireCompanyId())
                .map(invoice -> storedPdf(invoice)
                        .orElseGet(() -> new InvoicePdf(createInvoicePdf(invoice), filenameFor(invoice))));
    }

    @Transactional(readOnly = true)
    public Optional<InvoicePdf> downloadInvoice(Long invoiceId, String templateId) {
        return invoiceRepository.findByIdAndCompanyId(invoiceId, companyContext.requireCompanyId())
                .map(invoice -> storedPdf(invoice)
                        .orElseGet(() -> new InvoicePdf(createInvoicePdf(invoice, templateId), filenameFor(invoice))));
    }

    @Transactional(readOnly = true)
    public Optional<InvoicePdf> downloadStoredInvoice(Long invoiceId) {
        return invoiceRepository.findByIdAndCompanyId(invoiceId, companyContext.requireCompanyId())
                .flatMap(this::storedPdf);
    }

    @Transactional
    public void storeFinalPdf(Long invoiceId, byte[] pdfData) {
        if (invoiceId == null || pdfData == null || pdfData.length == 0) {
            throw new IllegalArgumentException("A saved invoice and non-empty PDF are required");
        }
        finalPdfRepository.save(new InvoiceFinalPdf(invoiceId, pdfData));
    }

    private Optional<InvoicePdf> storedPdf(Invoice invoice) {
        return finalPdfRepository.findById(invoice.getId())
                .map(finalPdf -> new InvoicePdf(finalPdf.getPdfData(), filenameFor(invoice)));
    }

    public byte[] createInvoicePdf(Invoice invoice) {
        long startedAt = System.nanoTime();
        byte[] docx = templateRenderer.render(invoice, LocaleContextHolder.getLocale());
        return convertAndLog(invoice, docx, startedAt);
    }

    public byte[] createInvoicePdf(Invoice invoice, String templateId) {
        if (templateId == null || InvoiceTemplateService.STANDARD_TEMPLATE_ID.equals(templateId)) {
            return createInvoicePdf(invoice);
        }
        InvoiceTemplateService.TemplateContent selected =
                invoiceTemplateService.getTemplateForCompany(invoice.getCompany().getId(), templateId);
        Locale locale = Locale.forLanguageTag(selected.languageTag());
        long startedAt = System.nanoTime();
        byte[] docx = templateRenderer.render(invoice, locale, selected.data());
        return convertAndLog(invoice, docx, startedAt);
    }

    private byte[] convertAndLog(Invoice invoice, byte[] docx, long startedAt) {
        byte[] pdf = pdfConverter.convertToPdf(docx);
        try (LoggingCategory.Scope ignored = LoggingCategory.open(LoggingCategory.INTERNAL_PROCESS)) {
            log.info("Invoice PDF generated: invoiceId={} bytes={} durationMs={}",
                    invoice.getId(), pdf.length, (System.nanoTime() - startedAt) / 1_000_000);
        }
        return pdf;
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
