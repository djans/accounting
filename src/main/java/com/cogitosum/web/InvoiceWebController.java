package com.cogitosum.web;

import com.cogitosum.entity.Customer;
import com.cogitosum.entity.Invoice;
import com.cogitosum.entity.InvoiceStatus;
import com.cogitosum.entity.LineItem;
import com.cogitosum.service.CustomerService;
import com.cogitosum.service.InvoiceService;
import com.cogitosum.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/invoices")
public class InvoiceWebController {

    @Autowired private InvoiceService invoiceService;
    @Autowired private CustomerService customerService;
    @Autowired private PaymentService paymentService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("invoices", invoiceService.getAllInvoices());
        return "invoices/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("customers", customerService.getAllCustomers());
        model.addAttribute("regimes", invoiceService.getRegimes());
        return "invoices/form";
    }

    @PostMapping
    public String create(@RequestParam Long customerId,
                         @RequestParam(required = false) String taxRegime,
                         @RequestParam(required = false) String notes,
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

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model, RedirectAttributes ra) {
        return invoiceService.getInvoiceById(id)
                .map(inv -> {
                    model.addAttribute("invoice", inv);
                    model.addAttribute("payments", paymentService.getPaymentsByInvoiceId(id));
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
