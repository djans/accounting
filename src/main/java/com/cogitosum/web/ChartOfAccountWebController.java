package com.cogitosum.web;

import com.cogitosum.entity.AccountType;
import com.cogitosum.entity.AccountCategory;
import com.cogitosum.entity.ChartOfAccount;
import com.cogitosum.entity.GeneralLedger;
import com.cogitosum.service.AccountingReportService;
import com.cogitosum.service.ChartOfAccountService;
import com.cogitosum.service.GeneralLedgerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/accounts")
public class ChartOfAccountWebController {

    @Autowired
    private ChartOfAccountService chartOfAccountService;

    @Autowired
    private AccountingReportService accountingReportService;

    @Autowired
    private GeneralLedgerService generalLedgerService;

    @GetMapping
    public String list(Model model) {
        List<ChartOfAccount> accounts = chartOfAccountService.getAllAccounts();
        Map<Long, GeneralLedger> ledgersByAccountId = new HashMap<>();
        for (GeneralLedger ledger : generalLedgerService.getAllLedgerAccounts()) {
            ledgersByAccountId.put(ledger.getAccount().getId(), ledger);
        }
        Map<Long, BigDecimal> endingBalances = new HashMap<>();
        for (ChartOfAccount account : accounts) {
            BigDecimal openingBalance = account.getOpeningBalance() == null
                    ? BigDecimal.ZERO
                    : account.getOpeningBalance();
            GeneralLedger ledger = ledgersByAccountId.get(account.getId());
            BigDecimal postedBalance = ledger == null || ledger.getBalance() == null
                    ? BigDecimal.ZERO
                    : ledger.getBalance();
            endingBalances.put(account.getId(), openingBalance.add(postedBalance));
        }
        model.addAttribute("accounts", accounts);
        model.addAttribute("accountEndingBalances", endingBalances);
        model.addAttribute("accountTypes", AccountType.values());
        return "accounts/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        ChartOfAccount a = new ChartOfAccount();
        a.setActive(true);
        a.setCurrency("CAD");
        model.addAttribute("account", a);
        model.addAttribute("types", AccountType.values());
        model.addAttribute("categories", AccountCategory.values());
        model.addAttribute("accounts", chartOfAccountService.getAllAccounts());
        model.addAttribute("isNew", true);
        return "accounts/form";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model, RedirectAttributes ra) {
        return chartOfAccountService.getAccountById(id)
                .map(account -> {
                    model.addAttribute("account", account);
                    model.addAttribute("journalEntries", accountingReportService.getAccountTransactions(id));
                    BigDecimal openingBalance = account.getOpeningBalance() == null
                            ? BigDecimal.ZERO
                            : account.getOpeningBalance();
                    BigDecimal postedBalance = generalLedgerService.getLedgerByAccountId(id)
                            .map(GeneralLedger::getBalance)
                            .orElse(BigDecimal.ZERO);
                    model.addAttribute("endingBalance", openingBalance.add(postedBalance));
                    return "accounts/detail";
                })
                .orElseGet(() -> {
                    ra.addFlashAttribute("flashError", "Account not found");
                    return "redirect:/accounts";
                });
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model, RedirectAttributes ra) {
        return chartOfAccountService.getAccountById(id)
                .map(a -> {
                    model.addAttribute("account", a);
                    model.addAttribute("types", AccountType.values());
                    model.addAttribute("categories", AccountCategory.values());
                    model.addAttribute("accounts", chartOfAccountService.getAllAccounts());
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
