package com.cogitosum.web;

import com.cogitosum.entity.Company;
import com.cogitosum.service.CompanyService;
import com.cogitosum.service.CurrentCompanyContext;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(CompanyWebController.class)
@AutoConfigureMockMvc(addFilters = false)
class CompanyWebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CompanyService companyService;

    @MockitoBean
    private CurrentCompanyContext companyContext;

    @Test
    void rendersCompanyEditFormForTheRequestedCompany() throws Exception {
        Company company = company(10L);
        when(companyService.getCompanyForEdit(10L)).thenReturn(company);

        mockMvc.perform(get("/companies/10/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("companies/form"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Edit company details")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("action=\"/companies/10\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("99 Main Street")));
    }

    @Test
    void postsCompanyProfileChangesToTheIdSpecificUpdateRoute() throws Exception {
        mockMvc.perform(post("/companies/10")
                        .param("name", "Updated company")
                        .param("legalName", "Updated Company Ltd.")
                        .param("email", "updated@example.test")
                        .param("phone", "555-0199")
                        .param("address", "99 Main Street")
                        .param("city", "Montréal")
                        .param("province", "QC")
                        .param("postalCode", "H0H 0H0")
                        .param("country", "Canada")
                        .param("businessNumber", "123456789"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/companies"));

        verify(companyService).updateCompany(eq(10L), argThat(
                submitted -> "Updated company".equals(submitted.getName())
                        && "99 Main Street".equals(submitted.getAddress())
                        && "123456789".equals(submitted.getBusinessNumber())),
                eq(null), eq(null), eq(false));
    }

    @Test
    void uploadsCompanyLogoWithTheProfileUpdate() throws Exception {
        byte[] logo = {
                (byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10,
                0, 0, 0, 13, 'I', 'H', 'D', 'R',
                0, 0, 0, 1, 0, 0, 0, 1,
                8, 6, 0, 0, 0, 31, 21, (byte) 0xC4, (byte) 0x89,
                0, 0, 0, 11, 'I', 'D', 'A', 'T', 120, (byte) 0x9C,
                99, 96, 96, 0, 2, 0, 0, 5, 0, 1,
                (byte) 0xA5, (byte) 0xF6, 69, 64,
                0, 0, 0, 0, 'I', 'E', 'N', 'D', (byte) 0xAE, 66, 96, (byte) 0x82
        };
        mockMvc.perform(multipart("/companies/10")
                        .file(new MockMultipartFile("logo", "logo.png", "image/png", logo))
                        .param("name", "Updated company")
                        .param("legalName", "Updated Company Ltd.")
                        .param("email", "updated@example.test")
                        .param("phone", "555-0199")
                        .param("address", "99 Main Street")
                        .param("city", "Montréal")
                        .param("province", "QC")
                        .param("postalCode", "H0H 0H0")
                        .param("country", "Canada"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/companies"));

        verify(companyService).updateCompany(eq(10L), any(Company.class),
                argThat(uploaded -> java.util.Arrays.equals(logo, uploaded)),
                eq("image/png"), eq(false));
    }

    private Company company(Long id) {
        Company company = new Company();
        company.setId(id);
        company.setName("Example company");
        company.setLegalName("Example Company Ltd.");
        company.setEmail("company@example.test");
        company.setPhone("555-0100");
        company.setAddress("99 Main Street");
        company.setCity("Montréal");
        company.setProvince("QC");
        company.setPostalCode("H0H 0H0");
        company.setCountry("Canada");
        return company;
    }
}
