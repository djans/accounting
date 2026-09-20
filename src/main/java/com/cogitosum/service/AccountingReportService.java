package com.cogitosum.service;

import com.cogitosum.entity.GeneralLedger;
import com.cogitosum.entity.JournalEntry;
import com.cogitosum.entity.ChartOfAccount;
import com.cogitosum.entity.AccountType;
import com.cogitosum.repository.GeneralLedgerRepository;
import com.cogitosum.repository.ChartOfAccountRepository;
import com.cogitosum.repository.JournalEntryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

@Service
public class AccountingReportService {

    @Autowired
    private GeneralLedgerRepository generalLedgerRepository;

    @Autowired
    private ChartOfAccountRepository chartOfAccountRepository;

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    public Map<String, Object> getTrialBalance() {
        List<GeneralLedger> ledgers = generalLedgerRepository.findAllByOrderByAccountAccountNumberAsc();

        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;
        Map<String, Object> trialBalance = new HashMap<>();
        Map<String, Map<String, Object>> accounts = new LinkedHashMap<>();
        Map<AccountType, BigDecimal> categoryTotals = new HashMap<>();
        for (AccountType type : AccountType.values()) {
            categoryTotals.put(type, BigDecimal.ZERO);
        }

        for (ChartOfAccount account : chartOfAccountRepository.findAllByOrderByAccountNumberAsc()) {
            Map<String, Object> accountData = new HashMap<>();
            accountData.put("accountNumber", account.getAccountNumber());
            accountData.put("accountId", account.getId());
            accountData.put("accountName", account.getAccountName());
            accountData.put("accountType", account.getAccountType());
            accountData.put("debitBalance", BigDecimal.ZERO);
            accountData.put("creditBalance", BigDecimal.ZERO);
            accountData.put("balance", BigDecimal.ZERO);
            accounts.put(account.getAccountNumber(), accountData);
        }

        for (GeneralLedger ledger : ledgers) {
            ChartOfAccount account = ledger.getAccount();
            if (account == null) continue;
            AccountType type = account.getAccountType();
            Map<String, Object> accountData = accounts.computeIfAbsent(account.getAccountNumber(), key -> new HashMap<>());
            accountData.put("accountNumber", account.getAccountNumber());
            accountData.put("accountId", account.getId());
            accountData.put("accountName", account.getAccountName());
            accountData.put("accountType", type);
            BigDecimal debit = ledger.getDebitBalance() == null ? BigDecimal.ZERO : ledger.getDebitBalance();
            BigDecimal credit = ledger.getCreditBalance() == null ? BigDecimal.ZERO : ledger.getCreditBalance();
            BigDecimal balance = calculateBalance(type, debit, credit);
            
            accountData.put("debitBalance", debit);
            accountData.put("creditBalance", credit);
            accountData.put("balance", balance);

            accounts.put(account.getAccountNumber(), accountData);

            totalDebits = totalDebits.add(debit);
            totalCredits = totalCredits.add(credit);
            
            if (type != null) {
                BigDecimal currentTotal = categoryTotals.getOrDefault(type, BigDecimal.ZERO);
                categoryTotals.put(type, currentTotal.add(balance));
            }
        }

        trialBalance.put("accounts", accounts);
        trialBalance.put("categoryTotals", categoryTotals);
        trialBalance.put("totalDebits", totalDebits);
        trialBalance.put("totalCredits", totalCredits);
        trialBalance.put("balanced", totalDebits.compareTo(totalCredits) == 0);

        return trialBalance;
    }

