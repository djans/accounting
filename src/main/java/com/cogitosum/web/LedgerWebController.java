package com.cogitosum.web;

import com.cogitosum.service.GeneralLedgerService;
import com.cogitosum.service.ChartOfAccountService;
import com.cogitosum.entity.GeneralLedger;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/ledger")
public class LedgerWebController {

    @Autowired
    private GeneralLedgerService generalLedgerService;

    @Autowired
    private ChartOfAccountService chartOfAccountService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("ledgers", generalLedgerService.getAllLedgerAccounts());
        return "ledger/list";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model, RedirectAttributes ra) {
        try {
            model.addAttribute("ledger", generalLedgerService.getLedger(id));
            model.addAttribute("accounts", chartOfAccountService.getAllAccounts());
            return "ledger/form";
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
            return "redirect:/ledger";
        }
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @ModelAttribute("ledger") GeneralLedger ledger,
                         @org.springframework.web.bind.annotation.RequestParam Long accountId,
                         RedirectAttributes ra) {
        try {
            generalLedgerService.updateLedger(id, accountId, ledger.getDebitBalance(), ledger.getCreditBalance());
            ra.addFlashAttribute("flashSuccess", "General Ledger entry updated");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/ledger";
    }
}
