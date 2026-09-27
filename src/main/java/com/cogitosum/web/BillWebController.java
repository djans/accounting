package com.cogitosum.web;

import com.cogitosum.entity.*;
import com.cogitosum.service.BillPaymentService;
import com.cogitosum.service.BillService;
import com.cogitosum.service.ChartOfAccountService;
import com.cogitosum.service.DocumentAttachmentService;
import com.cogitosum.service.VendorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Controller
@RequestMapping("/bills")
public class BillWebController {

    @Autowired private BillService billService;
    @Autowired private VendorService vendorService;
    @Autowired private BillPaymentService billPaymentService;
    @Autowired private ChartOfAccountService accountService;
    @Autowired private DocumentAttachmentService attachmentService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("bills", billService.getAllBills());
        return "bills/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("vendors", vendorService.getAllVendors());
        model.addAttribute("regimes", billService.getRegimes());
        model.addAttribute("expenseAccounts", expenseAccountChoices());
        return "bills/form";
    }

    @PostMapping
    public String create(@RequestParam Long vendorId,
                         @RequestParam(required = false) String taxRegime,
                         @RequestParam(required = false) String notes,
                         @RequestParam(required = false) List<String> descriptions,
                         @RequestParam(required = false) List<BigDecimal> quantities,
                         @RequestParam(required = false) List<BigDecimal> unitPrices,
                         @RequestParam(required = false) List<Long> accountIds,
                         RedirectAttributes ra) {
        try {
            Vendor vendor = vendorService.getVendorById(vendorId)
                    .orElseThrow(() -> new IllegalArgumentException("Vendor not found"));

            Bill bill = new Bill();
            bill.setVendor(vendor);
            bill.setNotes(notes);
            bill.setStatus(BillStatus.DRAFT);

            List<BillLineItem> items = new ArrayList<>();
            if (descriptions != null) {
                for (int i = 0; i < descriptions.size(); i++) {
                    String desc = descriptions.get(i);
                    if (desc == null || desc.isBlank()) continue;
                    BillLineItem li = new BillLineItem();
                    li.setBill(bill);
                    li.setDescription(desc);
                    li.setQuantity(quantities.get(i));
                    li.setUnitPrice(unitPrices.get(i));
                    Long accountId = (accountIds != null && i < accountIds.size()) ? accountIds.get(i) : null;
                    if (accountId != null) {
                        li.setExpenseAccount(accountService.getAccountById(accountId).orElse(null));
                    }
                    items.add(li);
                }
            }
            if (items.isEmpty()) {
                throw new IllegalArgumentException("Bill must have at least one line item");
            }
            bill.setLineItems(items);

            Bill saved = billService.createBill(bill, taxRegime);
            ra.addFlashAttribute("flashSuccess", "Bill " + saved.getBillNumber() + " created");
            return "redirect:/bills/" + saved.getId();
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Could not create bill: " + e.getMessage());
            return "redirect:/bills/new";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model, RedirectAttributes ra) {
        return billService.getBillById(id)
                .map(bill -> {
                    model.addAttribute("bill", bill);
                    model.addAttribute("payments", billPaymentService.getPaymentsByBillId(id));
                    model.addAttribute("methods", PaymentMethod.values());
                    model.addAttribute("bankAccounts", bankAccountChoices());
                    model.addAttribute("attachments", attachmentService.listBillAttachments(id));
                    return "bills/detail";
                })
                .orElseGet(() -> {
                    ra.addFlashAttribute("flashError", "Bill not found");
                    return "redirect:/bills";
                });
    }

    @PostMapping("/{id}/receive")
    public String receive(@PathVariable Long id, RedirectAttributes ra) {
        billService.markBillAsReceived(id);
        ra.addFlashAttribute("flashSuccess", "Bill marked as received");
        return "redirect:/bills/" + id;
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id, RedirectAttributes ra) {
        try {
            billService.cancelBill(id);
            ra.addFlashAttribute("flashSuccess", "Bill cancelled");
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Could not cancel bill: " + e.getMessage());
        }
        return "redirect:/bills/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            billService.deleteBill(id);
            ra.addFlashAttribute("flashSuccess", "Bill deleted");
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Could not delete bill: " + e.getMessage());
        }
        return "redirect:/bills";
    }

    @PostMapping("/{id}/pay")
    public String pay(@PathVariable Long id,
                      @RequestParam(required = false) Long bankAccountId,
                      @RequestParam BigDecimal amount,
                      @RequestParam PaymentMethod paymentMethod,
                      @RequestParam(required = false) String transactionId,
                      @RequestParam(required = false) String notes,
                      RedirectAttributes ra) {
        try {
            Bill bill = billService.getBillById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Bill not found"));

            BillPayment payment = new BillPayment();
            payment.setBill(bill);
            payment.setAmount(amount);
            payment.setPaymentMethod(paymentMethod);
            payment.setTransactionId(transactionId);
            payment.setNotes(notes);
            if (bankAccountId != null) {
                payment.setBankAccount(accountService.getAccountById(bankAccountId)
                        .orElseThrow(() -> new IllegalArgumentException("Bank account not found")));
            }

            billPaymentService.recordPayment(payment);
            ra.addFlashAttribute("flashSuccess", "Payment recorded");
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Could not record payment: " + e.getMessage());
        }
        return "redirect:/bills/" + id;
    }

    /** Expense and asset accounts a purchase line can be charged to. */
    private List<ChartOfAccount> expenseAccountChoices() {
        List<ChartOfAccount> accounts = new ArrayList<>(accountService.getAccountsByType(AccountType.EXPENSE));
        accounts.addAll(accountService.getAccountsByType(AccountType.ASSET));
        accounts.sort(Comparator.comparing(ChartOfAccount::getAccountNumber));
        return accounts;
    }

    /** Only ASSET accounts whose number starts with "10" (bank / cash range). */
    private List<ChartOfAccount> bankAccountChoices() {
        return accountService.getAccountsByType(AccountType.ASSET).stream()
                .filter(a -> a.getAccountNumber() != null && a.getAccountNumber().startsWith("10"))
                .toList();
    }
}
