package com.cogitosum.controller;

import com.cogitosum.service.AccountingReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/accounting-reports")
public class AccountingReportController {

    private static final Logger log = LoggerFactory.getLogger(AccountingReportController.class);

    @Autowired
    private AccountingReportService accountingReportService;

    @GetMapping("/trial-balance")
    public ResponseEntity<Map<String, Object>> getTrialBalance() {
        try {
            Map<String, Object> trialBalance = accountingReportService.getTrialBalance();
            return new ResponseEntity<>(trialBalance, HttpStatus.OK);
        } catch (Exception e) {
            log.error("Could not generate trial balance", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/balance-sheet")
    public ResponseEntity<Map<String, Object>> getBalanceSheet() {
        try {
            Map<String, Object> balanceSheet = accountingReportService.getBalanceSheet();
            return new ResponseEntity<>(balanceSheet, HttpStatus.OK);
        } catch (Exception e) {
            log.error("Could not generate balance sheet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/income-statement")
    public ResponseEntity<Map<String, Object>> getIncomeStatement() {
        try {
            Map<String, Object> incomeStatement = accountingReportService.getIncomeStatement();
            return new ResponseEntity<>(incomeStatement, HttpStatus.OK);
        } catch (Exception e) {
            log.error("Could not generate income statement", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/account-detail/{accountId}")
    public ResponseEntity<Map<String, Object>> getAccountDetail(@PathVariable Long accountId) {
        try {
            Map<String, Object> detail = accountingReportService.getAccountDetail(accountId);
            return new ResponseEntity<>(detail, HttpStatus.OK);
        } catch (Exception e) {
            log.error("Could not generate account detail for account {}", accountId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/account-transactions/{accountId}")
    public ResponseEntity<List<Map<String, Object>>> getAccountTransactions(@PathVariable Long accountId) {
        try {
            List<Map<String, Object>> transactions = accountingReportService.getAccountTransactions(accountId);
            return new ResponseEntity<>(transactions, HttpStatus.OK);
        } catch (Exception e) {
            log.error("Could not get transactions for account {}", accountId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/account-type-balances")
    public ResponseEntity<Map<String, ?>> getAccountTypeBalances() {
        try {
            Map<String, ?> balances = accountingReportService.getAccountTypeBalances();
            return new ResponseEntity<>(balances, HttpStatus.OK);
        } catch (Exception e) {
            log.error("Could not generate account type balances", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
