package com.cogitosum.web;

import com.cogitosum.service.AccountCredentialsService;
import com.cogitosum.service.DatabaseSchemaVersionService;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountCredentialsWebControllerTest {

    @Test
    void remembersPromptDismissalAndReturnsToDashboard() {
        AccountCredentialsService credentialsService = mock(AccountCredentialsService.class);
        DatabaseSchemaVersionService schemaVersionService = mock(DatabaseSchemaVersionService.class);
        MessageSource messageSource = mock(MessageSource.class);
        AccountCredentialsWebController controller = new AccountCredentialsWebController(
                credentialsService, schemaVersionService, messageSource);
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("owner@example.test");
        when(credentialsService.promptDismissalToken("owner@example.test")).thenReturn("opaque-token");
        when(schemaVersionService.getStatus()).thenReturn(new DatabaseSchemaVersionService.Status(
                "1.1.0", "1.1.0", List.of(), List.of(), true, true, null));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/account/credentials/continue");
        request.setSecure(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        String result = controller.continueAfterPrompt(authentication, request, response);

        assertEquals("redirect:/", result);
        String cookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertTrue(cookie.contains("credentialPromptDismissed=opaque-token"));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("Secure"));
        assertTrue(cookie.contains("SameSite=Lax"));
    }
}
