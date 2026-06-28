package com.cogitosum.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ReconcileWebController {

    @GetMapping("/reconcile")
    public String reconcile(Model model) {
        model.addAttribute("active", "reconcile");
        return "reconcile";
    }
}
