package com.cogitosum.web;

import com.cogitosum.service.DatabaseSchemaVersionService;
import com.cogitosum.service.DatabaseSchemaVersionService.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

@Controller
@RequestMapping("/database/schema")
public class DatabaseSchemaVersionWebController {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSchemaVersionWebController.class);

    private final DatabaseSchemaVersionService schemaVersionService;
    private final MessageSource messageSource;

    public DatabaseSchemaVersionWebController(
            DatabaseSchemaVersionService schemaVersionService,
            MessageSource messageSource) {
        this.schemaVersionService = schemaVersionService;
        this.messageSource = messageSource;
    }

    @GetMapping
    public String page(Authentication authentication, Model model) {
        requireAuthenticated(authentication);
        Status status = schemaVersionService.getStatus();
        model.addAttribute("schemaStatus", status);
        model.addAttribute("schemaVersions", status.versions().stream()
                .map(version -> new LocalizedVersion(
                        version.version(),
                        message(version.titleKey()),
                        message(version.descriptionKey()),
                        version.current(),
                        version.applied(),
                        version.requiresBackup()))
                .toList());
        model.addAttribute("isAdmin", isAdmin(authentication));
        model.addAttribute("active", "schema-version");
        return "database/schema-version";
    }

    @PostMapping("/apply")
    public String apply(@RequestParam(defaultValue = "false") boolean backupConfirmed,
                        Authentication authentication,
                        RedirectAttributes attributes) {
        requireAdmin(authentication);
        Status status = schemaVersionService.getStatus();
        if (!status.migrationPossible() || !status.updateAvailable()) {
            attributes.addFlashAttribute("flashError",
                    message("database.schema.migrationUnavailable"));
            return "redirect:/database/schema";
        }
        if (status.requiresBackup() && !backupConfirmed) {
            attributes.addFlashAttribute("flashError",
                    message("database.schema.backupConfirmationRequired"));
            return "redirect:/database/schema";
        }

        try {
            Status updated = schemaVersionService.applyPendingMigrations();
            attributes.addFlashAttribute("flashSuccess",
                    message("database.schema.updated", updated.currentVersion()));
        } catch (DataAccessException | IllegalStateException exception) {
            log.error("Database schema migration failed for actor={} at version={}",
                    authentication.getName(), status.currentVersion(), exception);
            attributes.addFlashAttribute("flashError",
                    message("database.schema.migrationFailed"));
        }
        return "redirect:/database/schema";
    }

    private String message(String key, Object... arguments) {
        return messageSource.getMessage(key, arguments, LocaleContextHolder.getLocale());
    }

    public record LocalizedVersion(
            String version,
            String title,
            String description,
            boolean current,
            boolean applied,
            boolean requiresBackup) {
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private void requireAuthenticated(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
    }

    private void requireAdmin(Authentication authentication) {
        if (!isAdmin(authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator role required");
        }
    }
}
