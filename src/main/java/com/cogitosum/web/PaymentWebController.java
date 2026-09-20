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
import java.util.List;

@Controller
@RequestMapping("/payments")
public class PaymentWebController {

    @Autowired private PaymentService paymentService;
    @Autowired private InvoiceService invoiceService;
    @Autowired private ChartOfAccountService accountService;
    @Autowired private com.cogitosum.service.CustomerService customerService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("payments", paymentService.getAllPayments());
        return "payments/list";
    }

    @GetMapping("/new")
    public String newForm(@RequestParam(required = false) Long invoiceId,
                          @RequestParam(required = false) PaymentMethod paymentMethod,
                          jakarta.servlet.http.HttpSession session,
                          Model model) {
        model.addAttribute("invoices", invoiceService.getAllInvoices());
        model.addAttribute("methods", PaymentMethod.values());
        model.addAttribute("bankAccounts", bankAccountChoices());
        model.addAttribute("arAccounts", arAccountChoices());
        model.addAttribute("preselectedInvoiceId", invoiceId);
        model.addAttribute("selectedMethod", paymentMethod);
        model.addAttribute("active", "payments");

        Long defaultBank = (Long) session.getAttribute("lastBankAccountId");
        Long defaultAr = (Long) session.getAttribute("lastArAccountId");
        model.addAttribute("defaultBankAccountId", defaultBank != null ? defaultBank : -1L);
        model.addAttribute("defaultArAccountId", defaultAr != null ? defaultAr : -1L);

        return "payments/form";
    }

    @PostMapping
    public String create(@RequestParam Long invoiceId,
                         @RequestParam(required = false) Long bankAccountId,
                         @RequestParam(required = false) Long arAccountId,
                         @RequestParam BigDecimal amount,
                         @RequestParam PaymentMethod paymentMethod,
                         @RequestParam(required = false) String transactionId,
                         @RequestParam(required = false) String notes,
                         jakarta.servlet.http.HttpSession session,
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
                session.setAttribute("lastBankAccountId", bankAccountId);
            }
            if (arAccountId != null) {
                payment.setArAccount(accountService.getAccountById(arAccountId)
                        .orElseThrow(() -> new IllegalArgumentException("A/R account not found")));
                session.setAttribute("lastArAccountId", arAccountId);
            }

            paymentService.recordPayment(payment);
            ra.addFlashAttribute("flashSuccess", "Payment recorded");
            return "redirect:/invoices/" + invoiceId;
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Could not record payment: " + e.getMessage());
            return "redirect:/payments/new";
        }
    }

    @GetMapping("/customer")
    public String customerPaymentForm(@RequestParam(required = false) Long customerId, 
                                      jakarta.servlet.http.HttpSession session,
                                      Model model) {
        model.addAttribute("customers", customerService.getAllCustomers());
        model.addAttribute("methods", PaymentMethod.values());
        model.addAttribute("bankAccounts", bankAccountChoices());
        model.addAttribute("arAccounts", arAccountChoices());
        model.addAttribute("active", "payments");
        model.addAttribute("selectedCustomerId", customerId);

        Long defaultBank = (Long) session.getAttribute("lastBankAccountId");
        Long defaultAr = (Long) session.getAttribute("lastArAccountId");
        model.addAttribute("defaultBankAccountId", defaultBank != null ? defaultBank : -1L);
        model.addAttribute("defaultArAccountId", defaultAr != null ? defaultAr : -1L);

        return "payments/customer_payment";
    }

    @GetMapping("/customer/{customerId}/details")
    @ResponseBody
    public com.cogitosum.dto.CustomerPaymentDetailsDTO getCustomerDetails(@PathVariable Long customerId) {
        return customerService.getPaymentDetails(customerId);
    }

    @PostMapping("/customer")
    public String createCustomerPayment(@RequestParam Long customerId,
                                        @RequestParam Long bankAccountId,
                                        @RequestParam Long arAccountId,
                                        @RequestParam java.time.LocalDate paymentDate,
                                        @RequestParam PaymentMethod paymentMethod,
                                        @RequestParam(required = false) String transactionId,
                                        @RequestParam(required = false) String notes,
                                        @RequestParam(required = false) List<Long> invoiceIds,
                                        @RequestParam(required = false) List<BigDecimal> invoiceAmounts,
                                        jakarta.servlet.http.HttpSession session,
                                        RedirectAttributes ra) {
        try {
            if (invoiceIds == null || invoiceIds.isEmpty()) {
                throw new IllegalArgumentException("No invoices selected");
            }

            ChartOfAccount bankAccount = accountService.getAccountById(bankAccountId)
                    .orElseThrow(() -> new IllegalArgumentException("Bank account not found"));
            session.setAttribute("lastBankAccountId", bankAccountId);

            ChartOfAccount arAccount = accountService.getAccountById(arAccountId)
                    .orElseThrow(() -> new IllegalArgumentException("A/R account not found"));
            session.setAttribute("lastArAccountId", arAccountId);
            
            for (int i = 0; i < invoiceIds.size(); i++) {
                Long invId = invoiceIds.get(i);
                BigDecimal amount = invoiceAmounts.get(i);
                
                if (amount.compareTo(BigDecimal.ZERO) <= 0) continue;

                Invoice invoice = invoiceService.getInvoiceById(invId)
                        .orElseThrow(() -> new IllegalArgumentException("Invoice not found: " + invId));

                Payment payment = new Payment();
                payment.setInvoice(invoice);
                payment.setAmount(amount);
                payment.setPaymentMethod(paymentMethod);
                payment.setPaymentDate(paymentDate);
                payment.setTransactionId(transactionId);
                payment.setNotes(notes);
                payment.setBankAccount(bankAccount);
                payment.setArAccount(arAccount);

                paymentService.recordPayment(payment);
            }

            ra.addFlashAttribute("flashSuccess", "Payments recorded successfully");
            return "redirect:/payments";
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Could not record payments: " + e.getMessage());
            return "redirect:/payments/customer?customerId=" + customerId;
        }
    }

    /** Only ASSET accounts whose number starts with "10" (bank / cash range) — A/R (1100) excluded. */
    private java.util.List<ChartOfAccount> bankAccountChoices() {
        return accountService.getAllAccounts().stream()
                .filter(a -> a.getCategory() == com.cogitosum.entity.AccountCategory.BANK)
                .toList();
    }

    private java.util.List<ChartOfAccount> arAccountChoices() {
        return accountService.getAllAccounts().stream()
                .filter(a -> a.getCategory() == com.cogitosum.entity.AccountCategory.ACCOUNTS_RECEIVABLE)
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
