package com.cogitosum.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/transfers")
public class TransferWebController {

    @GetMapping
    public String list(Model model) {
        // Placeholder implementation
        return "transfers/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        // Placeholder transfer form
        return "transfers/form";
    }
}
