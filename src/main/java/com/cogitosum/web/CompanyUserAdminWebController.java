package com.cogitosum.web;

import com.cogitosum.config.LoggingCategory;
import com.cogitosum.service.CurrentCompanyContext;
import com.cogitosum.service.UserAdministrationException;
import com.cogitosum.service.UserAccountService;
import com.cogitosum.service.UserShortIdService;
import com.cogitosum.entity.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Locale;

@Controller
@RequestMapping("/admin/users")
public class CompanyUserAdminWebController {

    private static final Logger log = LoggerFactory.getLogger(CompanyUserAdminWebController.class);

    private final UserAccountService userAccountService;
    private final CurrentCompanyContext companyContext;
    private final MessageSource messageSource;
    private final UserShortIdService shortIdService;

    public CompanyUserAdminWebController(
            UserAccountService userAccountService,
            CurrentCompanyContext companyContext,
            MessageSource messageSource,
            UserShortIdService shortIdService) {
        this.userAccountService = userAccountService;
        this.companyContext = companyContext;
        this.messageSource = messageSource;
        this.shortIdService = shortIdService;
    }

    @GetMapping
    public String page(Model model) {
        model.addAttribute("users", userAccountService.getCurrentCompanyUsers());
        model.addAttribute("company", companyContext.requireCompany());
        model.addAttribute("currentUserId", userAccountService.getCurrentUserId());
        model.addAttribute("minimumPasswordLength", 12);
        model.addAttribute("roles", userAccountService.getAvailableRoles().stream()
            .map(role -> new RoleOption(role, roleMessageKey(role)))
            .toList());
        model.addAttribute("active", "company-users");
        return "admin/users";
    }

    @PostMapping("/create")
    public String create(@RequestParam String fullName,
                         @RequestParam String email,
                         @RequestParam String password,
                         @RequestParam String confirmPassword,
                         @RequestParam UserRole role,
                         Authentication authentication,
                         RedirectAttributes attributes) {
        if (!password.equals(confirmPassword)) {
            return reject("admin.users.error.passwordMismatch", authentication, attributes);
        }
        try {
            var user = userAccountService.createAccount(fullName, email, password, role);
            logUserActivity("Company user account created: actor={} target={} company={}",
                    actor(authentication), shortIdService.getOrCreate(user.getEmail()),
                    companyContext.requireCompanyId());
            attributes.addFlashAttribute("flashSuccess",
                    message("admin.users.created", roleLabel(role)));
        } catch (UserAdministrationException exception) {
            return reject(exception.getMessage(), authentication, attributes);
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/grant")
    public String grant(@RequestParam String email,
                        @RequestParam UserRole role,
                        Authentication authentication,
                        RedirectAttributes attributes) {
        try {
            userAccountService.grantAccess(email, role);
            logUserActivity("Company access granted: actor={} target={} company={}",
                    actor(authentication), shortIdService.getOrCreate(email.trim()),
                    companyContext.requireCompanyId());
            attributes.addFlashAttribute("flashSuccess", message("admin.users.granted", roleLabel(role)));
        } catch (UserAdministrationException exception) {
            return reject(exception.getMessage(), authentication, attributes);
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/{userId}/role")
    public String updateRole(@PathVariable Long userId,
                             @RequestParam UserRole role,
                             Authentication authentication,
                             RedirectAttributes attributes) {
        try {
            userAccountService.updateRole(userId, role);
            logUserActivity("Company user role changed: actor={} targetUserId={} role={} company={}",
                    actor(authentication), userId, role, companyContext.requireCompanyId());
            attributes.addFlashAttribute("flashSuccess", message("admin.users.roleUpdated"));
        } catch (UserAdministrationException exception) {
            return reject(exception.getMessage(), authentication, attributes);
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/{userId}/revoke")
    public String revoke(@PathVariable Long userId,
                         Authentication authentication,
                         RedirectAttributes attributes) {
        try {
            userAccountService.revokeAccess(userId);
            logUserActivity("Company access revoked: actor={} targetUserId={} company={}",
                    actor(authentication), userId, companyContext.requireCompanyId());
            attributes.addFlashAttribute("flashSuccess", message("admin.users.revoked"));
        } catch (UserAdministrationException exception) {
            return reject(exception.getMessage(), authentication, attributes);
        }
        return "redirect:/admin/users";
    }

    private String reject(String messageCode, Authentication authentication, RedirectAttributes attributes) {
        String safeCode = messageCode == null ? "admin.users.error.requestRejected" : messageCode;
        try (LoggingCategory.Scope ignored = LoggingCategory.open(LoggingCategory.ERROR)) {
            log.warn("Company user administration request rejected: actor={} company={} reason={}",
                    actor(authentication), companyContext.requireCompanyId(), safeCode);
        }
        attributes.addFlashAttribute("flashError", message(safeCode));
        return "redirect:/admin/users";
    }

    private String message(String code) {
        return message(code, new Object[0]);
    }

    private String message(String code, Object... arguments) {
        Locale locale = LocaleContextHolder.getLocale();
        return messageSource.getMessage(code, arguments, code, locale);
    }

    private String roleLabel(UserRole role) {
        return message(roleMessageKey(role));
    }

    private String roleMessageKey(UserRole role) {
        return switch (role) {
            case ADMIN -> "admin.users.roleAdmin";
            case ACCOUNTANT -> "admin.users.roleAccountant";
            case BOOKKEEPER -> "admin.users.roleBookkeeper";
            case PAYMENT_OPERATOR -> "admin.users.rolePaymentOperator";
            case AUDITOR -> "admin.users.roleAuditor";
            case VIEWER -> "admin.users.roleAuditor";
        };
    }

    private void logUserActivity(String pattern, Object... arguments) {
        try (LoggingCategory.Scope ignored = LoggingCategory.open(LoggingCategory.USER_ACTIVITY)) {
            log.info(pattern, arguments);
        }
    }

    private String actor(Authentication authentication) {
        return authentication == null ? "unknown" : shortIdService.getOrCreate(authentication.getName());
    }

    public record RoleOption(UserRole role, String messageKey) {
    }
}
