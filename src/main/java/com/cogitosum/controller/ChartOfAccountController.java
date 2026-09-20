package com.cogitosum.controller;

import com.cogitosum.entity.ChartOfAccount;
import com.cogitosum.entity.AccountType;
import com.cogitosum.service.ChartOfAccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/chart-of-accounts")
public class ChartOfAccountController {

    @Autowired
    private ChartOfAccountService chartOfAccountService;

    @PostMapping
    public ResponseEntity<ChartOfAccount> createAccount(@RequestBody ChartOfAccount account) {
        try {
            ChartOfAccount createdAccount = chartOfAccountService.createAccount(account);
            return new ResponseEntity<>(createdAccount, HttpStatus.CREATED);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @GetMapping
    public ResponseEntity<List<ChartOfAccount>> getAllAccounts() {
        try {
            List<ChartOfAccount> accounts = chartOfAccountService.getAllAccounts();
            return new ResponseEntity<>(accounts, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/active")
    public ResponseEntity<List<ChartOfAccount>> getActiveAccounts() {
        try {
            List<ChartOfAccount> accounts = chartOfAccountService.getActiveAccounts();
            return new ResponseEntity<>(accounts, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ChartOfAccount> getAccountById(@PathVariable Long id) {
        try {
            Optional<ChartOfAccount> account = chartOfAccountService.getAccountById(id);
            return account.map(acc -> new ResponseEntity<>(acc, HttpStatus.OK))
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/number/{accountNumber}")
    public ResponseEntity<ChartOfAccount> getAccountByNumber(@PathVariable String accountNumber) {
        try {
            Optional<ChartOfAccount> account = chartOfAccountService.getAccountByNumber(accountNumber);
            return account.map(acc -> new ResponseEntity<>(acc, HttpStatus.OK))
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/type/{type}")
    public ResponseEntity<List<ChartOfAccount>> getAccountsByType(@PathVariable AccountType type) {
        try {
            List<ChartOfAccount> accounts = chartOfAccountService.getAccountsByType(type);
            return new ResponseEntity<>(accounts, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ChartOfAccount> updateAccount(@PathVariable Long id, @RequestBody ChartOfAccount account) {
        try {
            ChartOfAccount updatedAccount = chartOfAccountService.updateAccount(id, account);
            if (updatedAccount != null) {
                return new ResponseEntity<>(updatedAccount, HttpStatus.OK);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAccount(@PathVariable Long id) {
        try {
            chartOfAccountService.deleteAccount(id);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}

