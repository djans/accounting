package com.cogitosum.web;

import com.cogitosum.entity.*;
import com.cogitosum.service.CreditCardChargeService;
import com.cogitosum.service.BillService;
import com.cogitosum.service.ChartOfAccountService;
import com.cogitosum.service.VendorService;
import com.cogitosum.service.TaxRegime;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Controller
@RequestMapping("/credit-card-charges")
public class CreditCardChargeWebController {
    private final CreditCardChargeService chargeService;
    private final VendorService vendorService;
    private final ChartOfAccountService accountService;
    private final BillService billService;
    private final MessageSource messageSource;

    public CreditCardChargeWebController(CreditCardChargeService chargeService, VendorService vendorService,
                                         ChartOfAccountService accountService, BillService billService,
                                         MessageSource messageSource) {
        this.chargeService = chargeService;
        this.vendorService = vendorService;
        this.accountService = accountService;
        this.billService = billService;
        this.messageSource = messageSource;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("charges", chargeService.findAll());
        return "credit-card-charges/list";
    }

    @GetMapping("/new")
    public String form(@RequestParam(required = false) Long reissueOfId,
                       Model model, RedirectAttributes ra) {
        CreditCardCharge charge = new CreditCardCharge();
        if (reissueOfId != null) {
            try {
                CreditCardCharge original = chargeService.getVoidedForReissue(reissueOfId);
                charge.setVendor(original.getVendor());
                charge.setChargeDate(LocalDate.now());
                charge.setMemo(original.getMemo());
                charge.setExpenseAccount(original.getExpenseAccount());
                charge.setCardAccount(original.getCardAccount());
                charge.setTotalAmount(original.getTotalAmount());
                charge.setTaxRegime(original.getTaxRegime());
            } catch (IllegalArgumentException | IllegalStateException e) {
                ra.addFlashAttribute("flashError", message("creditCardCharges.invalidReissue", e.getMessage()));
                return "redirect:/credit-card-charges";
            }
        }
        populateForm(model, charge, reissueOfId, false);
        return "credit-card-charges/form";
    }

    @GetMapping("/{id}/copy")
    public String copyCharge(@PathVariable Long id, Model model, RedirectAttributes ra) {
        try {
            CreditCardCharge original = chargeService.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Credit card charge not found"));
            CreditCardCharge copy = new CreditCardCharge();
            copy.setVendor(original.getVendor());
            copy.setMemo(original.getMemo());
            copy.setExpenseAccount(original.getExpenseAccount());
            copy.setCardAccount(original.getCardAccount());
            copy.setTotalAmount(original.getTotalAmount());
            copy.setTaxRegime(original.getTaxRegime());
            populateForm(model, copy, null, true);
            return "credit-card-charges/form";
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("flashError", message("creditCardCharges.copyError", e.getMessage()));
            return "redirect:/credit-card-charges";
        }
    }

    @GetMapping("/{id}/edit")
    public String editDraft(@PathVariable Long id, Model model, RedirectAttributes ra) {
        try {
            populateForm(model, chargeService.getDraftForEdit(id), null, false);
            return "credit-card-charges/form";
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("flashError", message("creditCardCharges.notEditable", e.getMessage()));
            return "redirect:/credit-card-charges";
        }
    }

    private void populateForm(Model model, CreditCardCharge charge, Long reissueOfId, boolean copyCharge) {
        model.addAttribute("vendors", vendorService.getAllVendors());
        model.addAttribute("expenseAccounts", expenseAccounts());
        model.addAttribute("cardAccounts", accountService.getAccountsByType(AccountType.LIABILITY));
        model.addAttribute("regimes", billService.getRegimes());
        model.addAttribute("charge", charge);
        model.addAttribute("reissueOfId", reissueOfId);
        model.addAttribute("copyCharge", copyCharge);
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("active", "credit-card-charges");
    }

    @PostMapping
    public String create(@RequestParam(required = false) Long reissueOfId,
                         @RequestParam Long vendorId, @RequestParam LocalDate chargeDate,
                         @RequestParam(required = false) String memo, @RequestParam Long expenseAccountId,
                         @RequestParam Long cardAccountId, @RequestParam BigDecimal totalAmount,
                         @RequestParam String taxRegime, RedirectAttributes ra) {
        try {
            chargeService.saveDraft(
                    vendorService.getVendorById(vendorId).orElseThrow(() -> new IllegalArgumentException("Vendor not found")),
                    chargeDate, memo,
                    accountService.getAccountById(expenseAccountId).orElseThrow(() -> new IllegalArgumentException("Expense account not found")),
                    accountService.getAccountById(cardAccountId).orElseThrow(() -> new IllegalArgumentException("Card account not found")),
                    totalAmount, regimeForCode(taxRegime), reissueOfId);
            ra.addFlashAttribute("flashSuccess", message("creditCardCharges.draftSaved"));
            return "redirect:/credit-card-charges";
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("flashError", message("creditCardCharges.draftSaveError", e.getMessage()));
            return "redirect:/credit-card-charges/new";
        }
    }

