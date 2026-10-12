package com.cogitosum.web;

import com.cogitosum.service.AccountingReportService;
import com.cogitosum.service.BillingReportService;
import com.cogitosum.service.CurrentCompanyContext;
import com.cogitosum.service.FinancialStatementsPdfService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.Map;

@Controller
@RequestMapping("/reports")
public class ReportWebController {

    @Autowired private BillingReportService billingReportService;
    @Autowired private AccountingReportService accountingReportService;
    @Autowired private FinancialStatementsPdfService financialStatementsPdfService;
    @Autowired private CurrentCompanyContext companyContext;

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
            BillingReportService.AgingReport agingReport = billingReportService.getAgingReport(reportDate);
            model.addAttribute("asOf", reportDate);
            model.addAttribute("aging", agingReport.totals());
            model.addAttribute("agingSummary", agingReport.customers());
            model.addAttribute("active", "aging-summary");
            return "reports/aging";
    }

    @GetMapping("/accounting")
    public String accounting(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Model model) {
        ReportDates dates = resolveDates(asOf, startDate, endDate);
        Map<String, Object> reports = accountingReportService.getFinancialReports(
                dates.startDate(), dates.endDate(), dates.asOf());
        model.addAttribute("trialBalance", reports.get("trialBalance"));
        model.addAttribute("trialBalanceBeforeClose", reports.get("trialBalanceBeforeClose"));
        model.addAttribute("balanceSheet", reports.get("balanceSheet"));
        model.addAttribute("incomeStatement", reports.get("incomeStatement"));
        model.addAttribute("asOf", dates.asOf());
        model.addAttribute("startDate", dates.startDate());
        model.addAttribute("endDate", dates.endDate());
        model.addAttribute("active", "financial-statements");
        return "reports/accounting";
    }

    @GetMapping("/accounting/pdf")
    public ResponseEntity<byte[]> downloadAccountingPdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        ReportDates dates = resolveDates(asOf, startDate, endDate);
        Map<String, Object> reports = accountingReportService.getFinancialReports(
                dates.startDate(), dates.endDate(), dates.asOf());
        var company = companyContext.requireCompany();
        byte[] pdf = financialStatementsPdfService.generate(reports, company.getName(), company.getCurrency(),
                dates.startDate(), dates.endDate(), dates.asOf(), LocaleContextHolder.getLocale());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=financial_statements.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/accounting/csv/trial-balance")
    public ResponseEntity<byte[]> downloadTrialBalanceCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        String csv = accountingReportService.generateTrialBalanceCsv(
                reportMap(startDate, endDate, asOf, "trialBalance"));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=trial_balance.csv")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @GetMapping("/accounting/csv/balance-sheet")
    public ResponseEntity<byte[]> downloadBalanceSheetCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        String csv = accountingReportService.generateBalanceSheetCsv(
                reportMap(startDate, endDate, asOf, "balanceSheet"));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=balance_sheet.csv")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @GetMapping("/accounting/csv/income-statement")
    public ResponseEntity<byte[]> downloadIncomeStatementCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        String csv = accountingReportService.generateIncomeStatementCsv(
                reportMap(startDate, endDate, asOf, "incomeStatement"));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=income_statement.csv")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @GetMapping("/accounting/txt")
    public ResponseEntity<byte[]> downloadFinancialStatementsTxt(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        ReportDates dates = resolveDates(asOf, startDate, endDate);
        Map<String, Object> reports = accountingReportService.getFinancialReports(
                dates.startDate(), dates.endDate(), dates.asOf());
        String txt = accountingReportService.generateFinancialStatementsTxt(
                reports, dates.startDate(), dates.endDate(), dates.asOf());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=financial_statements.txt")
                .contentType(new MediaType("text", "plain", java.nio.charset.StandardCharsets.UTF_8))
                .body(txt.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> reportMap(LocalDate startDate, LocalDate endDate, LocalDate asOf, String key) {
        ReportDates dates = resolveDates(asOf, startDate, endDate);
        return (Map<String, Object>) accountingReportService
                .getFinancialReports(dates.startDate(), dates.endDate(), dates.asOf()).get(key);
    }

    private ReportDates resolveDates(LocalDate asOf, LocalDate startDate, LocalDate endDate) {
        LocalDate reportDate = asOf != null ? asOf : (endDate != null ? endDate : LocalDate.now());
        LocalDate periodEnd = endDate != null ? endDate : reportDate;
        LocalDate periodStart = startDate != null ? startDate : periodEnd.withDayOfYear(1);
        return new ReportDates(reportDate, periodStart, periodEnd);
    }

    private record ReportDates(LocalDate asOf, LocalDate startDate, LocalDate endDate) {
    }
}
