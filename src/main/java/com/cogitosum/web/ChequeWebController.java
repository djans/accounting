package com.cogitosum.web;

import com.cogitosum.entity.*;
import com.cogitosum.service.*;
import org.springframework.stereotype.Controller;
import org.springframework.context.MessageSource;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/cheques")
public class ChequeWebController {
    private final ChequeService chequeService;
    private final VendorService vendorService;
    private final CustomerService customerService;
    private final ChartOfAccountService accountService;
    private final TaxCodeService taxCodeService;
    private final MessageSource messageSource;

    public ChequeWebController(ChequeService chequeService, VendorService vendorService,
                               CustomerService customerService, ChartOfAccountService accountService,
                               TaxCodeService taxCodeService, MessageSource messageSource) {
        this.chequeService = chequeService; this.vendorService = vendorService;
        this.customerService = customerService; this.accountService = accountService;
        this.taxCodeService = taxCodeService; this.messageSource = messageSource;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("cheques", chequeService.findAll());
        return "cheques/list";
    }

    @GetMapping("/new")
    public String newCheque(@RequestParam(required = false) Long reissueOfId,
                            Model model, RedirectAttributes ra) {
        WrittenCheque cheque = new WrittenCheque();
        if (reissueOfId != null) {
            try {
                WrittenCheque original = chequeService.getVoidedForReissue(reissueOfId);
                cheque.setReissueOf(original);
                cheque.setChequeDate(LocalDate.now());
                cheque.setVendor(original.getVendor());
                cheque.setBankAccount(original.getBankAccount());
                cheque.setMemo(original.getMemo());
                List<ChequeExpense> expenses = original.getExpenses().stream().map(source -> {
                    ChequeExpense expense = new ChequeExpense();
                    expense.setAccount(source.getAccount());
                    expense.setTax(source.getTax());
                    expense.setAmount(source.getAmount());
                    expense.setMemo(source.getMemo());
                    expense.setCustomerJob(source.getCustomerJob());
                    return expense;
                }).toList();
                cheque.setExpenses(new ArrayList<>(expenses));
            } catch (IllegalArgumentException | IllegalStateException e) {
                ra.addFlashAttribute("flashError", message("cheques.invalidReissue", e.getMessage()));
                return "redirect:/cheques";
            }
        }
        populateForm(model, cheque, reissueOfId);
        return "cheques/form";
    }

    @GetMapping("/{id}/edit")
    public String editDraft(@PathVariable Long id, Model model, RedirectAttributes ra) {
        try {
            populateForm(model, chequeService.getDraftForEdit(id), null);
            return "cheques/form";
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("flashError", message("cheques.notEditable", e.getMessage()));
            return "redirect:/cheques";
        }
    }

    private void populateForm(Model model, WrittenCheque cheque, Long reissueOfId) {
        List<TaxCode> taxCodes = taxCodeService.getActiveCodes();
        model.addAttribute("vendors", vendorService.getAllVendors());
        model.addAttribute("customers", customerService.getAllCustomers());
        model.addAttribute("accounts", accountService.getAllAccounts().stream().filter(a -> Boolean.TRUE.equals(a.getActive())).toList());
        model.addAttribute("bankAccounts", bankAccounts());
        model.addAttribute("taxCodes", taxCodes);
        model.addAttribute("purchaseTaxRates", purchaseTaxRates(taxCodes));
        model.addAttribute("nextChequeNumber", chequeService.nextNumber());
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("active", "cheques-new");
        model.addAttribute("cheque", cheque);
        model.addAttribute("reissueOfId", reissueOfId);
    }

    @PostMapping
    public String create(@RequestParam(required = false) Long reissueOfId,
                         @RequestParam String chequeNumber, @RequestParam LocalDate chequeDate,
                         @RequestParam Long vendorId, @RequestParam Long bankAccountId,
                         @RequestParam(required = false) String memo,
                         @RequestParam(name = "expenseAccountId") List<Long> expenseAccountIds,
                         @RequestParam(name = "expenseAmount") List<BigDecimal> expenseAmounts,
                         @RequestParam(name = "expenseTax", required = false) List<String> expenseTaxes,
                         @RequestParam(name = "expenseMemo", required = false) List<String> expenseMemos,
                         @RequestParam(name = "expenseCustomerId", required = false) List<Long> expenseCustomerIds,
                         RedirectAttributes ra) {
        try {
            chequeService.saveDraft(buildCheque(chequeNumber, chequeDate, vendorId, bankAccountId,
                    memo, expenseAccountIds, expenseAmounts, expenseTaxes, expenseMemos, expenseCustomerIds),
                    reissueOfId);
            ra.addFlashAttribute("flashSuccess", message("cheques.flash.draftSaved"));
            return "redirect:/cheques";
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("flashError", message("cheques.flash.draftSaveError", e.getMessage()));
            return "redirect:/cheques/new";
        }
    }

