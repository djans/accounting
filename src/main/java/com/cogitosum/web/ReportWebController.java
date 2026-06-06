package com.cogitosum.web;

import com.cogitosum.service.AccountingReportService;
import com.cogitosum.service.BillingReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
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
        model.addAttribute("aging", billingReportService.getAgingAnalysis());
        model.addAttribute("statusSummary", billingReportService.getInvoiceStatusSummary());
        model.addAttribute("tax", billingReportService.getTaxSummary(start, end));
        return "reports/billing";
    }

    @GetMapping("/accounting")
    public String accounting(Model model) {
        model.addAttribute("trialBalance", accountingReportService.getTrialBalance());
        model.addAttribute("balanceSheet", accountingReportService.getBalanceSheet());
        model.addAttribute("incomeStatement", accountingReportService.getIncomeStatement());
        return "reports/accounting";
    }
}
