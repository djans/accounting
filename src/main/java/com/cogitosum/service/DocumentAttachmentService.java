package com.cogitosum.service;

import com.cogitosum.entity.Bill;
import com.cogitosum.entity.DocumentAttachment;
import com.cogitosum.entity.DocumentAttachmentContent;
import com.cogitosum.entity.Invoice;
import com.cogitosum.repository.BillRepository;
import com.cogitosum.repository.DocumentAttachmentContentRepository;
import com.cogitosum.repository.DocumentAttachmentRepository;
import com.cogitosum.repository.InvoiceRepository;
import java.io.IOException;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentAttachmentService {

    private static final String PDF = "application/pdf";
    private static final String JPEG = "image/jpeg";
    private static final String PNG = "image/png";

    private final DocumentAttachmentRepository attachmentRepository;
    private final DocumentAttachmentContentRepository contentRepository;
    private final InvoiceRepository invoiceRepository;
    private final BillRepository billRepository;
    private final CurrentCompanyContext companyContext;
    private final long maxSize;

    public DocumentAttachmentService(DocumentAttachmentRepository attachmentRepository,
                                     DocumentAttachmentContentRepository contentRepository,
                                     InvoiceRepository invoiceRepository,
                                     BillRepository billRepository,
                                     CurrentCompanyContext companyContext,
                                     @Value("${app.attachments.max-size:10485760}") long maxSize) {
        this.attachmentRepository = attachmentRepository;
        this.contentRepository = contentRepository;
        this.invoiceRepository = invoiceRepository;
        this.billRepository = billRepository;
        this.companyContext = companyContext;
        this.maxSize = maxSize;
    }

    @Transactional
    public DocumentAttachment addInvoiceAttachment(Long invoiceId, MultipartFile file) {
        ValidatedUpload upload = validate(file);
        Long companyId = companyContext.requireCompanyId();
        Invoice invoice = invoiceRepository.findByIdAndCompanyId(invoiceId, companyId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));
        return save(companyContext.requireCompany(), invoice, null, upload);
    }

    @Transactional
    public DocumentAttachment addBillAttachment(Long billId, MultipartFile file) {
        ValidatedUpload upload = validate(file);
        Long companyId = companyContext.requireCompanyId();
        Bill bill = billRepository.findByIdAndCompanyId(billId, companyId)
                .orElseThrow(() -> new IllegalArgumentException("Bill not found"));
        return save(companyContext.requireCompany(), null, bill, upload);
    }

    @Transactional(readOnly = true)
    public List<DocumentAttachment> listInvoiceAttachments(Long invoiceId) {
        Long companyId = companyContext.requireCompanyId();
        requireInvoice(invoiceId, companyId);
        return attachmentRepository.findAllByCompanyIdAndInvoiceIdOrderByCreatedAtDesc(companyId, invoiceId);
    }

    @Transactional(readOnly = true)
    public List<DocumentAttachment> listBillAttachments(Long billId) {
        Long companyId = companyContext.requireCompanyId();
        requireBill(billId, companyId);
        return attachmentRepository.findAllByCompanyIdAndBillIdOrderByCreatedAtDesc(companyId, billId);
    }

    @Transactional(readOnly = true)
    public Optional<AttachmentDownload> invoiceAttachment(Long invoiceId, Long attachmentId) {
        return attachmentDownload(attachmentRepository.findByIdAndCompanyIdAndInvoiceId(
                attachmentId, companyContext.requireCompanyId(), invoiceId));
    }

    @Transactional(readOnly = true)
    public Optional<AttachmentDownload> billAttachment(Long billId, Long attachmentId) {
        return attachmentDownload(attachmentRepository.findByIdAndCompanyIdAndBillId(
                attachmentId, companyContext.requireCompanyId(), billId));
    }

    @Transactional
    public boolean deleteInvoiceAttachment(Long invoiceId, Long attachmentId) {
        return delete(attachmentRepository.findByIdAndCompanyIdAndInvoiceId(
                attachmentId, companyContext.requireCompanyId(), invoiceId));
    }

    @Transactional
    public boolean deleteBillAttachment(Long billId, Long attachmentId) {
        return delete(attachmentRepository.findByIdAndCompanyIdAndBillId(
                attachmentId, companyContext.requireCompanyId(), billId));
    }

    private DocumentAttachment save(com.cogitosum.entity.Company company, Invoice invoice, Bill bill,
                                    ValidatedUpload upload) {
        DocumentAttachment attachment = new DocumentAttachment();
        attachment.setCompany(company);
        attachment.setInvoice(invoice);
        attachment.setBill(bill);
        attachment.setFilename(normalizedFilename(upload.originalFilename(), upload.extension()));
        attachment.setContentType(upload.contentType());
        attachment.setContentLength(upload.content().length);

        DocumentAttachmentContent content = new DocumentAttachmentContent();
        content.setContent(upload.content());
        DocumentAttachment saved = attachmentRepository.save(attachment);
        content.setAttachment(saved);
        contentRepository.save(content);
        return saved;
    }

    private Optional<AttachmentDownload> attachmentDownload(Optional<DocumentAttachment> attachment) {
        return attachment.map(item -> {
            companyContext.requireCurrentCompany(item);
            byte[] content = contentRepository.findById(item.getId())
                    .map(DocumentAttachmentContent::getContent)
                    .orElseThrow(() -> new IllegalStateException("Attachment content is unavailable"));
            return new AttachmentDownload(item.getFilename(), item.getContentType(), content);
        });
    }

    private boolean delete(Optional<DocumentAttachment> attachment) {
        if (attachment.isEmpty()) {
            return false;
        }
        companyContext.requireCurrentCompany(attachment.get());
        attachmentRepository.delete(attachment.get());
        return true;
    }

    private void requireInvoice(Long invoiceId, Long companyId) {
        if (invoiceRepository.findByIdAndCompanyId(invoiceId, companyId).isEmpty()) {
            throw new IllegalArgumentException("Invoice not found");
        }
    }

    private void requireBill(Long billId, Long companyId) {
        if (billRepository.findByIdAndCompanyId(billId, companyId).isEmpty()) {
            throw new IllegalArgumentException("Bill not found");
        }
    }

    private ValidatedUpload validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Select a PDF, JPEG, or PNG attachment");
        }
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException("Attachment exceeds the maximum allowed size");
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read the uploaded attachment", e);
        }
        if (content.length == 0 || content.length > maxSize) {
            throw new IllegalArgumentException("Attachment exceeds the maximum allowed size");
        }

        String detectedType = detectType(content);
        String declaredType = file.getContentType() == null ? "" : file.getContentType()
                .toLowerCase(Locale.ROOT).trim();
        if (!detectedType.equals(declaredType)) {
            throw new IllegalArgumentException("Attachment content type does not match its file signature");
        }
        return new ValidatedUpload(content, detectedType, file.getOriginalFilename(), extensionFor(detectedType));
    }

    private String detectType(byte[] content) {
        if (startsWith(content, new byte[] {'%', 'P', 'D', 'F', '-'})) {
            return PDF;
        }
        if (startsWith(content, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})) {
            return JPEG;
        }
        if (startsWith(content, new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'})) {
            return PNG;
        }
        throw new IllegalArgumentException("Only PDF, JPEG, and PNG attachments are allowed");
    }

    private boolean startsWith(byte[] content, byte[] signature) {
        if (content.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (content[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }

    static String normalizedFilename(String originalFilename, String extension) {
        String filename = originalFilename == null ? "" : originalFilename.replace('\\', '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1);
        filename = Normalizer.normalize(filename, Normalizer.Form.NFKC)
                .replaceAll("[\\p{Cntrl}]", " ")
                .replaceAll("[^A-Za-z0-9._ -]", "_")
                .replaceAll("\\s+", " ")
                .trim();
        int lastDot = filename.lastIndexOf('.');
        String basename = lastDot > 0 ? filename.substring(0, lastDot) : filename;
        basename = basename.replaceAll("^[. ]+|[. ]+$", "");
        if (basename.isBlank()) {
            basename = "attachment";
        }
        return basename.substring(0, Math.min(basename.length(), 240 - extension.length())) + extension;
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case PDF -> ".pdf";
            case JPEG -> ".jpg";
            case PNG -> ".png";
            default -> throw new IllegalArgumentException("Unsupported attachment type");
        };
    }

    public record AttachmentDownload(String filename, String contentType, byte[] content) {
    }

    private record ValidatedUpload(byte[] content, String contentType, String originalFilename, String extension) {
    }
}
