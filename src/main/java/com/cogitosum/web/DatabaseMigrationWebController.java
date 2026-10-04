package com.cogitosum.web;

import com.cogitosum.migration.v1.InitialDataMigrationService;
import com.cogitosum.migration.v1.MigrationImportSummary;
import com.cogitosum.migration.v1.MigrationPreview;
import com.cogitosum.migration.v1.MigrationValidationError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.List;

@Controller
@RequestMapping("/database/migration")
public class DatabaseMigrationWebController {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMigrationWebController.class);

    private final InitialDataMigrationService migrationService;
    private final MessageSource messageSource;

    public DatabaseMigrationWebController(InitialDataMigrationService migrationService, MessageSource messageSource) {
        this.migrationService = migrationService;
        this.messageSource = messageSource;
    }

    @GetMapping
    public String page(Model model, Authentication authentication) {
        requireAdmin(authentication);
        populatePage(model);
        return "database/migration";
    }

    @GetMapping("/documentation")
    public String documentation(Model model, Authentication authentication) {
        requireAdmin(authentication);
        populatePage(model);
        return "database/migration-documentation";
    }

    @PostMapping("/validate")
    public String validate(@RequestParam("file") MultipartFile file, Model model, Authentication authentication) {
        requireAdmin(authentication);
        populatePage(model);
        try {
            MigrationPreview preview = migrationService.preview(file.getBytes(), authentication.getName());
            model.addAttribute("preview", preview);
        } catch (Exception ex) {
            log.warn("Migration package validation failed before parsing", ex);
            model.addAttribute("preview", new MigrationPreview(null, java.util.Map.of(),
                List.of(new MigrationValidationError("manifest.json", 0,
                    message("database.migration.error.unreadable")))));
        }
        return "database/migration";
    }

    @PostMapping("/import")
    public String importValidated(@RequestParam("confirmationToken") String confirmationToken,
                                  @RequestParam(value = "confirmation", required = false) String confirmation,
                                  Authentication authentication, RedirectAttributes attributes) {
        requireAdmin(authentication);
        if (!"IMPORT".equals(confirmation)) {
            attributes.addFlashAttribute("flashError", message("database.migration.error.confirmation"));
            return "redirect:/database/migration";
        }
        try {
            MigrationImportSummary result = migrationService.confirmAndImport(confirmationToken, authentication.getName());
            attributes.addFlashAttribute("flashSuccess",
                message("database.migration.success", result.totalRows()));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            attributes.addFlashAttribute("flashError", ex.getMessage());
        } catch (Exception ex) {
            log.error("Confirmed migration import failed", ex);
            attributes.addFlashAttribute("flashError", message("database.migration.error.failed"));
        }
        return "redirect:/database/migration";
    }

    private void populatePage(Model model) {
        model.addAttribute("active", "migration");
    }

    private String message(String code, Object... arguments) {
        return messageSource.getMessage(code, arguments, LocaleContextHolder.getLocale());
    }

    private void requireAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator role required");
        }
    }
}
