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

    @Autowired
    private CurrentCompanyContext companyContext;

    @Transactional
    public ChartOfAccount createAccount(ChartOfAccount account) {
        Long companyId = companyContext.requireCompanyId();
        companyContext.assignCurrentCompany(account);
        account.setParentAccount(resolveParentAccount(account.getParentAccount(), companyId));
        ChartOfAccount saved = chartOfAccountRepository.save(account);
        // Every new account gets a matching GL row so journal posting can find it.
        generalLedgerService.createLedgerAccount(saved);
        return saved;
    }

    @Transactional
    public ChartOfAccount updateAccount(Long id, ChartOfAccount account) {
        Long companyId = companyContext.requireCompanyId();
        Optional<ChartOfAccount> existingAccount = chartOfAccountRepository.findByIdAndCompanyId(id, companyId);
        if (existingAccount.isPresent()) {
            ChartOfAccount acc = existingAccount.get();
            if (account.getAccountNumber() == null || account.getAccountNumber().isBlank()) {
                throw new IllegalArgumentException("Account number is required");
            }
            chartOfAccountRepository.findByCompanyIdAndAccountNumber(companyId, account.getAccountNumber())
                .filter(found -> !found.getId().equals(id))
                .ifPresent(found -> {
                    throw new IllegalArgumentException("Account number already exists: " + account.getAccountNumber());
                });
            acc.setAccountNumber(account.getAccountNumber().trim());
            acc.setAccountName(account.getAccountName());
            acc.setAccountType(account.getAccountType());
            acc.setCategory(account.getCategory());
            acc.setDescription(account.getDescription());
            acc.setActive(account.getActive());
            acc.setParentAccount(resolveParentAccount(account.getParentAccount(), companyId));
            acc.setCurrency(account.getCurrency());
            acc.setOpeningBalance(account.getOpeningBalance());
            acc.setOpeningBalanceDate(account.getOpeningBalanceDate());
            return chartOfAccountRepository.save(acc);
        }
        return null;
    }

    public Optional<ChartOfAccount> getAccountById(Long id) {
        return chartOfAccountRepository.findByIdAndCompanyId(id, companyContext.requireCompanyId());
    }

    public Optional<ChartOfAccount> getAccountByNumber(String accountNumber) {
        return chartOfAccountRepository.findByCompanyIdAndAccountNumber(companyContext.requireCompanyId(), accountNumber);
    }

    public List<ChartOfAccount> getAccountsByType(AccountType accountType) {
        return chartOfAccountRepository.findByCompanyIdAndAccountTypeOrderByAccountNumberAsc(
                companyContext.requireCompanyId(), accountType);
    }

    public List<ChartOfAccount> getActiveAccounts() {
        return chartOfAccountRepository.findByCompanyIdAndIsActiveOrderByAccountNumberAsc(
                companyContext.requireCompanyId(), true);
    }

    public List<ChartOfAccount> getAllAccounts() {
        return chartOfAccountRepository.findAllByCompanyIdOrderByAccountNumberAsc(companyContext.requireCompanyId());
    }

    @Transactional
    public void deleteAccount(Long id) {
        chartOfAccountRepository.findByIdAndCompanyId(id, companyContext.requireCompanyId())
                .ifPresent(chartOfAccountRepository::delete);
    }

    private ChartOfAccount resolveParentAccount(ChartOfAccount parent, Long companyId) {
        if (parent == null) {
            return null;
        }
        if (parent.getId() == null) {
            throw new IllegalArgumentException("Parent account is invalid");
        }
        return chartOfAccountRepository.findByIdAndCompanyId(parent.getId(), companyId)
                .orElseThrow(() -> new IllegalArgumentException("Parent account not found"));
    }
}
