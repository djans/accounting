package com.cogitosum.web;

import com.cogitosum.service.TaxAgencyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/tax/agencies")
public class TaxAgencyPagesController {

    @Autowired private TaxAgencyService agencyService;

    @GetMapping("/report")
    public String report(Model model) {
        // Map legacy /tax/agencies/report to agencies list
        model.addAttribute("agencies", agencyService.getAll());
        return "tax/agencies";
    }

    @GetMapping("/detail")
    public String detail(Model model) {
        // Redirectless placeholder: show agencies list with instruction
        model.addAttribute("agencies", agencyService.getAll());
        model.addAttribute("info", "Please select an agency to view details or append /{id}/report to the URL.");
        return "tax/agencies";
    }
}
