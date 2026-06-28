package com.cogitosum.web;

import com.cogitosum.entity.AccountType;
import com.cogitosum.entity.ChartOfAccount;
import com.cogitosum.entity.Transfer;
import com.cogitosum.service.ChartOfAccountService;
import com.cogitosum.service.TransferService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/transfers")
public class TransferWebController {

    @Autowired private TransferService transferService;
    @Autowired private ChartOfAccountService accountService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("transfers", transferService.getAllTransfers());
        return "transfers/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        List<ChartOfAccount> bankAccounts = accountService.getAccountsByType(AccountType.ASSET).stream()
                .filter(a -> a.getAccountNumber() != null && a.getAccountNumber().startsWith("10"))
                .toList();
        model.addAttribute("bankAccounts", bankAccounts);
        return "transfers/form";
    }

    @PostMapping
    public String create(@RequestParam Long fromAccountId,
                         @RequestParam Long toAccountId,
                         @RequestParam BigDecimal amount,
                         @RequestParam(required = false) LocalDate transferDate,
                         @RequestParam(required = false) String notes,
                         RedirectAttributes ra) {
        try {
            Transfer t = transferService.createTransfer(fromAccountId, toAccountId, amount, transferDate, notes, "portal");
            ra.addFlashAttribute("flashSuccess", "Transfer recorded (" + t.getId() + ")");
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Could not record transfer: " + e.getMessage());
        }
        return "redirect:/transfers";
    }
}
