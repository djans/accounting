package com.cogitosum.service;

import com.cogitosum.entity.Company;
import com.cogitosum.entity.InvoiceTemplate;
import com.cogitosum.entity.UserRole;
import com.cogitosum.repository.InvoiceTemplateRepository;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceTemplateServiceTest {

    @Mock private InvoiceTemplateRepository templateRepository;
    @Mock private CurrentCompanyContext companyContext;

    private InvoiceTemplateService service;

    @BeforeEach
    void setUp() {
        service = new InvoiceTemplateService(templateRepository, companyContext);
    }

    @Test
    void storesValidDocxTemplatesWithinTheCurrentCompany() throws Exception {
        Company company = new Company();
        byte[] data = validDocx();
        when(companyContext.requireCurrentRole()).thenReturn(UserRole.ADMIN);
        when(companyContext.requireCompanyId()).thenReturn(7L);
        when(companyContext.requireCompany()).thenReturn(company);
        when(templateRepository.existsByCompanyIdAndLanguageTagAndNameIgnoreCase(
                7L, "fr-CA", "Facture")).thenReturn(false);

        service.uploadForCurrentCompany(" Facture ", "fr-CA", "facture.docx", data);

        ArgumentCaptor<InvoiceTemplate> template = ArgumentCaptor.forClass(InvoiceTemplate.class);
        verify(templateRepository).save(template.capture());
        assertEquals(company, template.getValue().getCompany());
        assertEquals("Facture", template.getValue().getName());
        assertEquals("fr-CA", template.getValue().getLanguageTag());
        org.junit.jupiter.api.Assertions.assertArrayEquals(data, template.getValue().getTemplateData());
    }

    @Test
    void rejectsNonDocxUploads() {
        when(companyContext.requireCurrentRole()).thenReturn(UserRole.ADMIN);

        assertThrows(IllegalArgumentException.class,
                () -> service.uploadForCurrentCompany("Invalid", "en", "invalid.docx", new byte[]{1, 2, 3}));
    }

    @Test
    void suggestsCustomTemplateMatchingTheSelectedLanguage() {
        List<InvoiceTemplateService.TemplateOption> options = List.of(
                new InvoiceTemplateService.TemplateOption("standard", "Standard", "en", "default.docx", true),
                new InvoiceTemplateService.TemplateOption("french", "Français", "fr-CA", "fr.docx", false));

        assertEquals("french", service.suggestedTemplateId(options, Locale.CANADA_FRENCH));
    }

    @Test
    void onlyLoadsTemplatesBelongingToTheInvoiceCompany() {
        when(templateRepository.findByIdAndCompanyId("template-id", 7L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.getTemplateForCompany(7L, "template-id"));
    }

    @Test
    void downloadsTemplatesOnlyFromTheCurrentCompanyForAdministrators() {
        byte[] data = {1, 2, 3};
        InvoiceTemplate template = new InvoiceTemplate();
        template.setTemplateData(data);
        template.setLanguageTag("fr-CA");
        template.setOriginalFilename("facture-fr.docx");
        when(companyContext.requireCurrentRole()).thenReturn(UserRole.ADMIN);
        when(companyContext.requireCompanyId()).thenReturn(7L);
        when(templateRepository.findByIdAndCompanyId("template-id", 7L)).thenReturn(Optional.of(template));

        InvoiceTemplateService.TemplateContent downloaded =
                service.getTemplateForCurrentCompany("template-id");

        org.junit.jupiter.api.Assertions.assertArrayEquals(data, downloaded.data());
        assertEquals("facture-fr.docx", downloaded.originalFilename());
        verify(templateRepository).findByIdAndCompanyId("template-id", 7L);
    }

    @Test
    void deniesTemplateDownloadToNonAdministrators() {
        when(companyContext.requireCurrentRole()).thenReturn(UserRole.ACCOUNTANT);

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.getTemplateForCurrentCompany("template-id"));
    }

    private byte[] validDocx() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("Invoice {{invoice.number}}");
            document.write(output);
        }
        return output.toByteArray();
    }
}
