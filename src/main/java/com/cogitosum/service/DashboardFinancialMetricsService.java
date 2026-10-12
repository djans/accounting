package com.cogitosum.service;

import com.cogitosum.entity.AccountCategory;
import com.cogitosum.entity.ChartOfAccount;
import com.cogitosum.entity.Company;
import com.cogitosum.entity.GeneralLedger;
import com.cogitosum.entity.TaxItem;
import com.cogitosum.repository.GeneralLedgerRepository;
import com.cogitosum.repository.TaxItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
public class DashboardFinancialMetricsService {

    private final CurrentCompanyContext companyContext;
    private final GeneralLedgerRepository ledgerRepository;
    private final TaxItemRepository taxItemRepository;
    private final AccountingReportService reportService;

    public DashboardFinancialMetricsService(CurrentCompanyContext companyContext,
                                            GeneralLedgerRepository ledgerRepository,
                                            TaxItemRepository taxItemRepository,
                                            AccountingReportService reportService) {
        this.companyContext = companyContext;
        this.ledgerRepository = ledgerRepository;
        this.taxItemRepository = taxItemRepository;
        this.reportService = reportService;
    }

    @Transactional(readOnly = true)
    public Metrics getMetrics(LocalDate asOf) {
        if (asOf == null) {
            throw new IllegalArgumentException("Dashboard report date is required");
        }
        Company company = companyContext.requireCompany();
        Long companyId = company.getId();
        LocalDate fiscalYearStart = fiscalYearStart(asOf, company.getFiscalYearStartMonth());

        Map<String, Object> reports =
                reportService.getFinancialReports(fiscalYearStart, asOf, asOf);
        Map<?, ?> incomeStatement = requireMap(reports.get("incomeStatement"), "income statement");
        BigDecimal revenue = requireAmount(incomeStatement.get("totalRevenue"), "total revenue");
        BigDecimal netProfit = requireAmount(incomeStatement.get("netIncome"), "net profit");

        Map<Long, GeneralLedger> ledgersByAccountId = new HashMap<>();
        for (GeneralLedger ledger : ledgerRepository.findAllByCompanyIdOrderByAccountAccountNumberAsc(companyId)) {
            if (ledger.getAccount() != null) {
                ledgersByAccountId.put(ledger.getAccount().getId(), ledger);
            }
        }

        BigDecimal cash = balanceForCategory(ledgersByAccountId, AccountCategory.BANK);
        BigDecimal receivables =
                balanceForCategory(ledgersByAccountId, AccountCategory.ACCOUNTS_RECEIVABLE);
        BigDecimal payables = balanceForCategory(ledgersByAccountId, AccountCategory.ACCOUNTS_PAYABLE);
        BigDecimal taxesPayable = taxesPayable(companyId, ledgersByAccountId);

        return new Metrics(revenue, netProfit, cash, taxesPayable, receivables, payables,
                fiscalYearStart, asOf);
    }

    private BigDecimal taxesPayable(Long companyId, Map<Long, GeneralLedger> ledgersByAccountId) {
        Set<Long> payableAccountIds = new HashSet<>();
        for (TaxItem taxItem : taxItemRepository.findAllByCompanyIdOrderByCodeAsc(companyId)) {
            ChartOfAccount payableAccount = taxItem.getPayableAccount();
            if (payableAccount != null) {
                payableAccountIds.add(payableAccount.getId());
            }
        }
        return payableAccountIds.stream()
                .map(ledgersByAccountId::get)
                .filter(ledger -> ledger != null)
                .map(GeneralLedger::getBalance)
                .map(DashboardFinancialMetricsService::zero)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal balanceForCategory(Map<Long, GeneralLedger> ledgersByAccountId,
                                           AccountCategory category) {
        return ledgersByAccountId.values().stream()
                .filter(ledger -> ledger.getAccount().getCategory() == category)
                .map(GeneralLedger::getBalance)
                .map(DashboardFinancialMetricsService::zero)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private LocalDate fiscalYearStart(LocalDate asOf, Integer configuredStartMonth) {
        int startMonth = configuredStartMonth == null ? 1 : configuredStartMonth;
        Month month = Month.of(startMonth);
        int year = asOf.getMonthValue() < startMonth ? asOf.getYear() - 1 : asOf.getYear();
        return LocalDate.of(year, month, 1);
    }

    private Map<?, ?> requireMap(Object value, String reportName) {
        if (value instanceof Map<?, ?> report) {
            return report;
        }
        throw new IllegalStateException("Accounting report did not return " + reportName);
    }

    private BigDecimal requireAmount(Object value, String amountName) {
        if (value instanceof BigDecimal amount) {
            return amount;
        }
        throw new IllegalStateException("Accounting report did not return " + amountName);
    }

    private static BigDecimal zero(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    public record Metrics(BigDecimal revenue, BigDecimal netProfit, BigDecimal cash,
                          BigDecimal taxesPayable, BigDecimal accountsReceivable,
                          BigDecimal accountsPayable, LocalDate periodStart, LocalDate asOf) {
    }
}
