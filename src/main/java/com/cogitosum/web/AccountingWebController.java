package com.cogitosum.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/accounting")
public class AccountingWebController {

    @GetMapping("/trial-balance")
    public String trialBalance() {
        return "redirect:/reports/accounting";
    }
}
