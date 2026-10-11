package com.cogitosum.controller;

import com.cogitosum.config.LoggingCategory;
import com.cogitosum.service.AccountingReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/accounting-reports")
public class AccountingReportController {

    private static final Logger log = LoggerFactory.getLogger(AccountingReportController.class);

    @Autowired
    private AccountingReportService accountingReportService;

    @GetMapping("/trial-balance")
    public ResponseEntity<Map<String, Object>> getTrialBalance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
            @RequestParam(defaultValue = "false") boolean beforeClose) {
        try {
            Map<String, Object> reports = getFinancialReports(asOf, null, null);
            Map<String, Object> trialBalance = castMap(reports.get(
                    beforeClose ? "trialBalanceBeforeClose" : "trialBalance"));
            return new ResponseEntity<>(trialBalance, HttpStatus.OK);
        } catch (Exception e) {
            logError("Could not generate trial balance", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/balance-sheet")
    public ResponseEntity<Map<String, Object>> getBalanceSheet(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        try {
            Map<String, Object> balanceSheet = castMap(getFinancialReports(asOf, null, null).get("balanceSheet"));
            return new ResponseEntity<>(balanceSheet, HttpStatus.OK);
        } catch (Exception e) {
            logError("Could not generate balance sheet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/income-statement")
    public ResponseEntity<Map<String, Object>> getIncomeStatement(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        try {
            Map<String, Object> incomeStatement =
                    castMap(getFinancialReports(asOf, startDate, endDate).get("incomeStatement"));
            return new ResponseEntity<>(incomeStatement, HttpStatus.OK);
        } catch (Exception e) {
            logError("Could not generate income statement", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/account-detail/{accountId}")
    public ResponseEntity<Map<String, Object>> getAccountDetail(@PathVariable Long accountId) {
        try {
            Map<String, Object> detail = accountingReportService.getAccountDetail(accountId);
            return new ResponseEntity<>(detail, HttpStatus.OK);
        } catch (Exception e) {
            try (LoggingCategory.Scope ignored = LoggingCategory.open(LoggingCategory.ERROR)) {
                log.error("Could not generate account detail for account {}", accountId, e);
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/account-transactions/{accountId}")
    public ResponseEntity<List<Map<String, Object>>> getAccountTransactions(@PathVariable Long accountId) {
        try {
            List<Map<String, Object>> transactions = accountingReportService.getAccountTransactions(accountId);
            return new ResponseEntity<>(transactions, HttpStatus.OK);
        } catch (Exception e) {
            try (LoggingCategory.Scope ignored = LoggingCategory.open(LoggingCategory.ERROR)) {
                log.error("Could not get transactions for account {}", accountId, e);
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/account-type-balances")
    public ResponseEntity<Map<String, ?>> getAccountTypeBalances() {
        try {
            Map<String, ?> balances = accountingReportService.getAccountTypeBalances();
            return new ResponseEntity<>(balances, HttpStatus.OK);
        } catch (Exception e) {
            logError("Could not generate account type balances", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private void logError(String message, Exception exception) {
        try (LoggingCategory.Scope ignored = LoggingCategory.open(LoggingCategory.ERROR)) {
            log.error(message, exception);
        }
    }

    private Map<String, Object> getFinancialReports(LocalDate asOf, LocalDate startDate, LocalDate endDate) {
        LocalDate reportDate = asOf != null ? asOf : (endDate != null ? endDate : LocalDate.now());
        LocalDate periodEnd = endDate != null ? endDate : reportDate;
        LocalDate periodStart = startDate != null ? startDate : periodEnd.withDayOfYear(1);
        return accountingReportService.getFinancialReports(periodStart, periodEnd, reportDate);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }
}
