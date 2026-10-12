package com.cogitosum.service;

import com.cogitosum.entity.AccountCategory;
import com.cogitosum.entity.AccountType;
import com.cogitosum.entity.ChartOfAccount;
import com.cogitosum.entity.Company;
import com.cogitosum.entity.GeneralLedger;
import com.cogitosum.entity.TaxItem;
import com.cogitosum.repository.GeneralLedgerRepository;
import com.cogitosum.repository.TaxItemRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardFinancialMetricsServiceTest {

    @Test
    void calculatesCompanyMetricsAndUsesFiscalYearToDateForIncome() {
        CurrentCompanyContext companyContext = mock(CurrentCompanyContext.class);
        GeneralLedgerRepository ledgerRepository = mock(GeneralLedgerRepository.class);
        TaxItemRepository taxItemRepository = mock(TaxItemRepository.class);
        AccountingReportService reportService = mock(AccountingReportService.class);
        Company company = new Company();
        company.setId(7L);
        company.setFiscalYearStartMonth(7);
        LocalDate asOf = LocalDate.of(2026, 10, 11);
        when(companyContext.requireCompany()).thenReturn(company);
        when(reportService.getFinancialReports(LocalDate.of(2026, 7, 1), asOf, asOf))
                .thenReturn(Map.of("incomeStatement", Map.of(
                        "totalRevenue", new BigDecimal("12000.00"),
                        "netIncome", new BigDecimal("3500.00"))));

        ChartOfAccount bank = account(1L, AccountCategory.BANK);
        ChartOfAccount receivable = account(2L, AccountCategory.ACCOUNTS_RECEIVABLE);
        ChartOfAccount payable = account(3L, AccountCategory.ACCOUNTS_PAYABLE);
        ChartOfAccount taxPayable = account(4L, AccountCategory.OTHER_CURRENT_LIABILITY);
        when(ledgerRepository.findAllByCompanyIdOrderByAccountAccountNumberAsc(7L))
                .thenReturn(List.of(ledger(bank, "500.00"), ledger(receivable, "800.00"),
                        ledger(payable, "225.00"), ledger(taxPayable, "75.00")));
        TaxItem salesTax = new TaxItem();
        salesTax.setPayableAccount(taxPayable);
        TaxItem anotherTaxItemSameAccount = new TaxItem();
        anotherTaxItemSameAccount.setPayableAccount(taxPayable);
        when(taxItemRepository.findAllByCompanyIdOrderByCodeAsc(7L))
                .thenReturn(List.of(salesTax, anotherTaxItemSameAccount));

        DashboardFinancialMetricsService.Metrics metrics =
                new DashboardFinancialMetricsService(companyContext, ledgerRepository, taxItemRepository,
                        reportService).getMetrics(asOf);

        assertEquals(new BigDecimal("12000.00"), metrics.revenue());
        assertEquals(new BigDecimal("3500.00"), metrics.netProfit());
        assertEquals(new BigDecimal("500.00"), metrics.cash());
        assertEquals(new BigDecimal("75.00"), metrics.taxesPayable());
        assertEquals(new BigDecimal("800.00"), metrics.accountsReceivable());
        assertEquals(new BigDecimal("225.00"), metrics.accountsPayable());
        assertEquals(LocalDate.of(2026, 7, 1), metrics.periodStart());
        verify(reportService).getFinancialReports(LocalDate.of(2026, 7, 1), asOf, asOf);
    }

    private ChartOfAccount account(Long id, AccountCategory category) {
        ChartOfAccount account = new ChartOfAccount();
        account.setId(id);
        account.setAccountType(category == AccountCategory.BANK
                || category == AccountCategory.ACCOUNTS_RECEIVABLE
                ? AccountType.ASSET : AccountType.LIABILITY);
        account.setCategory(category);
        return account;
    }

    private GeneralLedger ledger(ChartOfAccount account, String balance) {
        GeneralLedger ledger = new GeneralLedger();
        ledger.setAccount(account);
        ledger.setBalance(new BigDecimal(balance));
        return ledger;
    }
}
