package com.cogitosum.service;

import com.cogitosum.entity.DocumentAttachment;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class InvoicePdfBundleService {

    public static final int MAX_INVOICES_PER_DOWNLOAD = 100;
    private static final int MAX_PDF_FILES = 250;
    private static final long MAX_UNCOMPRESSED_BYTES = 100L * 1024 * 1024;

    private final InvoicePdfService invoicePdfService;
    private final DocumentAttachmentService attachmentService;

    public InvoicePdfBundleService(InvoicePdfService invoicePdfService,
                                   DocumentAttachmentService attachmentService) {
        this.invoicePdfService = invoicePdfService;
        this.attachmentService = attachmentService;
    }

    public Download download(List<Long> requestedInvoiceIds) {
        return download(requestedInvoiceIds, InvoiceTemplateService.STANDARD_TEMPLATE_ID);
    }

    public Download download(List<Long> requestedInvoiceIds, String templateId) {
        if (requestedInvoiceIds == null || requestedInvoiceIds.isEmpty()) {
            throw new IllegalArgumentException("Select at least one invoice");
        }
        if (requestedInvoiceIds.size() > MAX_INVOICES_PER_DOWNLOAD
                || requestedInvoiceIds.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Select no more than "
                    + MAX_INVOICES_PER_DOWNLOAD + " valid invoices");
        }
        Set<Long> invoiceIds = new LinkedHashSet<>(requestedInvoiceIds);
        if (invoiceIds.size() > MAX_INVOICES_PER_DOWNLOAD) {
            throw new IllegalArgumentException("Select no more than "
                    + MAX_INVOICES_PER_DOWNLOAD + " invoices");
        }

        List<PdfFile> files = new ArrayList<>();
        long totalBytes = 0;
        Set<String> entryNames = new HashSet<>();
        for (Long invoiceId : invoiceIds) {
            Optional<InvoicePdfService.InvoicePdf> generatedPdf =
                    InvoiceTemplateService.STANDARD_TEMPLATE_ID.equals(templateId)
                            ? invoicePdfService.downloadInvoice(invoiceId)
                            : invoicePdfService.downloadInvoice(invoiceId, templateId);
            InvoicePdfService.InvoicePdf invoicePdf = generatedPdf
                    .orElseThrow(() -> new IllegalArgumentException(
                            "One or more selected invoices are unavailable"));
            totalBytes = addFile(files, entryNames, invoicePdf.filename(), invoicePdf.content(), totalBytes);

            String invoiceFolder = safePathPart(invoicePdf.filename().replaceFirst("\\.pdf$", ""));
            for (DocumentAttachment attachment : attachmentService.listInvoiceAttachments(invoiceId)) {
                if (!"application/pdf".equalsIgnoreCase(attachment.getContentType())) {
                    continue;
                }
                DocumentAttachmentService.AttachmentDownload download = attachmentService
                        .invoiceAttachment(invoiceId, attachment.getId())
                        .orElseThrow(() -> new IllegalStateException(
                                "Invoice attachment is unavailable: " + attachment.getId()));
                totalBytes = addFile(files, entryNames,
                        invoiceFolder + "/" + safePathPart(download.filename()), download.content(), totalBytes);
            }
        }

        if (files.size() > MAX_PDF_FILES) {
            throw new IllegalArgumentException("The selection contains too many PDF files");
        }
        if (files.size() == 1) {
            PdfFile file = files.getFirst();
            return new Download(file.filename(), "application/pdf", file.content());
        }
        return new Download("invoice-pdfs.zip", "application/zip", createZip(files));
    }

    private long addFile(List<PdfFile> files, Set<String> names, String name, byte[] content, long totalBytes) {
        if (content.length == 0 || content.length > MAX_UNCOMPRESSED_BYTES - totalBytes) {
            throw new IllegalArgumentException("The selected PDF files exceed the download size limit");
        }
        if (files.size() >= MAX_PDF_FILES) {
            throw new IllegalArgumentException("The selection contains too many PDF files");
        }
        String uniqueName = uniqueName(name, names);
        files.add(new PdfFile(uniqueName, content));
        return totalBytes + content.length;
    }

    private String uniqueName(String requestedName, Set<String> names) {
        String name = safeEntryName(requestedName);
        if (names.add(name)) {
            return name;
        }
        int separator = name.lastIndexOf('/');
        String directory = separator < 0 ? "" : name.substring(0, separator + 1);
        String baseName = separator < 0 ? name : name.substring(separator + 1);
        int dot = baseName.lastIndexOf('.');
        String stem = dot > 0 ? baseName.substring(0, dot) : baseName;
        String extension = dot > 0 ? baseName.substring(dot) : "";
        for (int suffix = 2; suffix <= MAX_PDF_FILES; suffix++) {
            String candidate = directory + stem + "-" + suffix + extension;
            if (names.add(candidate)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Could not create unique names for the selected PDF files");
    }

    private String safePathPart(String value) {
        String safe = value == null ? "" : value.replace('\\', '/');
        safe = safe.substring(safe.lastIndexOf('/') + 1)
                .replaceAll("[^A-Za-z0-9._ -]", "_")
                .replaceAll("^[. ]+|[. ]+$", "");
        return safe.isBlank() ? "document.pdf" : safe.substring(0, Math.min(safe.length(), 200));
    }

    private String safeEntryName(String value) {
        String normalized = value == null ? "" : value.replace('\\', '/');
        List<String> parts = new ArrayList<>();
        for (String part : normalized.split("/")) {
            if (part.isBlank() || ".".equals(part) || "..".equals(part)) {
                continue;
            }
            parts.add(safePathPart(part));
        }
        return parts.isEmpty() ? "document.pdf" : String.join("/", parts);
    }

    private byte[] createZip(List<PdfFile> files) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (PdfFile file : files) {
                zip.putNextEntry(new ZipEntry(file.filename()));
                zip.write(file.content());
                zip.closeEntry();
            }
            zip.finish();
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not package the selected invoice PDFs", exception);
        }
    }

    private record PdfFile(String filename, byte[] content) {
    }

    public record Download(String filename, String contentType, byte[] content) {
    }
}
