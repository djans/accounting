package com.cogitosum.web;

import com.cogitosum.service.GeneralLedgerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/ledger")
public class LedgerWebController {

    @Autowired
    private GeneralLedgerService generalLedgerService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("ledgers", generalLedgerService.getAllLedgerAccounts());
        return "ledger/list";
    }
}
