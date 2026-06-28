package com.cogitosum.web;

import com.cogitosum.service.AccountingReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

@Controller
@RequestMapping("/accounting")
public class AccountingWebController {

    @Autowired
    private AccountingReportService accountingReportService;

    @GetMapping("/trial-balance")
    public String trialBalance(Model model) {
        Map<String, Object> tb = accountingReportService.getTrialBalance();
        model.addAttribute("accounts", tb.get("accounts"));
        model.addAttribute("totalDebits", tb.get("totalDebits"));
        model.addAttribute("totalCredits", tb.get("totalCredits"));
        model.addAttribute("balanced", tb.get("balanced"));
        model.addAttribute("active", "accounting-reports");
        return "reports/trial-balance";
    }
}
