package com.cogitosum.web;

import com.cogitosum.entity.AccountCategory;
import com.cogitosum.entity.AccountType;
import com.cogitosum.entity.ChartOfAccount;
import com.cogitosum.entity.Transfer;
import com.cogitosum.service.ChartOfAccountService;
import com.cogitosum.service.TransferService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
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

    private final TransferService transferService;
    private final ChartOfAccountService accountService;
    private final MessageSource messageSource;

    public TransferWebController(TransferService transferService, ChartOfAccountService accountService,
                                 MessageSource messageSource) {
        this.transferService = transferService;
        this.accountService = accountService;
        this.messageSource = messageSource;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("transfers", transferService.getAllTransfers());
        return "transfers/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        populateForm(model, new Transfer(), false);
        return "transfers/form";
    }

    @GetMapping("/{id}")
    public String details(@PathVariable Long id, Model model, RedirectAttributes ra) {
        try {
            Transfer transfer = transferService.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Transfer not found"));
            model.addAttribute("transfer", transfer);
            model.addAttribute("active", "transfers");
            return "transfers/details";
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("flashError", message("transfers.detailError", e.getMessage()));
            return "redirect:/transfers";
        }
    }

    @GetMapping("/{id}/copy")
    public String copyTransfer(@PathVariable Long id, Model model, RedirectAttributes ra) {
        try {
            Transfer original = transferService.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Transfer not found"));
            Transfer copy = new Transfer();
            copy.setFromAccount(original.getFromAccount());
            copy.setToAccount(original.getToAccount());
            copy.setAmount(original.getAmount());
            copy.setNotes(original.getNotes());
            populateForm(model, copy, true);
            return "transfers/form";
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("flashError", message("transfers.copyError", e.getMessage()));
            return "redirect:/transfers";
        }
    }

    @GetMapping("/{id}/edit")
    public String editDraft(@PathVariable Long id, Model model, RedirectAttributes ra) {
        try {
            populateForm(model, transferService.getDraftForEdit(id), false);
            return "transfers/form";
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("flashError", message("transfers.notEditable", e.getMessage()));
            return "redirect:/transfers";
        }
    }

    @PostMapping
    public String create(@RequestParam Long fromAccountId,
                         @RequestParam Long toAccountId,
                         @RequestParam BigDecimal amount,
                         @RequestParam LocalDate transferDate,
                         @RequestParam(required = false) String notes,
                         RedirectAttributes ra) {
        try {
            transferService.saveDraft(fromAccountId, toAccountId, amount, transferDate, notes);
            ra.addFlashAttribute("flashSuccess", message("transfers.flash.draftSaved"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("flashError", message("transfers.flash.draftSaveError", e.getMessage()));
            return "redirect:/transfers/new";
        }
        return "redirect:/transfers";
    }

    @PostMapping("/{id}")
    public String updateDraft(@PathVariable Long id,
                              @RequestParam Long fromAccountId,
                              @RequestParam Long toAccountId,
                              @RequestParam BigDecimal amount,
                              @RequestParam LocalDate transferDate,
                              @RequestParam(required = false) String notes,
                              RedirectAttributes ra) {
        try {
            transferService.updateDraft(id, fromAccountId, toAccountId, amount, transferDate, notes);
            ra.addFlashAttribute("flashSuccess", message("transfers.flash.draftUpdated"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("flashError", message("transfers.flash.draftUpdateError", e.getMessage()));
        }
        return "redirect:/transfers";
    }

    @PostMapping("/{id}/delete")
    public String deleteDraft(@PathVariable Long id, RedirectAttributes ra) {
        try {
            transferService.deleteDraft(id);
            ra.addFlashAttribute("flashSuccess", message("transfers.flash.draftDeleted"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("flashError", message("transfers.flash.draftDeleteError", e.getMessage()));
        }
        return "redirect:/transfers";
    }

    @PostMapping("/{id}/post")
    public String post(@PathVariable Long id, RedirectAttributes ra) {
        try {
            transferService.postTransfer(id, "portal");
            ra.addFlashAttribute("flashSuccess", message("transfers.flash.posted"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("flashError", message("transfers.flash.postError", e.getMessage()));
        }
        return "redirect:/transfers";
    }

    @PostMapping("/{id}/void")
    public String voidTransfer(@PathVariable Long id, @RequestParam String reason, RedirectAttributes ra) {
        try {
            transferService.voidTransfer(id, reason);
            ra.addFlashAttribute("flashSuccess", message("transfers.flash.reversed"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("flashError", message("transfers.flash.reverseError", e.getMessage()));
        }
        return "redirect:/transfers";
    }

    private void populateForm(Model model, Transfer transfer, boolean copyTransfer) {
        if (!copyTransfer && transfer.getTransferDate() == null) {
            transfer.setTransferDate(LocalDate.now());
        }
        List<ChartOfAccount> transferAccounts = accountService.getActiveAccounts().stream()
                .filter(account -> account.getAccountType() == AccountType.ASSET
                        || account.getAccountType() == AccountType.LIABILITY
                        || account.getAccountType() == AccountType.EQUITY
                        || account.getCategory() == AccountCategory.BANK
                        || account.getCategory() == AccountCategory.CREDIT_CARD)
                .toList();
        model.addAttribute("bankAccounts", transferAccounts);
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("transfer", transfer);
        model.addAttribute("copyTransfer", copyTransfer);
    }

    private String message(String code, Object... args) {
        return messageSource.getMessage(code, args, LocaleContextHolder.getLocale());
    }
}
