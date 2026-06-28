package com.cogitosum.web;

import com.cogitosum.entity.AccountType;
import com.cogitosum.entity.ChartOfAccount;
import com.cogitosum.entity.GeneralLedger;
import com.cogitosum.repository.ChartOfAccountRepository;
import com.cogitosum.repository.GeneralLedgerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class ReconcileWebController {

    @Autowired private ChartOfAccountRepository accountRepository;
    @Autowired private GeneralLedgerRepository ledgerRepository;

    @GetMapping("/reconcile")
    public String reconcile(Model model) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ChartOfAccount acct : accountRepository.findByAccountType(AccountType.ASSET)) {
            Map<String, Object> row = new HashMap<>();
            row.put("accountId", acct.getId());
            row.put("accountNumber", acct.getAccountNumber());
            row.put("accountName", acct.getAccountName());
            GeneralLedger gl = ledgerRepository.findByAccountId(acct.getId()).orElse(null);
            row.put("balance", gl != null ? gl.getBalance() : null);
            rows.add(row);
        }
        model.addAttribute("rows", rows);
        model.addAttribute("active", "reconcile");
        return "reconcile";
    }
}
