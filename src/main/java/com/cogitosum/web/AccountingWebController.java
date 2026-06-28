package com.cogitosum.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/accounting")
public class AccountingWebController {

    @GetMapping("/trial-balance")
    public String trialBalance(Model model) {
        model.addAttribute("active", "accounting-reports");
        return "reports/trial-balance";
    }
}
