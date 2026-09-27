package com.cogitosum.web;

import com.cogitosum.entity.AccountCategory;
import com.cogitosum.entity.ChartOfAccount;
import com.cogitosum.service.BankReconciliationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReconcileWebController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReconcileWebControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private BankReconciliationService reconciliationService;

    @Test
    void reconcileListsOnlyBankAccountsProvidedByTheService() throws Exception {
        ChartOfAccount account = bankAccount(1L);
        when(reconciliationService.getBankAccounts()).thenReturn(List.of(
                new BankReconciliationService.BankAccountSummary(account, new BigDecimal("125.00"))));

        mvc.perform(get("/reconcile"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("rows"))
                .andExpect(view().name("reconcile"));
    }

    @Test
    void reconcileAccountPopulatesImportedTransactionsAndServerOpeningBalance() throws Exception {
        ChartOfAccount account = bankAccount(1L);
        when(reconciliationService.getBankAccount(1L)).thenReturn(account);
        when(reconciliationService.getUnreconciledTransactions(1L)).thenReturn(List.of());
        when(reconciliationService.getEligibleJournalEntries(1L)).thenReturn(List.of());
        when(reconciliationService.getOpeningBalance(1L)).thenReturn(new BigDecimal("25.00"));

        mvc.perform(get("/reconcile/account/1"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("account", account))
                .andExpect(model().attribute("openingBalance", new BigDecimal("25.00")))
                .andExpect(view().name("reconcile_details"));
    }

    @Test
    void finishParsesMatchParametersAndDelegatesServerValidation() throws Exception {
        mvc.perform(post("/reconcile/finish")
                        .param("accountId", "1")
                        .param("statementDate", "2026-01-31")
                        .param("statementEndingBalance", "100.00")
                        .param("match_10", "20"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/reconcile"));

        verify(reconciliationService).reconcile(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(LocalDate.of(2026, 1, 31)),
                org.mockito.ArgumentMatchers.eq(new BigDecimal("100.00")),
                anyMap());
    }

    private ChartOfAccount bankAccount(Long id) {
        ChartOfAccount account = new ChartOfAccount();
        account.setId(id);
        account.setAccountNumber("1001");
        account.setAccountName("Bank");
        account.setCategory(AccountCategory.BANK);
        account.setActive(true);
        return account;
    }
}
