package com.cogitosum.web;

import com.cogitosum.entity.BankTransaction;
import com.cogitosum.entity.ChartOfAccount;
import com.cogitosum.entity.JournalEntry;
import com.cogitosum.service.BankReconciliationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

@Controller
public class ReconcileWebController {

    private final BankReconciliationService reconciliationService;

    public ReconcileWebController(BankReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @GetMapping("/reconcile")
    public String reconcile(Model model) {
        model.addAttribute("rows", reconciliationService.getBankAccounts());
        model.addAttribute("active", "reconcile");
        return "reconcile";
    }

    @GetMapping("/reconcile/account/{id}")
    public String reconcileAccount(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            ChartOfAccount account = reconciliationService.getBankAccount(id);
            List<BankTransaction> transactions = reconciliationService.getUnreconciledTransactions(id);
            List<JournalEntry> entries = reconciliationService.getEligibleJournalEntries(id);
            model.addAttribute("account", account);
            model.addAttribute("bankTransactions", transactions);
            model.addAttribute("candidateEntries", candidates(transactions, entries));
            model.addAttribute("openingBalance", reconciliationService.getOpeningBalance(id));
            model.addAttribute("statementDate", LocalDate.now());
            model.addAttribute("active", "reconcile");
            return "reconcile_details";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("flashError", ex.getMessage());
            return "redirect:/reconcile";
        }
    }

    @PostMapping("/reconcile/account/{id}/import")
    public String importCsv(@PathVariable Long id,
                            @RequestParam("file") MultipartFile file,
                            RedirectAttributes redirectAttributes) {
        try {
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("Please select a non-empty CSV file");
            }
            BankReconciliationService.ImportResult result = reconciliationService.importCsv(id, file.getBytes());
            redirectAttributes.addFlashAttribute("flashSuccess",
                    result.importedCount() + " bank transaction(s) imported");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("flashError", "CSV import failed: " + ex.getMessage());
        }
        return "redirect:/reconcile/account/" + id;
    }

    @PostMapping("/reconcile/finish")
    public String finishReconciliation(@RequestParam Long accountId,
                                       @RequestParam String statementDate,
                                       @RequestParam String statementEndingBalance,
                                       @RequestParam MultiValueMap<String, String> parameters,
                                       RedirectAttributes redirectAttributes) {
        try {
            reconciliationService.reconcile(
                    accountId,
                    parseDate(statementDate),
                    new BigDecimal(statementEndingBalance),
                    parseMatches(parameters));
            redirectAttributes.addFlashAttribute("flashSuccess", "Bank reconciliation completed");
            return "redirect:/reconcile";
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("flashError", "Reconciliation failed: " + ex.getMessage());
            return "redirect:/reconcile/account/" + accountId;
        }
    }

    private Map<Long, List<JournalEntry>> candidates(List<BankTransaction> transactions,
                                                      List<JournalEntry> entries) {
        Map<Long, List<JournalEntry>> candidates = new LinkedHashMap<>();
        for (BankTransaction transaction : transactions) {
            List<JournalEntry> matches = entries.stream()
                    .filter(entry -> entry.getDebit().subtract(entry.getCredit())
                            .compareTo(transaction.getAmount()) == 0)
                    .toList();
            candidates.put(transaction.getId(), matches);
        }
        return candidates;
    }

    private Map<Long, Long> parseMatches(MultiValueMap<String, String> parameters) {
        Map<Long, Long> matches = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> parameter : parameters.entrySet()) {
            if (!parameter.getKey().startsWith("match_")) {
                continue;
            }
            if (parameter.getValue().size() != 1 || parameter.getValue().get(0).isBlank()) {
                throw new IllegalArgumentException("Every imported bank transaction must have one journal entry match");
            }
            try {
                Long transactionId = Long.valueOf(parameter.getKey().substring("match_".length()));
                Long entryId = Long.valueOf(parameter.getValue().get(0));
                if (matches.put(transactionId, entryId) != null) {
                    throw new IllegalArgumentException("Duplicate bank transaction match");
                }
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid reconciliation match");
            }
        }
        return matches;
    }

    private LocalDate parseDate(String date) {
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Statement date must be YYYY-MM-DD");
        }
    }
}
