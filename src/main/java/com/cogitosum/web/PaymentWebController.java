package com.cogitosum.web;

import com.cogitosum.entity.AccountType;
import com.cogitosum.entity.ChartOfAccount;
import com.cogitosum.entity.Invoice;
import com.cogitosum.entity.Payment;
import com.cogitosum.entity.PaymentMethod;
import com.cogitosum.service.ChartOfAccountService;
import com.cogitosum.service.InvoiceService;
import com.cogitosum.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

@Controller
@RequestMapping("/payments")
public class PaymentWebController {

    @Autowired private PaymentService paymentService;
    @Autowired private InvoiceService invoiceService;
    @Autowired private ChartOfAccountService accountService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("payments", paymentService.getAllPayments());
        return "payments/list";
    }

    @GetMapping("/new")
    public String newForm(@RequestParam(required = false) Long invoiceId,
                          @RequestParam(required = false) PaymentMethod paymentMethod,
                          Model model) {
        model.addAttribute("invoices", invoiceService.getAllInvoices());
        model.addAttribute("methods", PaymentMethod.values());
        model.addAttribute("bankAccounts", bankAccountChoices());
        model.addAttribute("preselectedInvoiceId", invoiceId);
        model.addAttribute("selectedMethod", paymentMethod);
        return "payments/form";
    }

    @PostMapping
    public String create(@RequestParam Long invoiceId,
                         @RequestParam(required = false) Long bankAccountId,
                         @RequestParam BigDecimal amount,
                         @RequestParam PaymentMethod paymentMethod,
                         @RequestParam(required = false) String transactionId,
                         @RequestParam(required = false) String notes,
                         RedirectAttributes ra) {
        try {
            Invoice invoice = invoiceService.getInvoiceById(invoiceId)
                    .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));

            Payment payment = new Payment();
            payment.setInvoice(invoice);
            payment.setAmount(amount);
            payment.setPaymentMethod(paymentMethod);
            payment.setTransactionId(transactionId);
            payment.setNotes(notes);
            if (bankAccountId != null) {
                payment.setBankAccount(accountService.getAccountById(bankAccountId)
                        .orElseThrow(() -> new IllegalArgumentException("Bank account not found")));
            }

            paymentService.recordPayment(payment);
            ra.addFlashAttribute("flashSuccess", "Payment recorded");
            return "redirect:/invoices/" + invoiceId;
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Could not record payment: " + e.getMessage());
            return "redirect:/payments/new";
        }
    }

    /** Only ASSET accounts whose number starts with "10" (bank / cash range) — A/R (1100) excluded. */
    private java.util.List<ChartOfAccount> bankAccountChoices() {
        return accountService.getAccountsByType(AccountType.ASSET).stream()
                .filter(a -> a.getAccountNumber() != null
                        && a.getAccountNumber().startsWith("10")
                        && !a.getAccountNumber().equals("1100"))
                .toList();
    }

    @PostMapping("/{id}/complete")
    public String complete(@PathVariable Long id, RedirectAttributes ra) {
        paymentService.markPaymentAsCompleted(id);
        ra.addFlashAttribute("flashSuccess", "Payment marked as completed");
        return "redirect:/payments";
    }

    @PostMapping("/{id}/refund")
    public String refund(@PathVariable Long id, RedirectAttributes ra) {
        paymentService.refundPayment(id);
        ra.addFlashAttribute("flashSuccess", "Payment refunded");
        return "redirect:/payments";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            paymentService.deletePayment(id);
            ra.addFlashAttribute("flashSuccess", "Payment deleted");
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Could not delete payment: " + e.getMessage());
        }
        return "redirect:/payments";
    }
}
