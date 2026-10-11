package com.cogitosum.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccountingWebController.class)
public class AccountingWebControllerTest {

    @Autowired private MockMvc mvc;

    @Test
    public void trialBalance_redirectsToUnifiedFinancialReports() throws Exception {
        mvc.perform(get("/accounting/trial-balance"))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrl("/reports/accounting"));
    }
}
