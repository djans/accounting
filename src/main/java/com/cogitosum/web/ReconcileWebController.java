package com.cogitosum.web;

import com.cogitosum.entity.ChartOfAccount;
import com.cogitosum.entity.JournalEntry;
import com.cogitosum.service.BankReconciliationService;
import com.cogitosum.service.BankReconciliationReportPdfService;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

@Controller
public class ReconcileWebController {

    private final BankReconciliationService reconciliationService;
    private final BankReconciliationReportPdfService reportPdfService;

    public ReconcileWebController(BankReconciliationService reconciliationService,
                                  BankReconciliationReportPdfService reportPdfService) {
        this.reconciliationService = reconciliationService;
        this.reportPdfService = reportPdfService;
    }

    @GetMapping("/reconcile")
    public String reconcile(Model model) {
        model.addAttribute("rows", reconciliationService.getBankAccounts());
        model.addAttribute("active", "reconcile");
        return "reconcile";
    }

    @GetMapping("/reconcile/reports")
    public String reports(Model model) {
        model.addAttribute("sessions", reportPdfService.getCompanySessions());
        model.addAttribute("active", "reconciliation-reports");
        return "reconcile_reports";
    }

    @GetMapping("/reconcile/sessions/{id}/report.pdf")
    public ResponseEntity<byte[]> downloadReport(@PathVariable Long id) {
        return reportPdfService.getPdfReport(id)
                .map(report -> ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_PDF)
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                ContentDisposition.attachment().filename(report.filename()).build().toString())
                        .body(report.content()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/reconcile/account/{id}")
    public String reconcileAccount(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            ChartOfAccount account = reconciliationService.getBankAccount(id);
            List<JournalEntry> entries = reconciliationService.getEligibleJournalEntries(id);
            model.addAttribute("account", account);
            model.addAttribute("journalEntries", entries);
            model.addAttribute("bankTransactions", reconciliationService.getImportedTransactions(id));
            model.addAttribute("categorizationAccounts", reconciliationService.getCategorizationAccounts());
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
    public String importStatement(@PathVariable Long id, @RequestParam("file") MultipartFile file,
                                  RedirectAttributes redirectAttributes) {
        try {
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("Choose a bank statement file");
            }
            String filename = file.getOriginalFilename();
            if (filename == null) {
                throw new IllegalArgumentException("The bank statement filename is missing");
            }
            String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(java.util.Locale.ROOT);
            var result = switch (extension) {
                case "csv" -> reconciliationService.importCsv(id, file.getBytes());
                case "ofx", "qfx" -> reconciliationService.importOfx(id, file.getBytes());
                default -> throw new IllegalArgumentException("Choose a CSV, OFX, or QFX file");
            };
            redirectAttributes.addFlashAttribute("flashSuccess",
                    "Imported " + result.importedCount() + " bank transactions");
        } catch (Exception exception) {
            redirectAttributes.addFlashAttribute("flashError", "Bank import failed: " + exception.getMessage());
        }
        return "redirect:/reconcile/account/" + id;
    }

    @PostMapping("/reconcile/transactions/{id}/review-category")
    public String reviewSuggestedCategory(@PathVariable Long id, @RequestParam Long accountId,
                                          RedirectAttributes redirectAttributes) {
        try {
            reconciliationService.reviewSuggestedCategory(id);
            redirectAttributes.addFlashAttribute("flashSuccess", "Suggested category reviewed");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("flashError", exception.getMessage());
        }
        return "redirect:/reconcile/account/" + accountId;
    }

    @GetMapping("/reconcile/rules")
    public String categorizationRules(Model model) {
        model.addAttribute("rules", reconciliationService.getCategorizationRules());
        model.addAttribute("accounts", reconciliationService.getCategorizationAccounts());
        model.addAttribute("active", "reconcile");
        return "reconcile_rules";
    }

    @PostMapping("/reconcile/rules")
    public String createCategorizationRule(@RequestParam String name, @RequestParam String matchText,
                                          @RequestParam int priority, @RequestParam Long targetAccountId,
                                          RedirectAttributes redirectAttributes) {
        try {
            reconciliationService.createCategorizationRule(name, matchText, priority, targetAccountId);
            redirectAttributes.addFlashAttribute("flashSuccess", "Categorization rule saved");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("flashError", exception.getMessage());
        }
        return "redirect:/reconcile/rules";
    }

    @PostMapping("/reconcile/rules/{id}/deactivate")
    public String deactivateCategorizationRule(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            reconciliationService.deactivateCategorizationRule(id);
            redirectAttributes.addFlashAttribute("flashSuccess", "Categorization rule disabled");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("flashError", exception.getMessage());
        }
        return "redirect:/reconcile/rules";
    }

    @PostMapping("/reconcile/finish")
    public String finishReconciliation(@RequestParam Long accountId,
                                       @RequestParam String statementDate,
                                       @RequestParam String statementEndingBalance,
                                       @RequestParam(value = "entryIds", required = false) List<Long> entryIds,
                                       RedirectAttributes redirectAttributes) {
        try {
            var session = reconciliationService.reconcile(
                    accountId,
                    parseDate(statementDate),
                    new BigDecimal(statementEndingBalance),
                    entryIds);
            redirectAttributes.addFlashAttribute("flashSuccess", "Bank reconciliation completed");
            return "redirect:/reconcile/sessions/" + session.getId() + "/report.pdf";
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("flashError", "Reconciliation failed: " + ex.getMessage());
            return "redirect:/reconcile/account/" + accountId;
        }
    }

    private LocalDate parseDate(String date) {
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Statement date must be YYYY-MM-DD");
        }
    }
}
