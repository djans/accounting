package com.cogitosum.web;

import com.cogitosum.service.TaxAgencyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TaxAgencyPagesController.class)
public class TaxAgencyPagesControllerTest {

    @Autowired private MockMvc mvc;
    @MockBean private TaxAgencyService agencyService;

    @Test
    public void report_returnsAgenciesView() throws Exception {
        when(agencyService.getAll()).thenReturn(List.of());
        mvc.perform(get("/tax/agencies/report"))
            .andExpect(status().isOk())
            .andExpect(view().name("tax/agencies"));
    }
}