    @PostMapping("/{id}")
    public String updateDraft(@PathVariable Long id,
                              @RequestParam String chequeNumber, @RequestParam LocalDate chequeDate,
                              @RequestParam Long vendorId, @RequestParam Long bankAccountId,
                              @RequestParam(required = false) String memo,
                              @RequestParam(name = "expenseAccountId") List<Long> expenseAccountIds,
                              @RequestParam(name = "expenseAmount") List<BigDecimal> expenseAmounts,
                              @RequestParam(name = "expenseTax", required = false) List<String> expenseTaxes,
                              @RequestParam(name = "expenseMemo", required = false) List<String> expenseMemos,
                              @RequestParam(name = "expenseCustomerId", required = false) List<Long> expenseCustomerIds,
                              RedirectAttributes ra) {
        try {
            chequeService.updateDraft(id, buildCheque(chequeNumber, chequeDate, vendorId, bankAccountId,
                    memo, expenseAccountIds, expenseAmounts, expenseTaxes, expenseMemos, expenseCustomerIds));
            ra.addFlashAttribute("flashSuccess", message("cheques.flash.draftUpdated"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("flashError", message("cheques.flash.draftUpdateError", e.getMessage()));
        }
        return "redirect:/cheques";
    }

    @PostMapping("/{id}/delete")
    public String deleteDraft(@PathVariable Long id, RedirectAttributes ra) {
        try {
            chequeService.deleteDraft(id);
            ra.addFlashAttribute("flashSuccess", message("cheques.flash.draftDeleted"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("flashError", message("cheques.flash.draftDeleteError", e.getMessage()));
        }
        return "redirect:/cheques";
    }

    @PostMapping("/{id}/issue")
    public String issue(@PathVariable Long id, RedirectAttributes ra) {
        try {
            chequeService.issue(id);
            ra.addFlashAttribute("flashSuccess", message("cheques.flash.issued"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("flashError", message("cheques.flash.issueError", e.getMessage()));
        }
        return "redirect:/cheques";
    }

    @PostMapping("/{id}/void")
    public String voidCheque(@PathVariable Long id, @RequestParam String reason, RedirectAttributes ra) {
        try {
            chequeService.voidCheque(id, reason);
            ra.addFlashAttribute("flashSuccess", message("cheques.flash.voided"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("flashError", message("cheques.flash.voidError", e.getMessage()));
        }
        return "redirect:/cheques";
    }

    private WrittenCheque buildCheque(String chequeNumber, LocalDate chequeDate, Long vendorId,
                                      Long bankAccountId, String memo, List<Long> expenseAccountIds,
                                      List<BigDecimal> expenseAmounts, List<String> expenseTaxes,
                                      List<String> expenseMemos, List<Long> expenseCustomerIds) {
        if (expenseAccountIds.size() != expenseAmounts.size()) {
            throw new IllegalArgumentException("Each expense account must have an amount");
        }
        WrittenCheque cheque = new WrittenCheque();
        cheque.setChequeNumber(chequeNumber);
        cheque.setChequeDate(chequeDate);
        cheque.setMemo(memo);
        cheque.setVendor(vendorService.getVendorById(vendorId)
                .orElseThrow(() -> new IllegalArgumentException("Vendor not found")));
        cheque.setBankAccount(accountService.getAccountById(bankAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Bank account not found")));
        List<ChequeExpense> expenses = new ArrayList<>();
        for (int i = 0; i < expenseAccountIds.size(); i++) {
            ChequeExpense expense = new ChequeExpense();
            expense.setAccount(accountService.getAccountById(expenseAccountIds.get(i))
                    .orElseThrow(() -> new IllegalArgumentException("Expense account not found")));
            expense.setAmount(expenseAmounts.get(i));
            expense.setTax(valueAt(expenseTaxes, i));
            expense.setMemo(valueAt(expenseMemos, i));
            Long customerId = valueAt(expenseCustomerIds, i);
            if (customerId != null) {
                expense.setCustomerJob(customerService.getCustomerById(customerId)
                        .orElseThrow(() -> new IllegalArgumentException("Customer/job not found")));
            }
            expenses.add(expense);
        }
        cheque.setExpenses(expenses);
        return cheque;
    }

    private List<ChartOfAccount> bankAccounts() {
        return accountService.getAllAccounts().stream()
                .filter(a -> Boolean.TRUE.equals(a.getActive()) && a.getAccountType() == AccountType.ASSET
                        && a.getCategory() == AccountCategory.BANK).toList();
    }

    private Map<String, String> purchaseTaxRates(List<TaxCode> taxCodes) {
        Map<String, String> ratesByCode = new HashMap<>();
        for (TaxCode taxCode : taxCodes) {
            TaxGroup group = taxCode.getPurchaseTaxGroup();
            String rates = group == null || group.getTaxItems() == null
                    ? ""
                    : group.getTaxItems().stream()
                            .filter(item -> !Boolean.FALSE.equals(item.getForPurchases()))
                            .map(item -> item.getRate().toPlainString())
                            .collect(Collectors.joining(","));
            ratesByCode.put(taxCode.getCode(), rates);
        }
        return ratesByCode;
    }

    private String message(String code, Object... args) {
        return messageSource.getMessage(code, args, LocaleContextHolder.getLocale());
    }

    private static <T> T valueAt(List<T> values, int index) {
        return values != null && index < values.size() ? values.get(index) : null;
    }
}
