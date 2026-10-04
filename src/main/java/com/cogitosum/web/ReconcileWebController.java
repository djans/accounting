package com.cogitosum.web;

import com.cogitosum.entity.ChartOfAccount;
import com.cogitosum.entity.JournalEntry;
import com.cogitosum.service.BankReconciliationService;
import com.cogitosum.service.BankReconciliationReportPdfService;
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
            model.addAttribute("openingBalance", reconciliationService.getOpeningBalance(id));
            model.addAttribute("statementDate", LocalDate.now());
            model.addAttribute("active", "reconcile");
            return "reconcile_details";
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("flashError", ex.getMessage());
            return "redirect:/reconcile";
        }
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
