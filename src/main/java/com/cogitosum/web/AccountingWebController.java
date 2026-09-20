package com.cogitosum.web;

import com.cogitosum.service.AccountingReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;

@Controller
@RequestMapping("/accounting")
public class AccountingWebController {

    private static final Logger log = LoggerFactory.getLogger(AccountingWebController.class);

    @Autowired
    private AccountingReportService accountingReportService;

    @GetMapping("/trial-balance")
    public String trialBalance(Model model) {
        try {
            Map<String, Object> tb = accountingReportService.getTrialBalance();
            Map<?, ?> accounts = tb.get("accounts") instanceof Map<?, ?> map
                    ? map : new LinkedHashMap<>();
            model.addAttribute("accounts", accounts);
            model.addAttribute("accountRows", new ArrayList<>(accounts.values()));
            model.addAttribute("totalDebits", tb.getOrDefault("totalDebits", BigDecimal.ZERO));
            model.addAttribute("totalCredits", tb.getOrDefault("totalCredits", BigDecimal.ZERO));
            model.addAttribute("balanced", tb.getOrDefault("balanced", true));
        } catch (RuntimeException e) {
            log.error("Could not render trial balance page", e);
            model.addAttribute("accounts", new LinkedHashMap<>());
            model.addAttribute("accountRows", new ArrayList<>());
            model.addAttribute("totalDebits", BigDecimal.ZERO);
            model.addAttribute("totalCredits", BigDecimal.ZERO);
            model.addAttribute("balanced", false);
            model.addAttribute("trialBalanceError", "Unable to load the trial balance. Please check the application log.");
        }
        model.addAttribute("active", "trial-balance");
        return "reports/trial-balance";
    }
}