    public Map<String, Object> getBalanceSheet() {
        Map<String, Object> balanceSheet = new HashMap<>();

        AccountSection assets = collectSection(AccountType.ASSET, AccountType.CONTRA_ASSET);
        AccountSection liabilities = collectSection(AccountType.LIABILITY, AccountType.CONTRA_LIABILITY);
        AccountSection equity = collectSection(AccountType.EQUITY);

        Map<String, Object> incomeStatement = getIncomeStatement();
        BigDecimal netIncome = (BigDecimal) incomeStatement.get("netIncome");

        balanceSheet.put("assets", assets.rows);
        balanceSheet.put("totalAssets", assets.total);
        balanceSheet.put("liabilities", liabilities.rows);
        balanceSheet.put("totalLiabilities", liabilities.total);
        balanceSheet.put("equity", equity.rows);
        balanceSheet.put("totalEquity", equity.total);
        balanceSheet.put("netIncome", netIncome);

        BigDecimal totalEquityPlusNetIncome = equity.total.add(netIncome);
        balanceSheet.put("totalEquityPlusNetIncome", totalEquityPlusNetIncome);

        BigDecimal liabilitiesAndEquity = liabilities.total.add(totalEquityPlusNetIncome);
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

    public List<Map<String, Object>> getAccountTransactions(Long accountId) {
        List<JournalEntry> entries = journalEntryRepository.findByAccountId(accountId);
        return entries.stream()
                .map(e -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("date", e.getJournal().getJournalDate());
                    row.put("journalNumber", e.getJournal().getJournalNumber());
                    row.put("description", e.getDescription() != null ? e.getDescription() : e.getJournal().getNarrative());
                    row.put("debit", e.getDebit());
                    row.put("credit", e.getCredit());
                    row.put("journalId", e.getJournal().getId());
                    return row;
                })
                .sorted((a, b) -> ((java.time.LocalDate) b.get("date")).compareTo((java.time.LocalDate) a.get("date")))
                .collect(Collectors.toList());
    }

    public Map<String, BigDecimal> getAccountTypeBalances() {
        Map<String, BigDecimal> balances = new HashMap<>();

        for (AccountType type : AccountType.values()) {
            balances.put(type.name(), collectSection(type).total);
        }

        return balances;
    }

    private AccountSection collectSection(AccountType... accountTypes) {
        AccountSection section = new AccountSection();
        AccountType primaryType = accountTypes.length > 0 ? accountTypes[0] : null;

        for (AccountType type : accountTypes) {
            List<ChartOfAccount> accounts = chartOfAccountRepository.findByAccountTypeOrderByAccountNumberAsc(type);

            for (ChartOfAccount account : accounts) {
                Optional<GeneralLedger> ledger = generalLedgerRepository.findByAccountId(account.getId());
                if (ledger.isEmpty()) {
                    continue;
                }
                BigDecimal debit = ledger.get().getDebitBalance();
                BigDecimal credit = ledger.get().getCreditBalance();
                if (debit == null) debit = BigDecimal.ZERO;
                if (credit == null) credit = BigDecimal.ZERO;
                BigDecimal balance = calculateBalance(account.getAccountType(), debit, credit);

                Map<String, Object> row = new HashMap<>();
                row.put("accountId", account.getId());
                row.put("accountNumber", account.getAccountNumber());
                row.put("accountName", account.getAccountName());
                row.put("balance", balance);
                section.rows.add(row);

                if (type == primaryType) {
                    section.total = section.total.add(balance);
                } else {
                    // It's a contra account, so subtract its balance from the section total
                    section.total = section.total.subtract(balance);
                }
            }
        }
        return section;
    }

    private BigDecimal calculateBalance(AccountType type, BigDecimal debit, BigDecimal credit) {
        if (type == null) return BigDecimal.ZERO;
        BigDecimal dr = debit == null ? BigDecimal.ZERO : debit;
        BigDecimal cr = credit == null ? BigDecimal.ZERO : credit;
        return switch (type) {
            case ASSET, EXPENSE, CONTRA_LIABILITY -> dr.subtract(cr);
            case LIABILITY, EQUITY, REVENUE, CONTRA_ASSET -> cr.subtract(dr);
        };
    }

    public String generateTrialBalanceCsv() {
        Map<String, Object> trialBalance = getTrialBalance();
        Map<String, Map<String, Object>> accounts = (Map<String, Map<String, Object>>) trialBalance.get("accounts");
        
        StringBuilder csv = new StringBuilder();
        csv.append("Account Number,Account Name,Account Type,Debit,Credit,Balance\n");
        
        for (Map<String, Object> account : accounts.values()) {
            csv.append(String.format("\"%s\",\"%s\",\"%s\",%s,%s,%s\n",
                account.get("accountNumber"),
                account.get("accountName"),
                account.get("accountType"),
                account.get("debitBalance"),
                account.get("creditBalance"),
                account.get("balance")));
        }
        
        csv.append(String.format(",Totals,,%s,%s,\n", 
            trialBalance.get("totalDebits"), 
            trialBalance.get("totalCredits")));
        
        Map<AccountType, BigDecimal> categoryTotals = (Map<AccountType, BigDecimal>) trialBalance.get("categoryTotals");
        if (categoryTotals != null) {
            csv.append(String.format(",Total Assets,,,%s\n", categoryTotals.get(AccountType.ASSET)));
            csv.append(String.format(",Total Liabilities,,,%s\n", categoryTotals.get(AccountType.LIABILITY)));
            csv.append(String.format(",Total Equity,,,%s\n", categoryTotals.get(AccountType.EQUITY)));
        }
        
        return csv.toString();
    }

