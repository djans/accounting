package com.cogitosum.web;

import com.cogitosum.entity.*;
import com.cogitosum.service.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/cheques")
public class ChequeWebController {
    private final ChequeService chequeService;
    private final VendorService vendorService;
    private final CustomerService customerService;
    private final ChartOfAccountService accountService;
    private final TaxCodeService taxCodeService;

    public ChequeWebController(ChequeService chequeService, VendorService vendorService,
                               CustomerService customerService, ChartOfAccountService accountService,
                               TaxCodeService taxCodeService) {
        this.chequeService = chequeService; this.vendorService = vendorService;
        this.customerService = customerService; this.accountService = accountService;
        this.taxCodeService = taxCodeService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("cheques", chequeService.findAll());
        return "cheques/list";
    }

    @GetMapping("/new")
    public String newCheque(Model model) {
        model.addAttribute("vendors", vendorService.getAllVendors());
        model.addAttribute("customers", customerService.getAllCustomers());
        model.addAttribute("accounts", accountService.getAllAccounts().stream().filter(a -> Boolean.TRUE.equals(a.getActive())).toList());
        model.addAttribute("bankAccounts", bankAccounts());
        model.addAttribute("taxCodes", taxCodeService.getActiveCodes());
        model.addAttribute("nextChequeNumber", chequeService.nextNumber());
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("active", "cheques-new");
        return "cheques/form";
    }

    @PostMapping
    public String create(@RequestParam String chequeNumber, @RequestParam LocalDate chequeDate,
                         @RequestParam Long vendorId, @RequestParam Long bankAccountId,
                         @RequestParam(required = false) String memo,
                         @RequestParam(name = "expenseAccountId") List<Long> expenseAccountIds,
                         @RequestParam(name = "expenseAmount") List<BigDecimal> expenseAmounts,
                         @RequestParam(name = "expenseTax", required = false) List<String> expenseTaxes,
                         @RequestParam(name = "expenseMemo", required = false) List<String> expenseMemos,
                         @RequestParam(name = "expenseCustomerId", required = false) List<Long> expenseCustomerIds,
                         RedirectAttributes ra) {
        try {
            WrittenCheque cheque = new WrittenCheque();
            cheque.setChequeNumber(chequeNumber); cheque.setChequeDate(chequeDate); cheque.setMemo(memo);
            cheque.setVendor(vendorService.getVendorById(vendorId).orElseThrow());
            cheque.setBankAccount(accountService.getAccountById(bankAccountId).orElseThrow());
            List<ChequeExpense> expenses = new ArrayList<>();
            for (int i = 0; i < expenseAccountIds.size(); i++) {
                ChequeExpense expense = new ChequeExpense();
                expense.setAccount(accountService.getAccountById(expenseAccountIds.get(i)).orElseThrow());
                expense.setAmount(expenseAmounts.get(i));
                expense.setTax(valueAt(expenseTaxes, i)); expense.setMemo(valueAt(expenseMemos, i));
                Long customerId = valueAt(expenseCustomerIds, i);
                if (customerId != null) expense.setCustomerJob(customerService.getCustomerById(customerId).orElseThrow());
                expenses.add(expense);
            }
            cheque.setExpenses(expenses);
            chequeService.saveAndPost(cheque);
            ra.addFlashAttribute("flashSuccess", "Cheque recorded");
            return "redirect:/cheques";
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Could not record cheque: " + e.getMessage());
            return "redirect:/cheques/new";
        }
    }

    private List<ChartOfAccount> bankAccounts() {
        return accountService.getAllAccounts().stream()
                .filter(a -> Boolean.TRUE.equals(a.getActive()) && a.getAccountType() == AccountType.ASSET
                        && a.getCategory() == AccountCategory.BANK).toList();
    }
    private static <T> T valueAt(List<T> values, int index) {
        return values != null && index < values.size() ? values.get(index) : null;
    }
}
