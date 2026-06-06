package com.cogitosum.service;

import com.cogitosum.entity.ChartOfAccount;
import com.cogitosum.entity.AccountType;
import com.cogitosum.repository.ChartOfAccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ChartOfAccountService {

    @Autowired
    private ChartOfAccountRepository chartOfAccountRepository;

    @Autowired
    private GeneralLedgerService generalLedgerService;

    @Transactional
    public ChartOfAccount createAccount(ChartOfAccount account) {
        ChartOfAccount saved = chartOfAccountRepository.save(account);
        // Every new account gets a matching GL row so journal posting can find it.
        generalLedgerService.createLedgerAccount(saved);
        return saved;
    }

    @Transactional
    public ChartOfAccount updateAccount(Long id, ChartOfAccount account) {
        Optional<ChartOfAccount> existingAccount = chartOfAccountRepository.findById(id);
        if (existingAccount.isPresent()) {
            ChartOfAccount acc = existingAccount.get();
            acc.setAccountName(account.getAccountName());
            acc.setAccountType(account.getAccountType());
            acc.setDescription(account.getDescription());
            acc.setActive(account.getActive());
            return chartOfAccountRepository.save(acc);
        }
        return null;
    }

    public Optional<ChartOfAccount> getAccountById(Long id) {
        return chartOfAccountRepository.findById(id);
    }

    public Optional<ChartOfAccount> getAccountByNumber(String accountNumber) {
        return chartOfAccountRepository.findByAccountNumber(accountNumber);
    }

    public List<ChartOfAccount> getAccountsByType(AccountType accountType) {
        return chartOfAccountRepository.findByAccountType(accountType);
    }

    public List<ChartOfAccount> getActiveAccounts() {
        return chartOfAccountRepository.findByIsActive(true);
    }

    public List<ChartOfAccount> getAllAccounts() {
        return chartOfAccountRepository.findAll();
    }

    @Transactional
    public void deleteAccount(Long id) {
        chartOfAccountRepository.deleteById(id);
    }
}
