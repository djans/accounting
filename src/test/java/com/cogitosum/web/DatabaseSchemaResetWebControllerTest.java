package com.cogitosum.web;

import com.cogitosum.service.DatabaseSchemaResetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.MessageSource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.ui.ExtendedModelMap;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabaseSchemaResetWebControllerTest {

    @Mock private DatabaseSchemaResetService resetService;
    @Mock private MessageSource messageSource;
    @Mock private Authentication authentication;

    private DatabaseSchemaResetWebController controller;

    @BeforeEach
    void setUp() {
        controller = new DatabaseSchemaResetWebController(resetService, messageSource);
        when(authentication.isAuthenticated()).thenReturn(true);
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .when(authentication).getAuthorities();
    }

    @Test
    void pageIsAdminOnly() {
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.page(authentication, model);

        assertEquals("admin/database-recreate", view);
        assertEquals("schema-reset", model.get("active"));
        doReturn(List.of()).when(authentication).getAuthorities();
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.page(authentication, model));
    }

    @Test
    void resetRequiresTypedConfirmationAndCheckbox() {
        when(messageSource.getMessage(
                eq("admin.schemaReset.confirmationRequired"), isNull(), any(Locale.class)))
                .thenReturn("Confirmation required");
        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

        String view = controller.recreate("DROP", true, authentication, attributes);

        assertEquals("redirect:/admin/database/recreate", view);
        assertEquals("Confirmation required", attributes.getFlashAttributes().get("flashError"));
        controller.recreate("DROP AND RECREATE", false, authentication, attributes);
        verify(resetService, never()).dropAndRecreate();
    }

    @Test
    void resetDropsAndRecreatesSchemaOnlyAfterConfirmation() {
        when(authentication.getName()).thenReturn("admin@example.test");
        when(resetService.dropAndRecreate()).thenReturn(38);

        String view = controller.recreate(
                "DROP AND RECREATE", true, authentication, new RedirectAttributesModelMap());

        assertEquals("admin/database-recreated", view);
        verify(resetService).dropAndRecreate();
    }
}
