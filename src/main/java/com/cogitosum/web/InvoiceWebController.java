package com.cogitosum.web;

import com.cogitosum.entity.Customer;
import com.cogitosum.entity.Invoice;
import com.cogitosum.entity.InvoiceStatus;
import com.cogitosum.entity.LineItem;
import com.cogitosum.service.CustomerService;
import com.cogitosum.service.DocumentAttachmentService;
import com.cogitosum.service.InvoiceEmailService;
import com.cogitosum.service.InvoicePdfService;
import com.cogitosum.service.InvoiceService;
import com.cogitosum.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/invoices")
public class InvoiceWebController {

    @Autowired private InvoiceService invoiceService;
    @Autowired private CustomerService customerService;
    @Autowired private PaymentService paymentService;
    @Autowired private InvoicePdfService invoicePdfService;
    @Autowired private InvoiceEmailService invoiceEmailService;
    @Autowired private DocumentAttachmentService attachmentService;

    @GetMapping
    public String list(@RequestParam(required = false) InvoiceStatus status, Model model) {
        model.addAttribute("invoices", status == null
                ? invoiceService.getAllInvoices()
                : invoiceService.getInvoicesByStatus(status));
        model.addAttribute("invoiceStatuses", InvoiceStatus.values());
        model.addAttribute("selectedStatus", status);
        return "invoices/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("customers", customerService.getAllCustomers());
        model.addAttribute("regimes", invoiceService.getRegimes());
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

            Invoice saved = invoiceService.createInvoice(invoice, taxRegime);
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

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        return invoicePdfService.downloadInvoice(id)
                .map(pdf -> ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_PDF)
                        .cacheControl(CacheControl.noStore().cachePrivate())
                        .header("X-Content-Type-Options", "nosniff")
                        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                                .filename(pdf.filename(), StandardCharsets.UTF_8).build().toString())
                        .contentLength(pdf.content().length)
                        .body(pdf.content()))
                .orElseGet(() -> ResponseEntity.notFound().build());
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
    public String cancel(@PathVariable Long id, RedirectAttributes ra) {
        invoiceService.cancelInvoice(id);
        ra.addFlashAttribute("flashSuccess", "Invoice cancelled");
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
}
