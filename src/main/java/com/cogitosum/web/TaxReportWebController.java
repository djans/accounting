package com.cogitosum.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/reports/tax")
public class TaxReportWebController {
n    @GetMapping
    public String index(Model model) {
        // Placeholder tax reports landing page
        return "reports/tax";
    }
}
