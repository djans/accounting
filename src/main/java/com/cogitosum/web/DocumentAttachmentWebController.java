package com.cogitosum.web;

import com.cogitosum.service.DocumentAttachmentService;
import java.nio.charset.StandardCharsets;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping
public class DocumentAttachmentWebController {

    private final DocumentAttachmentService attachmentService;

    public DocumentAttachmentWebController(DocumentAttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping(value = "/invoices/{invoiceId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String uploadInvoice(@PathVariable Long invoiceId, @RequestParam("file") MultipartFile file,
                                RedirectAttributes attributes) {
        try {
            attachmentService.addInvoiceAttachment(invoiceId, file);
            attributes.addFlashAttribute("flashSuccess", "Attachment uploaded.");
        } catch (Exception e) {
            attributes.addFlashAttribute("flashError", "Could not upload attachment: " + e.getMessage());
        }
        return "redirect:/invoices/" + invoiceId;
    }

    @GetMapping("/invoices/{invoiceId}/attachments/{attachmentId}")
    public ResponseEntity<byte[]> downloadInvoice(@PathVariable Long invoiceId, @PathVariable Long attachmentId) {
        return attachmentService.invoiceAttachment(invoiceId, attachmentId)
                .map(this::attachmentResponse)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/invoices/{invoiceId}/attachments/{attachmentId}/delete")
    public String deleteInvoice(@PathVariable Long invoiceId, @PathVariable Long attachmentId,
                                RedirectAttributes attributes) {
        if (attachmentService.deleteInvoiceAttachment(invoiceId, attachmentId)) {
            attributes.addFlashAttribute("flashSuccess", "Attachment deleted.");
        } else {
            attributes.addFlashAttribute("flashError", "Attachment not found.");
        }
        return "redirect:/invoices/" + invoiceId;
    }

    @PostMapping(value = "/bills/{billId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String uploadBill(@PathVariable Long billId, @RequestParam("file") MultipartFile file,
                             RedirectAttributes attributes) {
        try {
            attachmentService.addBillAttachment(billId, file);
            attributes.addFlashAttribute("flashSuccess", "Attachment uploaded.");
        } catch (Exception e) {
            attributes.addFlashAttribute("flashError", "Could not upload attachment: " + e.getMessage());
        }
        return "redirect:/bills/" + billId;
    }

    @GetMapping("/bills/{billId}/attachments/{attachmentId}")
    public ResponseEntity<byte[]> downloadBill(@PathVariable Long billId, @PathVariable Long attachmentId) {
        return attachmentService.billAttachment(billId, attachmentId)
                .map(this::attachmentResponse)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/bills/{billId}/attachments/{attachmentId}/delete")
    public String deleteBill(@PathVariable Long billId, @PathVariable Long attachmentId,
                             RedirectAttributes attributes) {
        if (attachmentService.deleteBillAttachment(billId, attachmentId)) {
            attributes.addFlashAttribute("flashSuccess", "Attachment deleted.");
        } else {
            attributes.addFlashAttribute("flashError", "Attachment not found.");
        }
        return "redirect:/bills/" + billId;
    }

    private ResponseEntity<byte[]> attachmentResponse(DocumentAttachmentService.AttachmentDownload attachment) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.contentType()))
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(attachment.filename(), StandardCharsets.UTF_8).build().toString())
                .contentLength(attachment.content().length)
                .body(attachment.content());
    }
}
