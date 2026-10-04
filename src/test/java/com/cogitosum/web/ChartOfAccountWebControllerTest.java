package com.cogitosum.web;

import com.cogitosum.entity.AccountCategory;
import com.cogitosum.entity.AccountType;
import com.cogitosum.entity.ChartOfAccount;
import com.cogitosum.service.ChartOfAccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
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

    @Test
    public void list_ReturnsView() throws Exception {
        when(chartOfAccountService.getAllAccounts()).thenReturn(new ArrayList<>());
        mockMvc.perform(get("/accounts"))
                .andExpect(status().isOk())
                .andExpect(view().name("accounts/list"))
                .andExpect(model().attributeExists("accounts", "accountTypes"));
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
