package com.cogitosum.web;

import com.cogitosum.entity.TaxAgency;
import com.cogitosum.entity.TaxCode;
import com.cogitosum.entity.TaxFilingPeriod;
import com.cogitosum.entity.TaxFilingStatus;
import com.cogitosum.entity.TaxGroup;
import com.cogitosum.entity.TaxItem;
import com.cogitosum.dto.TaxReturnRowDTO;
import com.cogitosum.dto.TaxReturnLineDetailDTO;
import com.cogitosum.repository.ChartOfAccountRepository;
import com.cogitosum.service.TaxAgencyService;
import com.cogitosum.service.TaxCodeService;
import com.cogitosum.service.TaxFilingService;
import com.cogitosum.service.TaxReturnReportPdfService;
import com.cogitosum.service.CurrentCompanyContext;
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
    private TaxReturnReportPdfService returnReportPdfService;

    @MockitoBean
    private CurrentCompanyContext companyContext;

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
    public void periodLineDetailsShowsTheSelectedReturnLineSources() throws Exception {
        TaxAgency agency = new TaxAgency();
        agency.setId(10L);
        agency.setCode("CRA");
        TaxFilingPeriod period = new TaxFilingPeriod();
        period.setId(1L);
        period.setAgency(agency);
        period.setPeriodStart(java.time.LocalDate.of(2026, 1, 1));
        period.setPeriodEnd(java.time.LocalDate.of(2026, 1, 31));
        period.setStatus(TaxFilingStatus.OPEN);
        TaxReturnRowDTO row = new TaxReturnRowDTO(
                "tax.detail.returnLine103", "103", new BigDecimal("5.00"), null, false, false);
        TaxReturnLineDetailDTO source = new TaxReturnLineDetailDTO(
                "INVOICE", 71L, java.time.LocalDate.of(2026, 1, 15),
                "INV-71", "GST — Customer", null, new BigDecimal("5.00"));
        when(taxFilingService.getById(1L)).thenReturn(Optional.of(period));
        when(taxFilingService.getReturnLineBreakdown(period)).thenReturn(java.util.List.of());
        when(taxFilingService.getTaxReturnRows(period, java.util.List.of())).thenReturn(java.util.List.of(row));
        when(taxFilingService.getReturnLineDetails(period, "103")).thenReturn(java.util.List.of(source));

        mockMvc.perform(get("/tax/periods/1/lines/103"))
                .andExpect(status().isOk())
                .andExpect(view().name("tax/period-line-detail"))
                .andExpect(model().attribute("returnRow", row))
                .andExpect(model().attribute("lineDetails", java.util.List.of(source)));
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
    public void saveItem_SavesSeparateReturnLines() throws Exception {
        TaxAgency agency = new TaxAgency();
        agency.setId(1L);
        when(taxAgencyService.getById(1L)).thenReturn(Optional.of(agency));
        when(companyContext.requireCompanyId()).thenReturn(1L);

        mockMvc.perform(post("/tax/items")
                .param("code", "GST")
                .param("name", "GST")
                .param("rate", "0.05")
                .param("agencyId", "1")
                .param("salesReturnLine", "103")
                .param("purchaseReturnLine", "106"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/tax/codes"))
                .andExpect(flash().attributeExists("flashSuccess"));

        org.mockito.ArgumentCaptor<TaxItem> itemCaptor =
                org.mockito.ArgumentCaptor.forClass(TaxItem.class);
        verify(taxCodeService).createItem(itemCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("103", itemCaptor.getValue().getSalesReturnLine());
        org.junit.jupiter.api.Assertions.assertEquals("106", itemCaptor.getValue().getPurchaseReturnLine());
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
    public void saveGroup_UpdateUsesSubmittedItemIds() throws Exception {
        TaxGroup group = new TaxGroup();
        group.setId(1L);
        when(taxCodeService.updateGroup(eq(1L), any(TaxGroup.class))).thenReturn(group);

        mockMvc.perform(post("/tax/groups")
                .param("id", "1")
                .param("code", "QC-GROUP")
                .param("name", "QC Group")
                .param("itemIds", "7", "8"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("flashSuccess"));

        org.mockito.ArgumentCaptor<TaxGroup> groupCaptor =
                org.mockito.ArgumentCaptor.forClass(TaxGroup.class);
        verify(taxCodeService).updateGroup(eq(1L), groupCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(
                java.util.List.of(7L, 8L),
                groupCaptor.getValue().getTaxItems().stream().map(TaxItem::getId).toList());
    }

    @Test
    public void saveGroup_FailureWithoutMessageShowsExceptionTypeInsteadOfNull() throws Exception {
        doThrow(new NullPointerException()).when(taxCodeService).createGroup(any(TaxGroup.class));

        mockMvc.perform(post("/tax/groups")
                .param("code", "FED")
                .param("name", "Federal")
                .param("itemIds", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("flashError",
                        containsString("NullPointerException without details")));
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
    public void fileReturnRedirectsToDownloadableReport() throws Exception {
        mockMvc.perform(post("/tax/periods/45/file"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/tax/periods/45/report"));

        verify(taxFilingService).file(45L, "portal");
    }

    @Test
    public void reportDownloadIsNotFoundWhenPeriodIsNotFiledOrOutsideCompany() throws Exception {
        when(taxFilingService.getFiledReturnReport(45L)).thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/tax/periods/45/report"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(returnReportPdfService);
    }

    @Test
    public void reportDownloadReturnsPdfForFiledPeriod() throws Exception {
        TaxFilingService.TaxReturnReportData report = new TaxFilingService.TaxReturnReportData(
                "Company", "CRA", java.time.LocalDate.of(2026, 7, 1),
                java.time.LocalDate.of(2026, 9, 30), java.util.List.of(), false);
        byte[] pdf = new byte[]{1, 2, 3};
        when(taxFilingService.getFiledReturnReport(45L)).thenReturn(java.util.Optional.of(report));
        when(returnReportPdfService.create(any(), any())).thenReturn(pdf);

        mockMvc.perform(get("/tax/periods/45/report"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition",
                        containsString("attachment; filename=\"tax-return-45.pdf\"")))
                .andExpect(content().bytes(pdf));
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
