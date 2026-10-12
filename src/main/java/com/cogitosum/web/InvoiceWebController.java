package com.cogitosum.web;

import com.cogitosum.entity.Customer;
import com.cogitosum.entity.Invoice;
import com.cogitosum.entity.InvoiceStatus;
import com.cogitosum.entity.LineItem;
import com.cogitosum.service.CustomerService;
import com.cogitosum.service.CustomerStatementPdfService;
import com.cogitosum.service.CustomerStatementService;
import com.cogitosum.service.CreditNoteService;
import com.cogitosum.service.DocumentAttachmentService;
import com.cogitosum.service.InvoiceEmailService;
import com.cogitosum.service.InvoicePdfBundleService;
import com.cogitosum.service.InvoicePdfService;
import com.cogitosum.service.InvoiceService;
import com.cogitosum.service.InvoiceTemplateService;
import com.cogitosum.service.PaymentService;
import com.cogitosum.service.TaxFilingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;

@Controller
@RequestMapping("/invoices")
public class InvoiceWebController {

    @Autowired private InvoiceService invoiceService;
    @Autowired private CustomerService customerService;
    @Autowired private PaymentService paymentService;
    @Autowired private InvoicePdfService invoicePdfService;
    @Autowired private InvoiceEmailService invoiceEmailService;
    @Autowired private DocumentAttachmentService attachmentService;
    @Autowired private TaxFilingService taxFilingService;
    @Autowired private InvoicePdfBundleService invoicePdfBundleService;
    @Autowired private InvoiceTemplateService invoiceTemplateService;
    @Autowired private CustomerStatementService customerStatementService;
    @Autowired private CustomerStatementPdfService customerStatementPdfService;
    @Autowired private CreditNoteService creditNoteService;

    @GetMapping
    public String list(@RequestParam(required = false) InvoiceStatus status, Model model) {
        model.addAttribute("invoices", status == null
                ? invoiceService.getAllInvoices()
                : invoiceService.getInvoicesByStatus(status));
        model.addAttribute("invoiceStatuses", InvoiceStatus.values());
        model.addAttribute("selectedStatus", status);
        model.addAttribute("customers", customerService.getAllCustomers());
        model.addAttribute("dateFilterPeriods", taxFilingService.getDateFilterPeriods(LocalDate.now()));
        return "invoices/list";
    }

    @GetMapping("/statements")
    public String customerStatement(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Model model) {
        LocalDate effectiveEndDate = endDate == null ? LocalDate.now() : endDate;
        LocalDate effectiveStartDate = startDate == null
                ? LocalDate.of(effectiveEndDate.getYear(), 1, 1) : startDate;
        model.addAttribute("customers", customerService.getAllCustomers());
        model.addAttribute("selectedCustomerId", customerId);
        model.addAttribute("startDate", effectiveStartDate);
        model.addAttribute("endDate", effectiveEndDate);
        if (customerId != null) {
            try {
                model.addAttribute("statement",
                        customerStatementService.getStatement(customerId, effectiveStartDate, effectiveEndDate));
            } catch (IllegalArgumentException exception) {
                model.addAttribute("statementError", exception.getMessage());
            }
        }
        return "invoices/statements";
    }

