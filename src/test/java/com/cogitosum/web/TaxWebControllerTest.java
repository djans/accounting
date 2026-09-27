package com.cogitosum.web;

import com.cogitosum.entity.TaxAgency;
import com.cogitosum.entity.TaxCode;
import com.cogitosum.entity.TaxGroup;
import com.cogitosum.repository.ChartOfAccountRepository;
import com.cogitosum.service.TaxAgencyService;
import com.cogitosum.service.TaxCodeService;
import com.cogitosum.service.TaxFilingService;
import com.cogitosum.repository.GeneralLedgerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TaxWebController.class)
public class TaxWebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TaxCodeService taxCodeService;

    @MockitoBean
    private TaxAgencyService taxAgencyService;

    @MockitoBean
    private TaxFilingService taxFilingService;

    @MockitoBean
    private ChartOfAccountRepository accountRepository;

    @MockitoBean
    private GeneralLedgerRepository ledgerRepository;

    @Test
    public void codes_ReturnsView() throws Exception {
        when(taxCodeService.getAllCodes()).thenReturn(new ArrayList<>());
        when(taxCodeService.getAllGroups()).thenReturn(new ArrayList<>());
        when(taxAgencyService.getAll()).thenReturn(new ArrayList<>());
        when(accountRepository.findAllByOrderByAccountNumberAsc()).thenReturn(new ArrayList<>());

        mockMvc.perform(get("/tax/codes"))
                .andExpect(status().isOk())
                .andExpect(view().name("tax/codes"))
                .andExpect(model().attributeExists("codes", "groups", "agencies", "accounts"));
    }

    @Test
    public void saveCode_Success() throws Exception {
        when(taxCodeService.createCode(any(TaxCode.class))).thenReturn(new TaxCode());
        when(taxCodeService.getGroupById(anyLong())).thenReturn(Optional.of(new TaxGroup()));

        mockMvc.perform(post("/tax/codes")
                .param("code", "TPS")
                .param("name", "TPS 5%")
                .param("salesTaxGroupId", "1")
                .param("purchaseTaxGroupId", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/tax/codes"))
                .andExpect(flash().attributeExists("flashSuccess"));

        verify(taxCodeService).createCode(any(TaxCode.class));
    }

    @Test
    public void deleteCode_Success() throws Exception {
        mockMvc.perform(post("/tax/codes/delete/1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/tax/codes"))
                .andExpect(flash().attribute("flashSuccess", "Tax code deactivated"));

        verify(taxCodeService).deleteCode(1L);
    }

    @Test
    public void deleteCode_Failure() throws Exception {
        doThrow(new RuntimeException("FK constraint")).when(taxCodeService).deleteCode(anyLong());

        mockMvc.perform(post("/tax/codes/delete/1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/tax/codes"))
                .andExpect(flash().attribute("flashError", containsString("Could not deactivate tax code: FK constraint")));
    }

    @Test
    public void saveGroup_Success() throws Exception {
        TaxGroup group = new TaxGroup();
        group.setId(1L);
        when(taxCodeService.createGroup(any(TaxGroup.class))).thenReturn(group);

        mockMvc.perform(post("/tax/groups")
                .param("code", "QC-GROUP")
                .param("name", "QC Group")
                .param("itemIds", "1", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/tax/codes"))
                .andExpect(flash().attributeExists("flashSuccess"));

        verify(taxCodeService).createGroup(any(TaxGroup.class));
    }

    @Test
    public void deleteGroup_Success() throws Exception {
        mockMvc.perform(post("/tax/groups/delete/1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/tax/codes"))
                .andExpect(flash().attribute("flashSuccess", "Tax group deactivated"));

        verify(taxCodeService).deleteGroup(1L);
    }

    @Test
    public void deleteItem_Success() throws Exception {
        mockMvc.perform(post("/tax/items/delete/1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/tax/codes"))
                .andExpect(flash().attribute("flashSuccess", "Tax item deactivated"));

        verify(taxCodeService).deleteItem(1L);
    }

    @Test
    public void reconciliation_ReturnsView() throws Exception {
        com.cogitosum.entity.TaxAgency agency = new com.cogitosum.entity.TaxAgency();
        agency.setCode("CRA");
        agency.setName("CRA Name");
        
        com.cogitosum.entity.TaxItem item = new com.cogitosum.entity.TaxItem();
        item.setCode("TPS");
        item.setName("TPS Name");
        
        com.cogitosum.entity.ChartOfAccount acc = new com.cogitosum.entity.ChartOfAccount();
        acc.setId(1L);
        acc.setAccountNumber("2310");
        acc.setAccountName("TPS Payable");
        item.setPayableAccount(acc);
        
        java.util.List<com.cogitosum.entity.TaxAgency> agencies = new java.util.ArrayList<>();
        agencies.add(agency);
        when(taxAgencyService.getAll()).thenReturn(agencies);
        
        java.util.List<com.cogitosum.entity.TaxItem> items = new java.util.ArrayList<>();
        items.add(item);
        when(taxCodeService.getItemsByAgency(any())).thenReturn(items);
        
        mockMvc.perform(get("/tax/reconciliation"))
                .andExpect(status().isOk())
                .andExpect(view().name("tax/reconciliation"))
                .andExpect(model().attributeExists("rows"));
    }
}
