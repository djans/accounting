package com.cogitosum.web;

import com.cogitosum.service.CompanyActivityResetService;
import com.cogitosum.repository.CompanyActivityRepository.ActivityCounts;
import com.cogitosum.repository.CompanyActivityRepository.BankReconciliationCounts;
import com.cogitosum.service.CurrentCompanyContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.MessageSource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyActivityResetWebControllerTest {

    @Mock private CompanyActivityResetService resetService;
    @Mock private CurrentCompanyContext companyContext;
    @Mock private MessageSource messageSource;
    @Mock private Authentication authentication;

    private CompanyActivityResetWebController controller;

    @BeforeEach
    void setUp() {
        controller = new CompanyActivityResetWebController(resetService, companyContext, messageSource);
        when(authentication.isAuthenticated()).thenReturn(true);
        lenient().doReturn(List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .when(authentication).getAuthorities();
    }

    @Test
    void resetRequiresTypedConfirmationAndCheckbox() {
        when(messageSource.getMessage(
                eq("admin.reset.confirmationRequired"), isNull(), any(Locale.class)))
                .thenReturn("Confirmation required");
        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

        String view = controller.reset("reset", true, authentication, attributes);

        assertEquals("redirect:/admin/reset", view);
        assertEquals("Confirmation required", attributes.getFlashAttributes().get("flashError"));
        controller.reset("RESET", false, authentication, attributes);
        assertEquals("Confirmation required", attributes.getFlashAttributes().get("flashError"));
        verify(resetService, never()).resetCurrentCompany();
    }

    @Test
    void resetRunsForTheAdministratorAndReportsDeletedCounts() {
        ActivityCounts counts = new ActivityCounts(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
        when(resetService.resetCurrentCompany()).thenReturn(counts);
        when(companyContext.requireCompanyId()).thenReturn(27L);
        when(messageSource.getMessage(eq("admin.reset.success"), any(Object[].class), any(Locale.class)))
                .thenReturn("Reset complete");
        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

        String view = controller.reset("RESET", true, authentication, attributes);

        assertEquals("redirect:/admin/reset", view);
        assertEquals("Reset complete", attributes.getFlashAttributes().get("flashSuccess"));
        verify(resetService).resetCurrentCompany();
    }

    @Test
    void resetRequiresAdministratorRole() {
        doReturn(List.of()).when(authentication).getAuthorities();

        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.reset("RESET", true, authentication, new RedirectAttributesModelMap()));
    }

    @Test
    void bankResetRequiresTypedConfirmationAndCheckbox() {
        when(messageSource.getMessage(
                eq("admin.reset.bankConfirmationRequired"), isNull(), any(Locale.class)))
                .thenReturn("Bank reset confirmation required");
        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

        String view = controller.resetBankReconciliation("RESET BANK", false, authentication, attributes);

        assertEquals("redirect:/admin/reset", view);
        assertEquals("Bank reset confirmation required",
                attributes.getFlashAttributes().get("flashError"));
        verify(resetService, never()).resetBankReconciliationForCurrentCompany();
    }

    @Test
    void bankResetRunsForTheAdministratorAndReportsDeletedHistory() {
        when(resetService.resetBankReconciliationForCurrentCompany())
                .thenReturn(new BankReconciliationCounts(5, 2));
        when(companyContext.requireCompanyId()).thenReturn(27L);
        when(messageSource.getMessage(
                eq("admin.reset.bankSuccess"), any(Object[].class), any(Locale.class)))
                .thenReturn("Bank data reset");
        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

        String view = controller.resetBankReconciliation("RESET BANK", true, authentication, attributes);

        assertEquals("redirect:/admin/reset", view);
        assertEquals("Bank data reset", attributes.getFlashAttributes().get("flashSuccess"));
        verify(resetService).resetBankReconciliationForCurrentCompany();
    }
}