    public String generateBalanceSheetCsv() {
        Map<String, Object> bs = getBalanceSheet();
        StringBuilder csv = new StringBuilder();
        csv.append("Section,Account Number,Account Name,Amount\n");
        
        appendSectionToCsv(csv, "ASSETS", (List<Map<String, Object>>) bs.get("assets"), (BigDecimal) bs.get("totalAssets"));
        appendSectionToCsv(csv, "LIABILITIES", (List<Map<String, Object>>) bs.get("liabilities"), (BigDecimal) bs.get("totalLiabilities"));
        
        List<Map<String, Object>> equity = (List<Map<String, Object>>) bs.get("equity");
        for (Map<String, Object> row : equity) {
            csv.append(String.format("EQUITY,\"%s\",\"%s\",%s\n", row.get("accountNumber"), row.get("accountName"), row.get("balance")));
        }
        csv.append(String.format("EQUITY,,\"Net Income (Current Year)\",%s\n", bs.get("netIncome")));
        csv.append(String.format("EQUITY,,\"Total Equity\",%s\n", bs.get("totalEquityPlusNetIncome")));
        
        csv.append(String.format(",,\"Total Liabilities + Equity\",%s\n", bs.get("totalLiabilitiesAndEquity")));
        
        return csv.toString();
    }

    private void appendSectionToCsv(StringBuilder csv, String sectionName, List<Map<String, Object>> rows, BigDecimal total) {
        for (Map<String, Object> row : rows) {
            csv.append(String.format("%s,\"%s\",\"%s\",%s\n", sectionName, row.get("accountNumber"), row.get("accountName"), row.get("balance")));
        }
        csv.append(String.format("%s,,\"Total %s\",%s\n", sectionName, sectionName, total));
    }

    public String generateIncomeStatementCsv() {
        Map<String, Object> is = getIncomeStatement();
        StringBuilder csv = new StringBuilder();
        csv.append("Section,Account Number,Account Name,Amount\n");
        
        appendSectionToCsv(csv, "REVENUE", (List<Map<String, Object>>) is.get("revenue"), (BigDecimal) is.get("totalRevenue"));
        appendSectionToCsv(csv, "EXPENSES", (List<Map<String, Object>>) is.get("expenses"), (BigDecimal) is.get("totalExpenses"));
        
        csv.append(String.format(",,\"Net Income\",%s\n", is.get("netIncome")));
        
        return csv.toString();
    }

    public String generateTrialBalanceTxt() {
        Map<String, Object> trialBalance = getTrialBalance();
        Map<String, Map<String, Object>> accounts = (Map<String, Map<String, Object>>) trialBalance.get("accounts");
        
        StringBuilder txt = new StringBuilder();
        txt.append("TRIAL BALANCE\n");
        txt.append("================================================================================\n");
        txt.append(String.format("%-10s %-30s %-15s %10s %10s %10s\n", "Acc #", "Account Name", "Type", "Debit", "Credit", "Balance"));
        txt.append("--------------------------------------------------------------------------------\n");
        
        for (Map<String, Object> account : accounts.values()) {
            txt.append(String.format("%-10s %-30s %-15s %10.2f %10.2f %10.2f\n",
                account.get("accountNumber"),
                truncate((String)account.get("accountName"), 30),
                account.get("accountType"),
                account.get("debitBalance"),
                account.get("creditBalance"),
                account.get("balance")));
        }
        txt.append("--------------------------------------------------------------------------------\n");
        txt.append(String.format("%-57s %10.2f %10.2f\n", "TOTALS", 
            trialBalance.get("totalDebits"), 
            trialBalance.get("totalCredits")));
        
        Map<AccountType, BigDecimal> categoryTotals = (Map<AccountType, BigDecimal>) trialBalance.get("categoryTotals");
        if (categoryTotals != null) {
            txt.append("--------------------------------------------------------------------------------\n");
            txt.append(String.format("%-69s %10.2f\n", "TOTAL ASSETS", categoryTotals.get(AccountType.ASSET)));
            txt.append(String.format("%-69s %10.2f\n", "TOTAL LIABILITIES", categoryTotals.get(AccountType.LIABILITY)));
            txt.append(String.format("%-69s %10.2f\n", "TOTAL EQUITY", categoryTotals.get(AccountType.EQUITY)));
        }
        
        txt.append("================================================================================\n");
        
        return txt.toString();
    }

