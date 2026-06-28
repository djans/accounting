package com.cogitosum.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/cheques")
public class ChequeWebController {

    @GetMapping
    public String list(Model model) {
        // Placeholder implementation
        return "cheques/list";
    }
}
