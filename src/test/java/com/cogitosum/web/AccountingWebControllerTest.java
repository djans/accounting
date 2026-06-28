package com.cogitosum.web;

import com.cogitosum.service.AccountingReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccountingWebController.class)
public class AccountingWebControllerTest {

    @Autowired private MockMvc mvc;
    @MockBean private AccountingReportService accountingReportService;

    @Test
    public void trialBalance_showsModelAttributes() throws Exception {
        Map<String,Object> tb = new HashMap<>();
        Map<String, Map<String,Object>> accounts = new HashMap<>();
        Map<String,Object> a = new HashMap<>();
        a.put("accountNumber","1001");
        a.put("accountName","Bank A");
        a.put("debitBalance", new BigDecimal("100.00"));
        a.put("creditBalance", new BigDecimal("0.00"));
        accounts.put("1001", a);
        tb.put("accounts", accounts);
        tb.put("totalDebits", new BigDecimal("100.00"));
        tb.put("totalCredits", new BigDecimal("0.00"));
        tb.put("balanced", false);

        when(accountingReportService.getTrialBalance()).thenReturn(tb);

        mvc.perform(get("/accounting/trial-balance"))
           .andExpect(status().isOk())
           .andExpect(model().attributeExists("accounts","totalDebits","totalCredits","balanced"))
           .andExpect(view().name("reports/trial-balance"));
    }
}
