package com.cogitosum.web;

import com.cogitosum.entity.AccountType;
import com.cogitosum.entity.ChartOfAccount;
import com.cogitosum.entity.GeneralLedger;
import com.cogitosum.repository.ChartOfAccountRepository;
import com.cogitosum.repository.GeneralLedgerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReconcileWebController.class)
public class ReconcileWebControllerTest {

    @Autowired private MockMvc mvc;
    @MockBean private ChartOfAccountRepository accountRepository;
    @MockBean private GeneralLedgerRepository ledgerRepository;

    @Test
    public void reconcile_populatesRows() throws Exception {
        ChartOfAccount acct = new ChartOfAccount();
        acct.setId(1L);
        acct.setAccountNumber("1001");
        acct.setAccountName("Bank A");
        acct.setAccountType(AccountType.ASSET);
        when(accountRepository.findByAccountType(AccountType.ASSET)).thenReturn(List.of(acct));

        GeneralLedger gl = new GeneralLedger();
        gl.setAccount(acct);
        gl.setDebitBalance(new BigDecimal("100.00"));
        gl.setCreditBalance(BigDecimal.ZERO);
        when(ledgerRepository.findByAccountId(acct.getId())).thenReturn(Optional.of(gl));

        mvc.perform(get("/reconcile"))
            .andExpect(status().isOk())
            .andExpect(model().attributeExists("rows"))
            .andExpect(view().name("reconcile"));
    }
}
