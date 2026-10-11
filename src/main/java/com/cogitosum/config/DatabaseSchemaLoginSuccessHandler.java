package com.cogitosum.config;

import com.cogitosum.service.AccountCredentialsService;
import com.cogitosum.service.DatabaseSchemaVersionService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class DatabaseSchemaLoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final AccountCredentialsService credentialsService;
    private final DatabaseSchemaVersionService schemaVersionService;

    public DatabaseSchemaLoginSuccessHandler(
            AccountCredentialsService credentialsService,
            DatabaseSchemaVersionService schemaVersionService) {
        this.credentialsService = credentialsService;
        this.schemaVersionService = schemaVersionService;
        setDefaultTargetUrl("/");
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        if (credentialsService.usesDefaultCredentials(authentication.getName())
                && !hasPromptDismissal(request, authentication.getName())) {
            response.sendRedirect(request.getContextPath() + "/account/credentials");
            return;
        }

        if (!schemaVersionService.getStatus().upToDate()) {
            response.sendRedirect(request.getContextPath() + "/database/schema");
            return;
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }

    private boolean hasPromptDismissal(HttpServletRequest request, String email) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        String expectedToken = credentialsService.promptDismissalToken(email);
        for (Cookie cookie : cookies) {
            if (AccountCredentialsService.PROMPT_DISMISSED_COOKIE.equals(cookie.getName())
                    && expectedToken.equals(cookie.getValue())) {
                return true;
            }
        }
        return false;
    }
}
