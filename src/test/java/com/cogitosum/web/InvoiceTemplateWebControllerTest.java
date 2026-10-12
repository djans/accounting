package com.cogitosum.web;

import com.cogitosum.service.CurrentCompanyContext;
import com.cogitosum.service.InvoiceTemplateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InvoiceTemplateWebController.class)
@AutoConfigureMockMvc(addFilters = false)
class InvoiceTemplateWebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InvoiceTemplateService templateService;

    @MockitoBean
    private CurrentCompanyContext companyContext;

    @Test
    void downloadsTheSelectedDocxTemplateAsAnAttachment() throws Exception {
        byte[] data = {1, 2, 3};
        when(templateService.getTemplateForCurrentCompany("template-id"))
                .thenReturn(new InvoiceTemplateService.TemplateContent(data, "fr", "facture-fr.docx"));

        mockMvc.perform(get("/admin/invoice-templates/template-id/download"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(content().bytes(data));

        verify(templateService).getTemplateForCurrentCompany("template-id");
    }
}