    public String generateFinancialStatementsTxt() {
        StringBuilder txt = new StringBuilder();
        
        // Trial Balance
        txt.append(generateTrialBalanceTxt());
        txt.append("\n\n");
        
        // Balance Sheet
        Map<String, Object> bs = getBalanceSheet();
        txt.append("BALANCE SHEET\n");
        txt.append("================================================================================\n");
        
        txt.append("ASSETS\n");
        txt.append("------\n");
        for (Map<String, Object> row : (List<Map<String, Object>>) bs.get("assets")) {
            txt.append(String.format("%-10s %-50s %15.2f\n", row.get("accountNumber"), row.get("accountName"), row.get("balance")));
        }
        txt.append(String.format("%-61s %15.2f\n", "TOTAL ASSETS", bs.get("totalAssets")));
        txt.append("\n");
        
        txt.append("LIABILITIES\n");
        txt.append("-----------\n");
        for (Map<String, Object> row : (List<Map<String, Object>>) bs.get("liabilities")) {
            txt.append(String.format("%-10s %-50s %15.2f\n", row.get("accountNumber"), row.get("accountName"), row.get("balance")));
        }
        txt.append(String.format("%-61s %15.2f\n", "TOTAL LIABILITIES", bs.get("totalLiabilities")));
        txt.append("\n");
        
        txt.append("EQUITY\n");
        txt.append("------\n");
        for (Map<String, Object> row : (List<Map<String, Object>>) bs.get("equity")) {
            txt.append(String.format("%-10s %-50s %15.2f\n", row.get("accountNumber"), row.get("accountName"), row.get("balance")));
        }
        txt.append(String.format("%-61s %15.2f\n", "Net Income (Current Year)", bs.get("netIncome")));
        txt.append(String.format("%-61s %15.2f\n", "TOTAL EQUITY", bs.get("totalEquityPlusNetIncome")));
        txt.append("\n");
        
        txt.append(String.format("%-61s %15.2f\n", "TOTAL LIABILITIES AND EQUITY", bs.get("totalLiabilitiesAndEquity")));
        txt.append("================================================================================\n");
        txt.append("\n\n");
        
        // Income Statement
        Map<String, Object> is = getIncomeStatement();
        txt.append("INCOME STATEMENT\n");
        txt.append("================================================================================\n");
        txt.append("REVENUE\n");
        txt.append("-------\n");
        for (Map<String, Object> row : (List<Map<String, Object>>) is.get("revenue")) {
            txt.append(String.format("%-10s %-50s %15.2f\n", row.get("accountNumber"), row.get("accountName"), row.get("balance")));
        }
        txt.append(String.format("%-61s %15.2f\n", "TOTAL REVENUE", is.get("totalRevenue")));
        txt.append("\n");
        
        txt.append("EXPENSES\n");
        txt.append("--------\n");
        for (Map<String, Object> row : (List<Map<String, Object>>) is.get("expenses")) {
            txt.append(String.format("%-10s %-50s %15.2f\n", row.get("accountNumber"), row.get("accountName"), row.get("balance")));
        }
        txt.append(String.format("%-61s %15.2f\n", "TOTAL EXPENSES", is.get("totalExpenses")));
        txt.append("\n");
        
        txt.append(String.format("%-61s %15.2f\n", "NET INCOME", is.get("netIncome")));
        txt.append("================================================================================\n");
        
        return txt.toString();
    }

    private String truncate(String text, int length) {
        if (text == null) return "";
        return text.length() > length ? text.substring(0, length) : text;
    }

    private static class AccountSection {
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
    }
}
