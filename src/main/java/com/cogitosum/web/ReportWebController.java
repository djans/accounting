package com.cogitosum.web;

import com.cogitosum.service.AccountingReportService;
import com.cogitosum.service.BillingReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

@Controller
@RequestMapping("/reports")
public class ReportWebController {

    @Autowired private BillingReportService billingReportService;
    @Autowired private AccountingReportService accountingReportService;

    @GetMapping("/billing")
    public String billing(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Model model) {

        LocalDate end = endDate != null ? endDate : LocalDate.now();
        LocalDate start = startDate != null ? startDate : end.withDayOfYear(1);

        model.addAttribute("startDate", start);
        model.addAttribute("endDate", end);
        model.addAttribute("revenue", billingReportService.getRevenueReport(start, end));
        model.addAttribute("statusSummary", billingReportService.getInvoiceStatusSummary());
        model.addAttribute("tax", billingReportService.getTaxSummary(start, end));
        return "reports/billing";
    }

    @GetMapping("/aging")
    public String aging(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
            Model model) {
        LocalDate reportDate = asOf != null ? asOf : LocalDate.now();
        model.addAttribute("asOf", reportDate);
        model.addAttribute("aging", billingReportService.getAgingAnalysis(reportDate));
        model.addAttribute("agingSummary", billingReportService.getAgingSummary(reportDate));
        model.addAttribute("active", "aging-summary");
        return "reports/aging";
    }

    @GetMapping("/accounting")
    public String accounting(Model model) {
        model.addAttribute("trialBalance", accountingReportService.getTrialBalance());
        model.addAttribute("balanceSheet", accountingReportService.getBalanceSheet());
        model.addAttribute("incomeStatement", accountingReportService.getIncomeStatement());
        model.addAttribute("active", "financial-statements");
        return "reports/accounting";
    }

    @GetMapping("/accounting/csv/trial-balance")
    public ResponseEntity<byte[]> downloadTrialBalanceCsv() {
        String csv = accountingReportService.generateTrialBalanceCsv();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=trial_balance.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv.getBytes());
    }

    @GetMapping("/accounting/csv/balance-sheet")
    public ResponseEntity<byte[]> downloadBalanceSheetCsv() {
        String csv = accountingReportService.generateBalanceSheetCsv();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=balance_sheet.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv.getBytes());
    }

    @GetMapping("/accounting/csv/income-statement")
    public ResponseEntity<byte[]> downloadIncomeStatementCsv() {
        String csv = accountingReportService.generateIncomeStatementCsv();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=income_statement.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv.getBytes());
    }

    @GetMapping("/accounting/txt")
    public ResponseEntity<byte[]> downloadFinancialStatementsTxt() {
        String txt = accountingReportService.generateFinancialStatementsTxt();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=financial_statements.txt")
                .contentType(MediaType.TEXT_PLAIN)
                .body(txt.getBytes());
    }
}
