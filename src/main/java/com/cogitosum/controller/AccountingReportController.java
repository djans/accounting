package com.cogitosum.controller;

import com.cogitosum.service.AccountingReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/accounting-reports")
public class AccountingReportController {

    @Autowired
    private AccountingReportService accountingReportService;

    @GetMapping("/trial-balance")
    public ResponseEntity<Map<String, Object>> getTrialBalance() {
        try {
            Map<String, Object> trialBalance = accountingReportService.getTrialBalance();
            return new ResponseEntity<>(trialBalance, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/balance-sheet")
    public ResponseEntity<Map<String, Object>> getBalanceSheet() {
        try {
            Map<String, Object> balanceSheet = accountingReportService.getBalanceSheet();
            return new ResponseEntity<>(balanceSheet, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/income-statement")
    public ResponseEntity<Map<String, Object>> getIncomeStatement() {
        try {
            Map<String, Object> incomeStatement = accountingReportService.getIncomeStatement();
            return new ResponseEntity<>(incomeStatement, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/account-detail/{accountId}")
    public ResponseEntity<Map<String, Object>> getAccountDetail(@PathVariable Long accountId) {
        try {
            Map<String, Object> detail = accountingReportService.getAccountDetail(accountId);
            return new ResponseEntity<>(detail, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/account-type-balances")
    public ResponseEntity<Map<String, ?>> getAccountTypeBalances() {
        try {
            Map<String, ?> balances = accountingReportService.getAccountTypeBalances();
            return new ResponseEntity<>(balances, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}

