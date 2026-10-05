package com.cogitosum.config;

import com.cogitosum.service.AccountCredentialsService;
import com.cogitosum.service.DatabaseSchemaVersionService;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabaseSchemaLoginSuccessHandlerTest {

    @Mock private AccountCredentialsService credentialsService;
    @Mock private DatabaseSchemaVersionService schemaVersionService;
    @Mock private Authentication authentication;

    private DatabaseSchemaLoginSuccessHandler handler;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        handler = new DatabaseSchemaLoginSuccessHandler(credentialsService, schemaVersionService);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        when(authentication.getName()).thenReturn("admin@example.test");
    }

    @Test
    void promptsToChangeInitialCredentialsBeforeTheSchemaUpdate() throws IOException, ServletException {
        when(credentialsService.usesDefaultCredentials("admin@example.test")).thenReturn(true);

        handler.onAuthenticationSuccess(request, response, authentication);

        assertEquals("/account/credentials", response.getRedirectedUrl());
        verify(schemaVersionService, never()).getStatus();
    }

    @Test
    void redirectsToSchemaVersionsWhenAnUpdateIsPending() throws IOException, ServletException {
        when(credentialsService.usesDefaultCredentials("admin@example.test")).thenReturn(false);
        when(schemaVersionService.getStatus()).thenReturn(status(false));

        handler.onAuthenticationSuccess(request, response, authentication);

        assertEquals("/database/schema", response.getRedirectedUrl());
    }

    @Test
    void usesDefaultTargetWhenTheSchemaIsCurrent() throws IOException, ServletException {
        when(credentialsService.usesDefaultCredentials("admin@example.test")).thenReturn(false);
        when(schemaVersionService.getStatus()).thenReturn(status(true));

        handler.onAuthenticationSuccess(request, response, authentication);

        assertEquals("/", response.getRedirectedUrl());
    }

    private DatabaseSchemaVersionService.Status status(boolean upToDate) {
        return new DatabaseSchemaVersionService.Status("1.0.0", "1.1.0", List.of(), List.of(),
                true, upToDate, null);
    }
}
