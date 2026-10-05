package com.cogitosum.web;

import com.cogitosum.entity.AccountCategory;
import com.cogitosum.entity.AccountType;
import com.cogitosum.entity.ChartOfAccount;
import com.cogitosum.entity.GeneralLedger;
import com.cogitosum.entity.JournalStatus;
import com.cogitosum.service.AccountingReportService;
import com.cogitosum.service.ChartOfAccountService;
import com.cogitosum.service.GeneralLedgerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChartOfAccountWebController.class)
public class ChartOfAccountWebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChartOfAccountService chartOfAccountService;

    @MockitoBean
    private AccountingReportService accountingReportService;

    @MockitoBean
    private GeneralLedgerService generalLedgerService;

    @Test
    public void list_ReturnsView() throws Exception {
        when(chartOfAccountService.getAllAccounts()).thenReturn(new ArrayList<>());
        when(generalLedgerService.getAllLedgerAccounts()).thenReturn(List.of());
        mockMvc.perform(get("/accounts"))
                .andExpect(status().isOk())
                .andExpect(view().name("accounts/list"))
                .andExpect(model().attributeExists("accounts", "accountTypes", "accountEndingBalances"));
    }

    @Test
    public void list_ShowsEndingBalanceIncludingPostedLedgerActivity() throws Exception {
        ChartOfAccount account = new ChartOfAccount();
        account.setId(1L);
        account.setAccountType(AccountType.ASSET);
        account.setOpeningBalance(new BigDecimal("125.00"));
        GeneralLedger ledger = new GeneralLedger();
        ledger.setAccount(account);
        ledger.setBalance(new BigDecimal("50.00"));
        when(chartOfAccountService.getAllAccounts()).thenReturn(List.of(account));
        when(generalLedgerService.getAllLedgerAccounts()).thenReturn(List.of(ledger));

        mockMvc.perform(get("/accounts"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("accountEndingBalances",
                        Map.of(1L, new BigDecimal("175.00"))));
    }

    @Test
    public void newForm_ReturnsView() throws Exception {
        when(chartOfAccountService.getAllAccounts()).thenReturn(new ArrayList<>());
        mockMvc.perform(get("/accounts/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("accounts/form"))
                .andExpect(model().attributeExists("account", "types", "categories", "accounts", "isNew"));
    }

    @Test
    public void editForm_ReturnsView() throws Exception {
        ChartOfAccount a = new ChartOfAccount();
        a.setId(1L);
        a.setOpeningBalance(new BigDecimal("57867.87"));
        a.setOpeningBalanceDate(LocalDate.of(2026, 1, 1));
        when(chartOfAccountService.getAccountById(1L)).thenReturn(Optional.of(a));
        when(chartOfAccountService.getAllAccounts()).thenReturn(new ArrayList<>());

        mockMvc.perform(get("/accounts/1/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("accounts/form"))
                .andExpect(model().attributeExists("account", "types", "categories", "accounts", "isNew"))
                .andExpect(content().string(containsString("id=\"openingBalance\"")))
                .andExpect(content().string(containsString("data-original-value=\"57867.87\"")))
                .andExpect(content().string(containsString("id=\"openingBalanceDate\"")))
                .andExpect(content().string(containsString("role=\"alert\"")));
    }

    @Test
    public void detail_ShowsOpeningBalanceAndGeneralJournalEntries() throws Exception {
        ChartOfAccount account = new ChartOfAccount();
        account.setId(1L);
        account.setAccountNumber("1010");
        account.setAccountName("Cash");
        account.setAccountType(AccountType.ASSET);
        account.setOpeningBalance(new BigDecimal("125.00"));
        when(chartOfAccountService.getAccountById(1L)).thenReturn(Optional.of(account));
        GeneralLedger ledger = new GeneralLedger();
        ledger.setAccount(account);
        ledger.setBalance(new BigDecimal("50.00"));
        when(generalLedgerService.getLedgerByAccountId(1L)).thenReturn(Optional.of(ledger));

        Map<String, Object> entry = new HashMap<>();
        entry.put("date", LocalDate.of(2026, 1, 15));
        entry.put("journalNumber", "JE-2026-001");
        entry.put("description", "Customer payment");
        entry.put("status", JournalStatus.POSTED);
        entry.put("debit", new BigDecimal("50.00"));
        entry.put("credit", BigDecimal.ZERO);
        entry.put("journalId", 8L);
        when(accountingReportService.getAccountTransactions(1L)).thenReturn(List.of(entry));

        mockMvc.perform(get("/accounts/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("accounts/detail"))
                .andExpect(model().attributeExists("account", "journalEntries"))
                .andExpect(model().attribute("endingBalance", new BigDecimal("175.00")))
                .andExpect(content().string(containsString("Customer payment")))
                .andExpect(content().string(containsString("JE-2026-001")));
    }

    @Test
    public void create_RedirectsToList() throws Exception {
        when(chartOfAccountService.createAccount(any(ChartOfAccount.class))).thenReturn(new ChartOfAccount());
        mockMvc.perform(post("/accounts")
                .param("accountNumber", "1010")
                .param("accountName", "Cash")
                .param("accountType", "ASSET")
                .param("description", "Petty Cash"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/accounts"));
    }
}
