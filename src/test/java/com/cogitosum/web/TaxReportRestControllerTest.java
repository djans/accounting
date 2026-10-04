package com.cogitosum.web;

import com.cogitosum.controller.TaxReportRestController;
import com.cogitosum.entity.TaxAgency;
import com.cogitosum.entity.TaxCode;
import com.cogitosum.service.TaxAgencyService;
import com.cogitosum.service.TaxCodeService;
import com.cogitosum.service.TaxFilingService;
import com.cogitosum.repository.GeneralLedgerRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaxReportRestController.class)
class TaxReportRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TaxAgencyService agencyService;

    @MockitoBean
    private TaxCodeService codeService;

    @MockitoBean
    private TaxFilingService filingService;

    @MockitoBean
    private GeneralLedgerRepository ledgerRepository;

    @Test
    void agenciesReturnsList() throws Exception {
        TaxAgency agency = Mockito.mock(TaxAgency.class);
        when(agency.getId()).thenReturn(1L);
        when(agency.getCode()).thenReturn("CRA");
        when(agency.getName()).thenReturn("Canada Revenue Agency");

        when(agencyService.getAll()).thenReturn(List.of(agency));

        mockMvc.perform(get("/api/tax/agencies").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("CRA"))
                .andExpect(jsonPath("$[0].name").value("Canada Revenue Agency"));
    }

    @Test
    void codesReturnsList() throws Exception {
        TaxCode code = Mockito.mock(TaxCode.class);
        when(code.getId()).thenReturn(11L);
        when(code.getCode()).thenReturn("TPS");
        when(code.getName()).thenReturn("TPS 5%");

        when(codeService.getAllCodes()).thenReturn(List.of(code));

        mockMvc.perform(get("/api/tax/codes").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("TPS"))
                .andExpect(jsonPath("$[0].name").value("TPS 5%"));
    }

    @Test
    void periodsReturnsList() throws Exception {
        // Create a TaxFilingPeriod with agency
        com.cogitosum.entity.TaxFilingPeriod period = new com.cogitosum.entity.TaxFilingPeriod();
        com.cogitosum.entity.TaxAgency agency = Mockito.mock(com.cogitosum.entity.TaxAgency.class);
        when(agency.getCode()).thenReturn("CRA");
        period.setAgency(agency);
        period.setId(42L);
        period.setStatus(com.cogitosum.entity.TaxFilingStatus.OPEN);
        period.setTaxCollected(java.math.BigDecimal.ZERO);
        period.setTaxItc(java.math.BigDecimal.ZERO);
        period.setNetOwing(java.math.BigDecimal.ZERO);

        when(filingService.getAll()).thenReturn(List.of(period));

        mockMvc.perform(get("/api/tax/periods").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].agency.code").value("CRA"))
                .andExpect(jsonPath("$[0].status").value("OPEN"));
    }

    @Test
    void periodByIdReturnsObject() throws Exception {
        com.cogitosum.entity.TaxFilingPeriod period = new com.cogitosum.entity.TaxFilingPeriod();
        com.cogitosum.entity.TaxAgency agency = Mockito.mock(com.cogitosum.entity.TaxAgency.class);
        when(agency.getCode()).thenReturn("CRA");
        period.setAgency(agency);
        period.setId(99L);
        period.setStatus(com.cogitosum.entity.TaxFilingStatus.FILED);
        period.setTaxCollected(java.math.BigDecimal.ZERO);
        period.setTaxItc(java.math.BigDecimal.ZERO);
        period.setNetOwing(java.math.BigDecimal.ZERO);

        when(filingService.getById(99L)).thenReturn(java.util.Optional.of(period));
        when(filingService.getReturnLineBreakdown(period)).thenReturn(List.of());
        when(filingService.getTaxReturnRows(period, List.of())).thenReturn(List.of());
        when(filingService.hasUnmappedReturnLineAmounts(period, List.of())).thenReturn(false);

        mockMvc.perform(get("/api/tax/periods/99").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.agency.code").value("CRA"))
                .andExpect(jsonPath("$.period.status").value("FILED"))
                .andExpect(jsonPath("$.collectedDetail").isArray())
                .andExpect(jsonPath("$.itcDetail").isArray())
                .andExpect(jsonPath("$.returnLineBreakdown").isArray())
                .andExpect(jsonPath("$.taxReturnRows").isArray())
                .andExpect(jsonPath("$.hasUnmappedReturnLineAmounts").value(false));
    }

    @Test
    void reconciliationReturnsEmptyWhenNoAgencies() throws Exception {
        when(agencyService.getAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/tax/reconciliation").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
