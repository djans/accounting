package com.cogitosum.web;

import com.cogitosum.entity.PaymentMethod;
import com.cogitosum.service.PaymentService;
import com.cogitosum.service.InvoiceService;
import com.cogitosum.service.ChartOfAccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/cheques")
public class ChequeWebController {

    @Autowired private PaymentService paymentService;
    @Autowired private InvoiceService invoiceService;
    @Autowired private ChartOfAccountService accountService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("cheques", paymentService.getPaymentsByMethod(PaymentMethod.CHEQUE));
        return "cheques/list";
    }

    @GetMapping("/new")
    public String newCheque(@RequestParam(required = false) Long invoiceId, Model model) {
        // Reuse payments form but preselect payment method = CHEQUE
        model.addAttribute("invoices", invoiceService.getAllInvoices());
        model.addAttribute("methods", PaymentMethod.values());
        model.addAttribute("bankAccounts", bankAccountChoices());
        model.addAttribute("preselectedInvoiceId", invoiceId);
        model.addAttribute("selectedMethod", PaymentMethod.CHEQUE);
        return "payments/form";
    }

    private java.util.List<com.cogitosum.entity.ChartOfAccount> bankAccountChoices() {
        return accountService.getAccountsByType(com.cogitosum.entity.AccountType.ASSET).stream()
                .filter(a -> a.getAccountNumber() != null
                        && a.getAccountNumber().startsWith("10")
                        && !a.getAccountNumber().equals("1100"))
                .toList();
    }
}