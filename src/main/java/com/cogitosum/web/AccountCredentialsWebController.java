package com.cogitosum.web;

import com.cogitosum.service.AccountCredentialsService;
import com.cogitosum.service.AccountCredentialsService.UpdateResult;
import com.cogitosum.service.DatabaseSchemaVersionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/account/credentials")
public class AccountCredentialsWebController {

    private final AccountCredentialsService credentialsService;
    private final DatabaseSchemaVersionService schemaVersionService;
    private final MessageSource messageSource;

    public AccountCredentialsWebController(
            AccountCredentialsService credentialsService,
            DatabaseSchemaVersionService schemaVersionService,
            MessageSource messageSource) {
        this.credentialsService = credentialsService;
        this.schemaVersionService = schemaVersionService;
        this.messageSource = messageSource;
    }

    @GetMapping
    public String page(Authentication authentication, Model model) {
        requireAuthenticated(authentication);
        model.addAttribute("currentEmail", authentication.getName());
        model.addAttribute("defaultCredentials", credentialsService.usesDefaultCredentials(authentication.getName()));
        model.addAttribute("schemaNeedsAttention", !schemaVersionService.getStatus().upToDate());
        model.addAttribute("active", "credentials");
        return "account/credentials";
    }

    @PostMapping
    public String update(@RequestParam String email,
                         @RequestParam(defaultValue = "") String newPassword,
                         @RequestParam(defaultValue = "") String confirmPassword,
                         Authentication authentication,
                         HttpServletRequest request,
                         HttpServletResponse response,
                         RedirectAttributes attributes) {
        requireAuthenticated(authentication);
        if (!newPassword.equals(confirmPassword)) {
            attributes.addFlashAttribute("flashError", message("account.credentials.passwordMismatch"));
            attributes.addFlashAttribute("requestedEmail", email);
            return "redirect:/account/credentials";
        }

        UpdateResult result = credentialsService.update(authentication.getName(), email, newPassword);
        if (result != UpdateResult.SUCCESS) {
            attributes.addFlashAttribute("flashError", message(switch (result) {
                case ACCOUNT_NOT_FOUND -> "account.credentials.accountNotFound";
                case INVALID_EMAIL -> "account.credentials.invalidEmail";
                case EMAIL_IN_USE -> "account.credentials.emailInUse";
                case PASSWORD_TOO_SHORT -> "account.credentials.passwordTooShort";
                case DEFAULT_CREDENTIALS_MUST_CHANGE -> "account.credentials.defaultMustChange";
                case SUCCESS -> throw new IllegalStateException("Unexpected successful update result.");
            }));
            attributes.addFlashAttribute("requestedEmail", email);
            return "redirect:/account/credentials";
        }

        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return "redirect:/login?credentialsUpdated";
    }

    @GetMapping("/continue")
    public String continueAfterPrompt(Authentication authentication) {
        requireAuthenticated(authentication);
        return schemaVersionService.getStatus().upToDate()
                ? "redirect:/"
                : "redirect:/database/schema";
    }

    private String message(String key) {
        return messageSource.getMessage(key, null, LocaleContextHolder.getLocale());
    }

    private void requireAuthenticated(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
    }
}
