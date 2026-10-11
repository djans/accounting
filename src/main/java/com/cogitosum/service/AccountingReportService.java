package com.cogitosum.service;

import com.cogitosum.entity.GeneralLedger;
import com.cogitosum.entity.JournalEntry;
import com.cogitosum.entity.ChartOfAccount;
import com.cogitosum.entity.AccountType;
import com.cogitosum.entity.JournalStatus;
import com.cogitosum.repository.GeneralLedgerRepository;
import com.cogitosum.repository.ChartOfAccountRepository;
import com.cogitosum.repository.JournalEntryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    @Autowired
    private CurrentCompanyContext companyContext;

    public Map<String, Object> getTrialBalance() {
        Long companyId = companyContext.requireCompanyId();
        List<GeneralLedger> ledgers = generalLedgerRepository.findAllByCompanyIdOrderByAccountAccountNumberAsc(companyId);

        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;
        Map<String, Object> trialBalance = new HashMap<>();
        Map<String, Map<String, Object>> accounts = new LinkedHashMap<>();
        Map<AccountType, BigDecimal> categoryTotals = new HashMap<>();
        for (AccountType type : AccountType.values()) {
            categoryTotals.put(type, BigDecimal.ZERO);
        }

        for (ChartOfAccount account : chartOfAccountRepository.findAllByCompanyIdOrderByAccountNumberAsc(companyId)) {
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

    /**
     * Builds date-bounded reports from posted journal entries rather than the
     * current ledger snapshot. Closing journals are included in the post-close
     * trial balance and balance sheet, but excluded from the before-close
     * trial balance and period income statement.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getFinancialReports(LocalDate periodStart, LocalDate periodEnd, LocalDate asOf) {
        if (periodStart == null || periodEnd == null || asOf == null) {
            throw new IllegalArgumentException("Report dates are required");
        }
        if (periodStart.isAfter(periodEnd)) {
            throw new IllegalArgumentException("The income statement start date must not be after its end date");
        }
        Long companyId = companyContext.requireCompanyId();
        List<ChartOfAccount> accounts =
                chartOfAccountRepository.findAllByCompanyIdOrderByAccountNumberAsc(companyId);
        List<JournalEntry> throughDate = journalEntryRepository.findReportEntriesThroughDate(
                companyId, List.of(JournalStatus.POSTED, JournalStatus.REVERSED), asOf);
        List<JournalEntry> inPeriod = journalEntryRepository.findReportEntriesBetweenDates(
                companyId, List.of(JournalStatus.POSTED, JournalStatus.REVERSED), periodStart, periodEnd);

        Map<Long, String> closingJournalNumbers = closingJournalNumbers(throughDate);
        Map<Long, AccountMovements> postCloseMovements = aggregateMovements(throughDate);
        Map<Long, AccountMovements> preCloseMovements =
                aggregateMovements(withoutClosingJournals(throughDate, closingJournalNumbers));
        Map<Long, AccountMovements> incomeMovements =
                aggregateMovements(withoutClosingJournals(inPeriod, closingJournalNumbers));

        Map<String, Object> reports = new LinkedHashMap<>();
        reports.put("trialBalance", datedTrialBalance(accounts, throughDate, postCloseMovements));
        reports.put("trialBalanceBeforeClose",
                datedTrialBalance(accounts, withoutClosingJournals(throughDate, closingJournalNumbers),
                        preCloseMovements));
        reports.put("balanceSheet", datedBalanceSheet(accounts, postCloseMovements));
        reports.put("incomeStatement", datedIncomeStatement(accounts, incomeMovements));
        return reports;
    }

    private Map<String, Object> datedTrialBalance(List<ChartOfAccount> accounts, List<JournalEntry> entries,
                                                   Map<Long, AccountMovements> movements) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Map<String, Object>> rows = new LinkedHashMap<>();
        Map<AccountType, BigDecimal> categoryTotals = new LinkedHashMap<>();
        for (AccountType type : AccountType.values()) {
            categoryTotals.put(type, BigDecimal.ZERO);
        }
        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;
        for (JournalEntry entry : entries) {
            totalDebits = totalDebits.add(zero(entry.getDebit()));
            totalCredits = totalCredits.add(zero(entry.getCredit()));
        }
        for (ChartOfAccount account : accounts) {
            AccountMovements amount = movements.getOrDefault(account.getId(), new AccountMovements());
            BigDecimal balance = calculateBalance(account.getAccountType(), amount.debit, amount.credit);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("accountId", account.getId());
            row.put("accountNumber", account.getAccountNumber());
            row.put("accountName", account.getAccountName());
            row.put("accountType", account.getAccountType());
            row.put("debitBalance", amount.debit);
            row.put("creditBalance", amount.credit);
            row.put("balance", balance);
            rows.put(account.getAccountNumber(), row);
            if (account.getAccountType() != null) {
                categoryTotals.merge(account.getAccountType(), balance, BigDecimal::add);
            }
        }
        result.put("accounts", rows);
        result.put("categoryTotals", categoryTotals);
        result.put("totalDebits", totalDebits);
        result.put("totalCredits", totalCredits);
        result.put("balanced", totalDebits.compareTo(totalCredits) == 0);
        return result;
    }

    private Map<String, Object> datedBalanceSheet(List<ChartOfAccount> accounts,
                                                   Map<Long, AccountMovements> movements) {
        Map<String, Object> result = new LinkedHashMap<>();
        AccountSection assets = datedSection(accounts, movements, AccountType.ASSET, AccountType.CONTRA_ASSET);
        AccountSection liabilities = datedSection(accounts, movements, AccountType.LIABILITY, AccountType.CONTRA_LIABILITY);
        AccountSection equity = datedSection(accounts, movements, AccountType.EQUITY);
        AccountSection revenue = datedSection(accounts, movements, AccountType.REVENUE);
        AccountSection expenses = datedSection(accounts, movements, AccountType.EXPENSE);
        BigDecimal netIncome = revenue.total.subtract(expenses.total);
        BigDecimal equityAndIncome = equity.total.add(netIncome);
        BigDecimal liabilitiesAndEquity = liabilities.total.add(equityAndIncome);
        result.put("assets", assets.rows);
        result.put("totalAssets", assets.total);
        result.put("liabilities", liabilities.rows);
        result.put("totalLiabilities", liabilities.total);
        result.put("equity", equity.rows);
        result.put("totalEquity", equity.total);
        result.put("netIncome", netIncome);
        result.put("totalEquityPlusNetIncome", equityAndIncome);
        result.put("totalLiabilitiesAndEquity", liabilitiesAndEquity);
        result.put("balanced", assets.total.compareTo(liabilitiesAndEquity) == 0);
        return result;
    }

    private Map<String, Object> datedIncomeStatement(List<ChartOfAccount> accounts,
                                                      Map<Long, AccountMovements> movements) {
        AccountSection revenue = datedSection(accounts, movements, AccountType.REVENUE);
        AccountSection expenses = datedSection(accounts, movements, AccountType.EXPENSE);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("revenue", revenue.rows);
        result.put("totalRevenue", revenue.total);
        result.put("expenses", expenses.rows);
        result.put("totalExpenses", expenses.total);
        result.put("netIncome", revenue.total.subtract(expenses.total));
        return result;
    }

    private AccountSection datedSection(List<ChartOfAccount> accounts, Map<Long, AccountMovements> movements,
                                        AccountType... types) {
        AccountSection section = new AccountSection();
        AccountType primaryType = types.length == 0 ? null : types[0];
        for (AccountType type : types) {
            for (ChartOfAccount account : accounts) {
                if (account.getAccountType() != type) continue;
                AccountMovements amount = movements.get(account.getId());
                if (amount == null) continue;
                BigDecimal balance = calculateBalance(type, amount.debit, amount.credit);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("accountId", account.getId());
                row.put("accountNumber", account.getAccountNumber());
                row.put("accountName", account.getAccountName());
                row.put("balance", balance);
                section.rows.add(row);
                section.total = type == primaryType
                        ? section.total.add(balance) : section.total.subtract(balance);
            }
        }
        return section;
    }

    private Map<Long, AccountMovements> aggregateMovements(List<JournalEntry> entries) {
        Map<Long, AccountMovements> result = new HashMap<>();
        for (JournalEntry entry : entries) {
            if (entry.getAccount() == null) continue;
            AccountMovements amount = result.computeIfAbsent(entry.getAccount().getId(), ignored -> new AccountMovements());
            amount.debit = amount.debit.add(zero(entry.getDebit()));
            amount.credit = amount.credit.add(zero(entry.getCredit()));
        }
        return result;
    }

    private Map<Long, String> closingJournalNumbers(List<JournalEntry> entries) {
        Map<Long, String> closingJournalNumbers = new HashMap<>();
        for (JournalEntry entry : entries) {
            String reference = entry.getJournal().getReference();
            if (reference != null && reference.startsWith("FY-CLOSE-")) {
                closingJournalNumbers.put(entry.getJournal().getId(), entry.getJournal().getJournalNumber());
            }
        }
        return closingJournalNumbers;
    }

    private List<JournalEntry> withoutClosingJournals(List<JournalEntry> entries,
                                                       Map<Long, String> closingJournalNumbers) {
        return entries.stream().filter(entry -> {
            String reference = entry.getJournal().getReference();
            if (reference != null && reference.startsWith("FY-CLOSE-")) return false;
            if (reference != null) {
                for (String journalNumber : closingJournalNumbers.values()) {
                    if (reference.equals("Reversal of " + journalNumber)) return false;
                }
            }
            return true;
        }).toList();
    }

    private BigDecimal zero(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    public Map<String, Object> getAccountDetail(Long accountId) {
        Map<String, Object> detail = new HashMap<>();

        Optional<GeneralLedger> ledger = generalLedgerRepository.findByCompanyIdAndAccountId(
                companyContext.requireCompanyId(), accountId);
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
        List<JournalEntry> entries = journalEntryRepository.findByJournalCompanyIdAndAccountId(
                companyContext.requireCompanyId(), accountId);
        return entries.stream()
                .map(e -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("date", e.getJournal().getJournalDate());
                    row.put("journalNumber", e.getJournal().getJournalNumber());
                    row.put("description", e.getDescription() != null ? e.getDescription() : e.getJournal().getNarrative());
                    row.put("debit", e.getDebit());
                    row.put("credit", e.getCredit());
                    row.put("journalId", e.getJournal().getId());
                    row.put("status", e.getJournal().getStatus());
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
            Long companyId = companyContext.requireCompanyId();
            List<ChartOfAccount> accounts = chartOfAccountRepository
                    .findByCompanyIdAndAccountTypeOrderByAccountNumberAsc(companyId, type);

            for (ChartOfAccount account : accounts) {
                Optional<GeneralLedger> ledger = generalLedgerRepository.findByCompanyIdAndAccountId(
                        companyId, account.getId());
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
        return generateTrialBalanceCsv(getTrialBalance());
    }

    public String generateTrialBalanceCsv(Map<String, Object> trialBalance) {
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
        return generateBalanceSheetCsv(getBalanceSheet());
    }

    public String generateBalanceSheetCsv(Map<String, Object> bs) {
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
        return generateIncomeStatementCsv(getIncomeStatement());
    }

    public String generateIncomeStatementCsv(Map<String, Object> is) {
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

    public String generateFinancialStatementsTxt(Map<String, Object> reports,
                                                  LocalDate periodStart, LocalDate periodEnd, LocalDate asOf) {
        StringBuilder txt = new StringBuilder();
        txt.append("FINANCIAL STATEMENTS\n")
                .append("As of: ").append(asOf).append('\n')
                .append("Income statement period: ").append(periodStart).append(" to ").append(periodEnd)
                .append("\n\n");
        appendDatedTrialBalanceTxt(txt, castReportMap(reports.get("trialBalanceBeforeClose")),
                "TRIAL BALANCE — BEFORE CLOSING");
        appendDatedTrialBalanceTxt(txt, castReportMap(reports.get("trialBalance")),
                "TRIAL BALANCE — AFTER CLOSING");
        Map<String, Object> balanceSheet = castReportMap(reports.get("balanceSheet"));
        txt.append("BALANCE SHEET\n");
        appendDatedSectionTxt(txt, "ASSETS", balanceSheet.get("assets"), balanceSheet.get("totalAssets"));
        appendDatedSectionTxt(txt, "LIABILITIES", balanceSheet.get("liabilities"), balanceSheet.get("totalLiabilities"));
        appendDatedSectionTxt(txt, "EQUITY", balanceSheet.get("equity"), balanceSheet.get("totalEquity"));
        txt.append("Net income (unclosed): ").append(balanceSheet.get("netIncome")).append("\n")
                .append("Total liabilities and equity: ")
                .append(balanceSheet.get("totalLiabilitiesAndEquity")).append("\n\n");
        Map<String, Object> income = castReportMap(reports.get("incomeStatement"));
        txt.append("INCOME STATEMENT\n");
        appendDatedSectionTxt(txt, "REVENUE", income.get("revenue"), income.get("totalRevenue"));
        appendDatedSectionTxt(txt, "EXPENSES", income.get("expenses"), income.get("totalExpenses"));
        txt.append("Net income: ").append(income.get("netIncome")).append('\n');
        return txt.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castReportMap(Object value) {
        return (Map<String, Object>) value;
    }

    private void appendDatedTrialBalanceTxt(StringBuilder txt, Map<String, Object> trialBalance, String title) {
        txt.append(title).append('\n');
        Map<String, Map<String, Object>> rows =
                (Map<String, Map<String, Object>>) trialBalance.get("accounts");
        if (rows != null) {
            for (Map<String, Object> row : rows.values()) {
                txt.append(row.get("accountNumber")).append(" | ").append(row.get("accountName")).append(" | ")
                        .append(row.get("accountType")).append(" | Dr ").append(row.get("debitBalance"))
                        .append(" | Cr ").append(row.get("creditBalance")).append('\n');
            }
        }
        txt.append("Debits: ").append(trialBalance.get("totalDebits"))
                .append(" | Credits: ").append(trialBalance.get("totalCredits")).append("\n\n");
    }

    @SuppressWarnings("unchecked")
    private void appendDatedSectionTxt(StringBuilder txt, String name, Object rawRows, Object total) {
        txt.append(name).append('\n');
        List<Map<String, Object>> rows = (List<Map<String, Object>>) rawRows;
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                txt.append(row.get("accountNumber")).append(" | ").append(row.get("accountName"))
                        .append(" | ").append(row.get("balance")).append('\n');
            }
        }
        txt.append("Total ").append(name).append(": ").append(total).append('\n');
    }

    private String truncate(String text, int length) {
        if (text == null) return "";
        return text.length() > length ? text.substring(0, length) : text;
    }

    private static class AccountSection {
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
    }

    private static class AccountMovements {
        private BigDecimal debit = BigDecimal.ZERO;
        private BigDecimal credit = BigDecimal.ZERO;
    }
}