    @GetMapping("/statements/{customerId}.pdf")
    public ResponseEntity<byte[]> customerStatementPdf(
            @PathVariable Long customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        LocalDate effectiveEndDate = endDate == null ? LocalDate.now() : endDate;
        try {
            CustomerStatementService.Statement statement =
                    customerStatementService.getStatement(customerId, startDate, effectiveEndDate);
            byte[] pdf = customerStatementPdfService.createPdf(statement);
            String filename = CustomerStatementPdfService.filenameFor(
                    statement.customer(), startDate, effectiveEndDate);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .cacheControl(CacheControl.noStore().cachePrivate())
                    .header("X-Content-Type-Options", "nosniff")
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename(filename, StandardCharsets.UTF_8).build().toString())
                    .contentLength(pdf.length)
                    .body(pdf);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN)
                    .body("Invalid statement request".getBytes(StandardCharsets.UTF_8));
        }
    }

    @PostMapping(value = "/pdf-download", params = "!templateId")
    public String selectTemplateForPdfs(
            @RequestParam(value = "invoiceIds", required = false) List<Long> invoiceIds,
            Model model) {
        if (invoiceIds == null || invoiceIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select at least one invoice");
        }
        if (invoiceIds.size() > InvoicePdfBundleService.MAX_INVOICES_PER_DOWNLOAD
                || invoiceIds.stream().anyMatch(java.util.Objects::isNull)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid invoice selection");
        }
        if (invoiceIds.stream().anyMatch(id -> invoiceService.getInvoiceById(id).isEmpty())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "One or more invoices are unavailable");
        }
        addTemplateChoice(model, true, null, invoiceIds);
        return "invoices/pdf-template-choice";
    }

    @PostMapping(value = "/pdf-download", params = "templateId")
    public ResponseEntity<byte[]> downloadSelectedPdfs(
            @RequestParam(value = "invoiceIds", required = false) List<Long> invoiceIds,
            @RequestParam String templateId) {
        try {
            InvoicePdfBundleService.Download download = invoicePdfBundleService.download(invoiceIds, templateId);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(download.contentType()))
                    .cacheControl(CacheControl.noStore().cachePrivate())
                    .header("X-Content-Type-Options", "nosniff")
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename(download.filename(), StandardCharsets.UTF_8).build().toString())
                    .contentLength(download.content().length)
                    .body(download.content());
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN)
                    .body(exception.getMessage().getBytes(StandardCharsets.UTF_8));
        }
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("customers", customerService.getAllCustomers());
        model.addAttribute("regimes", invoiceService.getRegimes());
        List<InvoiceTemplateService.TemplateOption> finalTemplates =
                invoiceTemplateService.getCurrentCompanyOptions();
        model.addAttribute("finalTemplates", finalTemplates);
        model.addAttribute("suggestedFinalTemplateId",
                invoiceTemplateService.suggestedTemplateId(finalTemplates,
                        org.springframework.context.i18n.LocaleContextHolder.getLocale()));
        model.addAttribute("invoice", new Invoice());
        model.addAttribute("suggestedNumber", invoiceService.suggestNextInvoiceNumber());
        model.addAttribute("isEdit", false);
        return "invoices/form";
    }

    @PostMapping
    public String create(@RequestParam Long customerId,
                         @RequestParam(required = false) String taxRegime,
                         @RequestParam(required = false) String notes,
                         @RequestParam(required = false) String invoiceNumber,
                         @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate invoiceDate,
                         @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate dueDate,
                         @RequestParam(required = false) List<String> descriptions,
                         @RequestParam(required = false) List<BigDecimal> quantities,
                         @RequestParam(required = false) List<BigDecimal> unitPrices,
                         @RequestParam(defaultValue = "false") boolean finalInvoice,
                         @RequestParam(required = false) String finalTemplateId,
                         RedirectAttributes ra) {
        try {
            Customer customer = customerService.getCustomerById(customerId)
                    .orElseThrow(() -> new IllegalArgumentException("Customer not found"));

            Invoice invoice = new Invoice();
            invoice.setCustomer(customer);
            invoice.setNotes(notes);
            invoice.setInvoiceNumber(invoiceNumber);
            invoice.setInvoiceDate(invoiceDate);
            invoice.setDueDate(dueDate);
            invoice.setStatus(InvoiceStatus.DRAFT);

            List<LineItem> items = new ArrayList<>();
            if (descriptions != null) {
                for (int i = 0; i < descriptions.size(); i++) {
                    String desc = descriptions.get(i);
                    if (desc == null || desc.isBlank()) continue;
                    LineItem li = new LineItem();
                    li.setInvoice(invoice);
                    li.setDescription(desc);
                    li.setQuantity(quantities.get(i));
                    li.setUnitPrice(unitPrices.get(i));
                    items.add(li);
                }
            }
            if (items.isEmpty()) {
                throw new IllegalArgumentException("Invoice must have at least one line item");
            }
            invoice.setLineItems(items);

            Invoice saved = invoiceService.createInvoice(invoice, taxRegime, finalInvoice, finalTemplateId);
            ra.addFlashAttribute("flashSuccess", "Invoice " + saved.getInvoiceNumber() + " created");
            return "redirect:/invoices/" + saved.getId();
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Could not create invoice: " + e.getMessage());
            return "redirect:/invoices/new";
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model, RedirectAttributes ra) {
        return invoiceService.getInvoiceById(id).map(invoice -> {
            if (invoice.getStatus() != InvoiceStatus.DRAFT) {
                ra.addFlashAttribute("flashError", "Only draft invoices can be edited");
                return "redirect:/invoices/" + id;
            }
            model.addAttribute("invoice", invoice);
            model.addAttribute("customers", customerService.getAllCustomers());
            model.addAttribute("regimes", invoiceService.getRegimes());
            model.addAttribute("selectedTaxRegime", invoiceService.effectiveTaxRegime(invoice));
            model.addAttribute("isEdit", true);
            return "invoices/form";
        }).orElseGet(() -> {
            ra.addFlashAttribute("flashError", "Invoice not found");
            return "redirect:/invoices";
        });
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @RequestParam Long customerId,
                         @RequestParam(required = false) String taxRegime,
                         @RequestParam(required = false) String notes,
                         @RequestParam(required = false) String invoiceNumber,
                         @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate invoiceDate,
                         @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate dueDate,
                         @RequestParam(required = false) List<String> descriptions,
                         @RequestParam(required = false) List<BigDecimal> quantities,
                         @RequestParam(required = false) List<BigDecimal> unitPrices,
                         RedirectAttributes ra) {
        try {
            Invoice invoice = new Invoice();
            invoice.setInvoiceNumber(invoiceNumber);
            invoice.setCustomer(customerService.getCustomerById(customerId).orElseThrow());
            invoice.setInvoiceDate(invoiceDate);
            invoice.setDueDate(dueDate);
            invoice.setNotes(notes);
            invoice.setLineItems(buildLineItems(descriptions, quantities, unitPrices, invoice));
            invoiceService.updateInvoice(id, invoice, taxRegime);
            ra.addFlashAttribute("flashSuccess", "Invoice updated");
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Could not update invoice: " + e.getMessage());
        }
        return "redirect:/invoices/" + id;
    }

    @PostMapping("/{id}/duplicate")
    public String duplicate(@PathVariable Long id, RedirectAttributes ra) {
        try {
            Invoice copy = invoiceService.duplicateInvoice(id);
            ra.addFlashAttribute("flashSuccess", "Invoice duplicated as " + copy.getInvoiceNumber());
            return "redirect:/invoices/" + copy.getId() + "/edit";
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Could not duplicate invoice: " + e.getMessage());
            return "redirect:/invoices/" + id;
        }
    }

    private List<LineItem> buildLineItems(List<String> descriptions, List<BigDecimal> quantities,
                                          List<BigDecimal> unitPrices, Invoice invoice) {
        List<LineItem> items = new ArrayList<>();
        if (descriptions == null) return items;
        for (int i = 0; i < descriptions.size(); i++) {
            if (descriptions.get(i) == null || descriptions.get(i).isBlank()) continue;
            LineItem item = new LineItem();
            item.setInvoice(invoice);
            item.setDescription(descriptions.get(i));
            item.setQuantity(quantities.get(i));
            item.setUnitPrice(unitPrices.get(i));
            items.add(item);
        }
        return items;
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model, RedirectAttributes ra) {
        return invoiceService.getInvoiceById(id)
                .map(inv -> {
                    model.addAttribute("invoice", inv);
                    model.addAttribute("payments", paymentService.getPaymentsByInvoiceId(id));
                    model.addAttribute("attachments", attachmentService.listInvoiceAttachments(id));
                    model.addAttribute("creditNotes", creditNoteService.getInvoiceCreditNotes(id));
                    if (inv.getStatus() == InvoiceStatus.DRAFT) {
                        List<InvoiceTemplateService.TemplateOption> finalTemplates =
                                invoiceTemplateService.getCurrentCompanyOptions();
                        model.addAttribute("finalTemplates", finalTemplates);
                        model.addAttribute("suggestedFinalTemplateId",
                                invoiceTemplateService.suggestedTemplateId(finalTemplates,
                                        org.springframework.context.i18n.LocaleContextHolder.getLocale()));
                    }
                    BigDecimal remainingCreditableAmount =
                            creditNoteService.getRemainingCreditableAmount(inv);
                    model.addAttribute("remainingCreditableAmount", remainingCreditableAmount);
                    model.addAttribute("canCreateCreditNote", creditNoteService.canCredit(inv)
                            && remainingCreditableAmount.signum() > 0);
                    model.addAttribute("issuedCreditAmount",
                            inv.getTotalAmount().subtract(remainingCreditableAmount));
                    model.addAttribute("outstandingAmount", invoiceService.getOutstandingAmount(inv));
                    return "invoices/detail";
                })
                .orElseGet(() -> {
                    ra.addFlashAttribute("flashError", "Invoice not found");
                    return "redirect:/invoices";
                });
    }

    @PostMapping("/{id}/send")
    public String send(@PathVariable Long id, RedirectAttributes ra) {
        invoiceService.markInvoiceAsSent(id);
        ra.addFlashAttribute("flashSuccess", "Invoice marked as sent");
        return "redirect:/invoices/" + id;
    }

    @PostMapping("/{id}/finalize")
    public String finalizeInvoice(@PathVariable Long id,
                                 @RequestParam(defaultValue = "false") boolean finalInvoice,
                                 @RequestParam(required = false) String finalTemplateId,
                                 RedirectAttributes ra) {
        try {
            if (!finalInvoice) {
                throw new IllegalArgumentException("Confirm that this is a final invoice before issuing it");
            }
            Invoice invoice = invoiceService.finalizeInvoice(id, finalTemplateId);
            if (invoice == null) {
                throw new IllegalArgumentException("Invoice not found");
            }
            ra.addFlashAttribute("flashSuccess",
                    "Final invoice issued, posted to the ledger, locked, and PDF saved");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            ra.addFlashAttribute("flashError", "Could not issue final invoice: " + exception.getMessage());
        }
        return "redirect:/invoices/" + id;
    }

    @GetMapping(value = "/{id}/pdf", params = "!templateId")
    public Object selectTemplateForPdf(@PathVariable Long id, Model model) {
        var invoice = invoiceService.getInvoiceById(id);
        if (invoice.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found");
        }
        var storedPdf = invoicePdfService.downloadStoredInvoice(id);
        if (storedPdf.isPresent()) {
            return pdfResponse(storedPdf.get());
        }
        model.addAttribute("invoice", invoice.get());
        addTemplateChoice(model, false, id, List.of(id));
        return "invoices/pdf-template-choice";
    }

    @GetMapping(value = "/{id}/pdf", params = "templateId")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id, @RequestParam String templateId) {
        try {
            return invoicePdfService.downloadInvoice(id, templateId)
                .map(this::pdfResponse)
                .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN)
                    .body("Invalid invoice template selection".getBytes(StandardCharsets.UTF_8));
        }
    }

    @PostMapping("/{id}/email")
    public String email(@PathVariable Long id, @RequestParam(required = false) String recipient,
                        RedirectAttributes ra) {
        try {
            invoiceEmailService.emailInvoice(id, recipient);
            ra.addFlashAttribute("flashSuccess", "Invoice emailed and marked as sent.");
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Could not email invoice: " + e.getMessage());
        }
        return "redirect:/invoices/" + id;
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id, @RequestParam String reason, RedirectAttributes ra) {
        try {
            invoiceService.cancelInvoice(id, reason);
            ra.addFlashAttribute("flashSuccess", "Invoice cancelled");
        } catch (Exception exception) {
            ra.addFlashAttribute("flashError", "Could not cancel invoice: " + exception.getMessage());
        }
        return "redirect:/invoices/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            invoiceService.deleteInvoice(id);
            ra.addFlashAttribute("flashSuccess", "Invoice deleted");
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Could not delete invoice: " + e.getMessage());
        }
        return "redirect:/invoices";
    }

    private void addTemplateChoice(Model model, boolean bulk, Long invoiceId, List<Long> invoiceIds) {
        List<InvoiceTemplateService.TemplateOption> templates =
                invoiceTemplateService.getCurrentCompanyOptions();
        model.addAttribute("templates", templates);
        model.addAttribute("suggestedTemplateId",
                invoiceTemplateService.suggestedTemplateId(templates,
                        org.springframework.context.i18n.LocaleContextHolder.getLocale()));
        model.addAttribute("bulkDownload", bulk);
        model.addAttribute("invoiceId", invoiceId);
        model.addAttribute("invoiceIds", invoiceIds);
    }

    private ResponseEntity<byte[]> pdfResponse(InvoicePdfService.InvoicePdf pdf) {
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .cacheControl(CacheControl.noStore().cachePrivate())
            .header("X-Content-Type-Options", "nosniff")
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                    .filename(pdf.filename(), StandardCharsets.UTF_8).build().toString())
            .contentLength(pdf.content().length)
            .body(pdf.content());
    }
}
