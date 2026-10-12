package com.cogitosum.web;

import com.cogitosum.config.LoggingCategory;
import com.cogitosum.service.DatabaseMigrationService;
import com.cogitosum.service.DatabaseMigrationService.MigrationException;
import com.cogitosum.service.DatabaseMigrationService.MigrationResult;
import com.cogitosum.service.UserShortIdService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/database/migrate")
public class DatabaseEngineMigrationWebController {

    private static final Logger log = LoggerFactory.getLogger(DatabaseEngineMigrationWebController.class);
    private static final String CONFIRMATION = "MIGRATE";

    private final DatabaseMigrationService migrationService;
    private final MessageSource messageSource;
    private final UserShortIdService shortIdService;

    public DatabaseEngineMigrationWebController(
            DatabaseMigrationService migrationService,
            MessageSource messageSource,
            UserShortIdService shortIdService) {
        this.migrationService = migrationService;
        this.messageSource = messageSource;
        this.shortIdService = shortIdService;
    }

    @GetMapping
    public String page(Authentication authentication, Model model) {
        requireAdmin(authentication);
        model.addAttribute("active", "database-engine-migration");
        try {
            String sourceDatabaseType = migrationService.getSourceDatabaseType();
            model.addAttribute("sourceDatabaseType", sourceDatabaseType);
            if ("mysql".equals(sourceDatabaseType)) {
                DatabaseMigrationService.MysqlConnectionDetails details =
                        migrationService.getSourceMysqlConnectionDetails();
                model.addAttribute("mysqlSourceUrl", details.jdbcUrl());
                model.addAttribute("mysqlSourceUsername", details.username());
                model.addAttribute("mysqlSourcePassword", details.password());
            }
        } catch (MigrationException exception) {
            model.addAttribute("migrationPageError", message(exception.messageKey()));
        }
        return "admin/database-migrate";
    }

    @PostMapping("/mysql")
    public String migrateToMysql(@RequestParam(required = false) String mysqlUrl,
                                 @RequestParam(required = false) String username,
                                 @RequestParam(required = false) String password,
                                 @RequestParam(required = false) String confirmation,
                                 @RequestParam(defaultValue = "false") boolean confirmReplacement,
                                 Authentication authentication,
                                 RedirectAttributes attributes) {
        requireAdmin(authentication);
        if (!confirmed(confirmation, confirmReplacement)) {
            logRejection(authentication, "mysql", "confirmation-required");
            attributes.addFlashAttribute("flashError", message("database.engineMigration.error.confirmation"));
            return "redirect:/admin/database/migrate";
        }
        try {
            MigrationResult result = migrationService.migrateToMysql(mysqlUrl, username, password);
            return finish(result, attributes);
        } catch (MigrationException exception) {
            logRejection(authentication, "mysql", exception.messageKey());
            attributes.addFlashAttribute("flashError", message(exception.messageKey()));
            return "redirect:/admin/database/migrate";
        }
    }

    @PostMapping("/sqlite")
    public String migrateToSqlite(@RequestParam(required = false) String sqlitePath,
                                  @RequestParam(required = false) String confirmation,
                                  @RequestParam(defaultValue = "false") boolean confirmReplacement,
                                  Authentication authentication,
                                  RedirectAttributes attributes) {
        requireAdmin(authentication);
        if (!confirmed(confirmation, confirmReplacement)) {
            logRejection(authentication, "sqlite", "confirmation-required");
            attributes.addFlashAttribute("flashError", message("database.engineMigration.error.confirmation"));
            return "redirect:/admin/database/migrate";
        }
        try {
            MigrationResult result = migrationService.migrateToSqlite(sqlitePath);
            return finish(result, attributes);
        } catch (MigrationException exception) {
            logRejection(authentication, "sqlite", exception.messageKey());
            attributes.addFlashAttribute("flashError", message(exception.messageKey()));
            return "redirect:/admin/database/migrate";
        }
    }

    @GetMapping("/complete")
    public String complete(Authentication authentication, Model model) {
        requireAdmin(authentication);
        model.addAttribute("active", "database-engine-migration");
        if (!model.containsAttribute("migrationRows")) {
            return "redirect:/admin/database/migrate";
        }
        return "admin/database-migrate-complete";
    }

    private String finish(MigrationResult result, RedirectAttributes attributes) {
        attributes.addFlashAttribute("migrationRows", result.importedRows());
        attributes.addFlashAttribute("migrationTargetType", result.targetType());
        attributes.addFlashAttribute("flashSuccess", message("database.engineMigration.success",
                result.importedRows(), databaseTypeLabel(result.targetType())));
        return "redirect:/admin/database/migrate/complete";
    }

    private boolean confirmed(String confirmation, boolean confirmReplacement) {
        return CONFIRMATION.equals(confirmation) && confirmReplacement;
    }

    private void logRejection(Authentication authentication, String targetType, String reason) {
        try (LoggingCategory.Scope ignored = LoggingCategory.open(LoggingCategory.ERROR)) {
            log.warn("Database migration rejected: actor={} targetType={} reason={}",
                    shortIdService.getOrCreate(authentication.getName()), targetType, reason);
        }
    }

    private String databaseTypeLabel(String databaseType) {
        return message("mysql".equals(databaseType)
                ? "database.engineMigration.mysql"
                : "database.engineMigration.sqlite");
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
