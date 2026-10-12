package com.cogitosum.web;

import com.cogitosum.service.CurrentCompanyContext;
import com.cogitosum.service.InvoiceTemplateService;
import org.springframework.stereotype.Controller;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
@RequestMapping("/admin/invoice-templates")
public class InvoiceTemplateWebController {

    private static final Logger log = LoggerFactory.getLogger(InvoiceTemplateWebController.class);
    private static final MediaType DOCX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    private final InvoiceTemplateService templateService;
    private final CurrentCompanyContext companyContext;
    private final MessageSource messageSource;

    public InvoiceTemplateWebController(
            InvoiceTemplateService templateService,
            CurrentCompanyContext companyContext,
            MessageSource messageSource) {
        this.templateService = templateService;
        this.companyContext = companyContext;
        this.messageSource = messageSource;
    }

    @GetMapping
    public String page(Model model) {
        List<InvoiceTemplateService.TemplateOption> templates =
                templateService.getManagedTemplatesForCurrentCompany();
        model.addAttribute("templates", templates);
        model.addAttribute("company", companyContext.requireCompany());
        model.addAttribute("active", "invoice-templates");
        return "admin/invoice-templates";
    }

    @GetMapping("/{templateId}/download")
    public ResponseEntity<byte[]> download(@PathVariable String templateId) {
        InvoiceTemplateService.TemplateContent template =
                templateService.getTemplateForCurrentCompany(templateId);
        byte[] data = template.data();
        return ResponseEntity.ok()
                .contentType(DOCX_MEDIA_TYPE)
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(template.originalFilename(), StandardCharsets.UTF_8).build().toString())
                .contentLength(data.length)
                .body(data);
    }

    @PostMapping
    public String upload(@RequestParam String name,
                         @RequestParam String languageTag,
                         @RequestParam("file") MultipartFile file,
                         RedirectAttributes attributes) {
        try {
            templateService.uploadForCurrentCompany(
                    name, languageTag, file.getOriginalFilename(), file.getBytes());
            attributes.addFlashAttribute("flashSuccess", message("invoiceTemplates.uploaded"));
        } catch (IOException exception) {
            log.warn("Could not read invoice template upload", exception);
            attributes.addFlashAttribute("flashError", message("invoiceTemplates.error.upload"));
        } catch (IllegalArgumentException exception) {
            log.warn("Rejected invoice template upload: {}", exception.getMessage());
            attributes.addFlashAttribute("flashError", message("invoiceTemplates.error.upload"));
        }
        return "redirect:/admin/invoice-templates";
    }

    @PostMapping("/{templateId}/delete")
    public String delete(@PathVariable String templateId, RedirectAttributes attributes) {
        try {
            templateService.deleteFromCurrentCompany(templateId);
            attributes.addFlashAttribute("flashSuccess", message("invoiceTemplates.deleted"));
        } catch (IllegalArgumentException exception) {
            log.warn("Rejected invoice template deletion: {}", exception.getMessage());
            attributes.addFlashAttribute("flashError", message("invoiceTemplates.error.delete"));
        }
        return "redirect:/admin/invoice-templates";
    }

    private String message(String key) {
        return messageSource.getMessage(key, null, LocaleContextHolder.getLocale());
    }
}