    @PostMapping("/{id}")
    public String updateDraft(@PathVariable Long id,
                              @RequestParam Long vendorId, @RequestParam LocalDate chargeDate,
                              @RequestParam(required = false) String memo,
                              @RequestParam Long expenseAccountId, @RequestParam Long cardAccountId,
                              @RequestParam BigDecimal totalAmount, @RequestParam String taxRegime,
                              RedirectAttributes ra) {
        try {
            chargeService.updateDraft(id,
                    vendorService.getVendorById(vendorId).orElseThrow(() -> new IllegalArgumentException("Vendor not found")),
                    chargeDate, memo,
                    accountService.getAccountById(expenseAccountId).orElseThrow(() -> new IllegalArgumentException("Expense account not found")),
                    accountService.getAccountById(cardAccountId).orElseThrow(() -> new IllegalArgumentException("Card account not found")),
                    totalAmount, regimeForCode(taxRegime));
            ra.addFlashAttribute("flashSuccess", message("creditCardCharges.draftUpdated"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("flashError", message("creditCardCharges.draftUpdateError", e.getMessage()));
        }
        return "redirect:/credit-card-charges";
    }

    @PostMapping("/{id}/delete")
    public String deleteDraft(@PathVariable Long id, RedirectAttributes ra) {
        try {
            chargeService.deleteDraft(id);
            ra.addFlashAttribute("flashSuccess", message("creditCardCharges.draftDeleted"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("flashError", message("creditCardCharges.draftDeleteError", e.getMessage()));
        }
        return "redirect:/credit-card-charges";
    }

    @PostMapping("/bulk-post")
    public String postSelected(@RequestParam(required = false) List<Long> chargeIds, RedirectAttributes ra) {
        try {
            CreditCardChargeService.BulkActionResult result = chargeService.postDrafts(chargeIds);
            ra.addFlashAttribute("flashSuccess", message(
                    "creditCardCharges.bulkPostResult", result.processed(), result.skipped()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("flashError", message("creditCardCharges.bulkPostError", e.getMessage()));
        }
        return "redirect:/credit-card-charges";
    }

    @PostMapping("/bulk-delete")
    public String deleteSelected(@RequestParam(required = false) List<Long> chargeIds, RedirectAttributes ra) {
        try {
            CreditCardChargeService.BulkActionResult result = chargeService.deleteDrafts(chargeIds);
            ra.addFlashAttribute("flashSuccess", message(
                    "creditCardCharges.bulkDeleteResult", result.processed(), result.skipped()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("flashError", message("creditCardCharges.bulkDeleteError", e.getMessage()));
        }
        return "redirect:/credit-card-charges";
    }

    @PostMapping("/{id}/post")
    public String post(@PathVariable Long id, RedirectAttributes ra) {
        try {
            chargeService.post(id);
            ra.addFlashAttribute("flashSuccess", message("creditCardCharges.posted"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("flashError", message("creditCardCharges.postError", e.getMessage()));
        }
        return "redirect:/credit-card-charges";
    }

    @PostMapping("/{id}/void")
    public String voidCharge(@PathVariable Long id, @RequestParam String reason, RedirectAttributes ra) {
        try {
            chargeService.voidCharge(id, reason);
            ra.addFlashAttribute("flashSuccess", message("creditCardCharges.voided"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("flashError", message("creditCardCharges.voidError", e.getMessage()));
        }
        return "redirect:/credit-card-charges";
    }

    private TaxRegime regimeForCode(String code) {
        return billService.getRegimes().stream()
                .filter(regime -> regime.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown tax regime"));
    }

    private String message(String code, Object... args) {
        return messageSource.getMessage(code, args, LocaleContextHolder.getLocale());
    }

    private List<ChartOfAccount> expenseAccounts() {
        List<ChartOfAccount> accounts = new ArrayList<>(accountService.getAccountsByType(AccountType.EXPENSE));
        accounts.addAll(accountService.getAccountsByType(AccountType.ASSET));
        accounts.sort(Comparator.comparing(ChartOfAccount::getAccountNumber));
        return accounts;
    }
}
