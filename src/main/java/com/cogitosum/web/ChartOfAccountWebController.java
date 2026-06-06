package com.cogitosum.web;

import com.cogitosum.entity.AccountType;
import com.cogitosum.entity.ChartOfAccount;
import com.cogitosum.service.ChartOfAccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/accounts")
public class ChartOfAccountWebController {

    @Autowired
    private ChartOfAccountService chartOfAccountService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("accounts", chartOfAccountService.getAllAccounts());
        return "accounts/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        ChartOfAccount a = new ChartOfAccount();
        a.setActive(true);
        model.addAttribute("account", a);
        model.addAttribute("types", AccountType.values());
        model.addAttribute("isNew", true);
        return "accounts/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model, RedirectAttributes ra) {
        return chartOfAccountService.getAccountById(id)
                .map(a -> {
                    model.addAttribute("account", a);
                    model.addAttribute("types", AccountType.values());
                    model.addAttribute("isNew", false);
                    return "accounts/form";
                })
                .orElseGet(() -> {
                    ra.addFlashAttribute("flashError", "Account not found");
                    return "redirect:/accounts";
                });
    }

    @PostMapping
    public String create(@ModelAttribute ChartOfAccount account, RedirectAttributes ra) {
        try {
            chartOfAccountService.createAccount(account);
            ra.addFlashAttribute("flashSuccess", "Account created and added to the General Ledger");
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Could not create account: " + e.getMessage());
        }
        return "redirect:/accounts";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @ModelAttribute ChartOfAccount account, RedirectAttributes ra) {
        try {
            chartOfAccountService.updateAccount(id, account);
            ra.addFlashAttribute("flashSuccess", "Account updated");
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Could not update account: " + e.getMessage());
        }
        return "redirect:/accounts";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            chartOfAccountService.deleteAccount(id);
            ra.addFlashAttribute("flashSuccess", "Account deleted");
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Could not delete account: " + e.getMessage());
        }
        return "redirect:/accounts";
    }
}
