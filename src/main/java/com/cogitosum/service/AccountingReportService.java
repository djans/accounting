package com.cogitosum.service;

import com.cogitosum.entity.GeneralLedger;
import com.cogitosum.entity.ChartOfAccount;
import com.cogitosum.entity.AccountType;
import com.cogitosum.repository.GeneralLedgerRepository;
import com.cogitosum.repository.ChartOfAccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AccountingReportService {

    @Autowired
    private GeneralLedgerRepository generalLedgerRepository;

    @Autowired
    private ChartOfAccountRepository chartOfAccountRepository;

    public Map<String, Object> getTrialBalance() {
        List<GeneralLedger> ledgers = generalLedgerRepository.findAll();

        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;
        Map<String, Object> trialBalance = new HashMap<>();
        Map<String, Map<String, Object>> accounts = new HashMap<>();

        for (GeneralLedger ledger : ledgers) {
            ChartOfAccount account = ledger.getAccount();
            Map<String, Object> accountData = new HashMap<>();
            accountData.put("accountNumber", account.getAccountNumber());
            accountData.put("accountName", account.getAccountName());
            accountData.put("accountType", account.getAccountType());
            accountData.put("debitBalance", ledger.getDebitBalance());
            accountData.put("creditBalance", ledger.getCreditBalance());
            accountData.put("balance", ledger.getBalance());

            accounts.put(account.getAccountNumber(), accountData);

            totalDebits = totalDebits.add(ledger.getDebitBalance());
            totalCredits = totalCredits.add(ledger.getCreditBalance());
        }

        trialBalance.put("accounts", accounts);
        trialBalance.put("totalDebits", totalDebits);
        trialBalance.put("totalCredits", totalCredits);
        trialBalance.put("balanced", totalDebits.compareTo(totalCredits) == 0);

        return trialBalance;
    }

    public Map<String, Object> getBalanceSheet() {
        Map<String, Object> balanceSheet = new HashMap<>();

        AccountSection assets = collectSection(AccountType.ASSET);
        AccountSection liabilities = collectSection(AccountType.LIABILITY);
        AccountSection equity = collectSection(AccountType.EQUITY);

        balanceSheet.put("assets", assets.rows);
        balanceSheet.put("totalAssets", assets.total);
        balanceSheet.put("liabilities", liabilities.rows);
        balanceSheet.put("totalLiabilities", liabilities.total);
        balanceSheet.put("equity", equity.rows);
        balanceSheet.put("totalEquity", equity.total);

        BigDecimal liabilitiesAndEquity = liabilities.total.add(equity.total);
        balanceSheet.put("totalLiabilitiesAndEquity", liabilitiesAndEquity);
        balanceSheet.put("balanced", assets.total.compareTo(liabilitiesAndEquity) == 0);

        return balanceSheet;
    }

    public Map<String, Object> getIncomeStatement() {
        Map<String, Object> incomeStatement = new HashMap<>();

        AccountSection revenue = collectSection(AccountType.REVENUE);
        AccountSection expenses = collectSection(AccountType.EXPENSE);

        incomeStatement.put("revenue", revenue.rows);
        incomeStatement.put("totalRevenue", revenue.total);
        incomeStatement.put("expenses", expenses.rows);
        incomeStatement.put("totalExpenses", expenses.total);
        incomeStatement.put("netIncome", revenue.total.subtract(expenses.total));

        return incomeStatement;
    }

    public Map<String, Object> getAccountDetail(Long accountId) {
        Map<String, Object> detail = new HashMap<>();

        Optional<GeneralLedger> ledger = generalLedgerRepository.findByAccountId(accountId);
        if (ledger.isPresent()) {
            GeneralLedger gl = ledger.get();
            detail.put("accountId", gl.getAccount().getId());
            detail.put("accountNumber", gl.getAccount().getAccountNumber());
            detail.put("accountName", gl.getAccount().getAccountName());
            detail.put("accountType", gl.getAccount().getAccountType());
            detail.put("debitBalance", gl.getDebitBalance());
            detail.put("creditBalance", gl.getCreditBalance());
            detail.put("balance", gl.getBalance());
            detail.put("lastUpdated", gl.getLastUpdated());
        }

        return detail;
    }

    public Map<String, BigDecimal> getAccountTypeBalances() {
        Map<String, BigDecimal> balances = new HashMap<>();

        for (AccountType type : AccountType.values()) {
            balances.put(type.name(), collectSection(type).total);
        }

        return balances;
    }

    private AccountSection collectSection(AccountType accountType) {
        AccountSection section = new AccountSection();
        List<ChartOfAccount> accounts = chartOfAccountRepository.findByAccountType(accountType);

        for (ChartOfAccount account : accounts) {
            Optional<GeneralLedger> ledger = generalLedgerRepository.findByAccountId(account.getId());
            if (ledger.isEmpty()) {
                continue;
            }
            BigDecimal balance = ledger.get().getBalance();

            Map<String, Object> row = new HashMap<>();
            row.put("accountId", account.getId());
            row.put("accountNumber", account.getAccountNumber());
            row.put("accountName", account.getAccountName());
            row.put("balance", balance);
            section.rows.add(row);
            section.total = section.total.add(balance);
        }
        return section;
    }

    private static class AccountSection {
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
    }
}
