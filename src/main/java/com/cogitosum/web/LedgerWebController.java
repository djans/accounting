package com.cogitosum.web;

import com.cogitosum.entity.AccountType;
import com.cogitosum.entity.GeneralLedger;
import com.cogitosum.service.GeneralLedgerService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Optional;

@Controller
@RequestMapping("/ledger")
public class LedgerWebController {

    @Autowired
    private GeneralLedgerService generalLedgerService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("ledgers", generalLedgerService.getAllLedgerAccounts());
        model.addAttribute("accountTypes", AccountType.values());
        return "ledger/list";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model, RedirectAttributes ra) {
        try {
            return showLedger(generalLedgerService.getLedger(id), model);
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
            return "redirect:/ledger";
        }
    }

    @GetMapping("/select-account")
    public String selectAccount(@RequestParam Long accountId, Model model, RedirectAttributes ra) {
        try {
            Optional<GeneralLedger> ledger = generalLedgerService.getLedgerByAccountId(accountId);
            return showLedger(ledger.orElseThrow(
                    () -> new IllegalArgumentException("No General Ledger entry for account: " + accountId)), model);
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
            return "redirect:/ledger";
        }
    }

    private String showLedger(GeneralLedger ledger, Model model) {
        model.addAttribute("ledger", ledger);
        model.addAttribute("ledgerAccounts", generalLedgerService.getAllLedgerAccounts());
        return "ledger/form";
    }
}
