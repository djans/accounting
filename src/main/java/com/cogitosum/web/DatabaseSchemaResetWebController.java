package com.cogitosum.web;

import com.cogitosum.service.DatabaseSchemaResetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/database/recreate")
public class DatabaseSchemaResetWebController {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSchemaResetWebController.class);
    private static final String CONFIRMATION = "DROP AND RECREATE";

    private final DatabaseSchemaResetService resetService;
    private final MessageSource messageSource;

    public DatabaseSchemaResetWebController(
            DatabaseSchemaResetService resetService,
            MessageSource messageSource) {
        this.resetService = resetService;
        this.messageSource = messageSource;
    }

    @GetMapping
    public String page(Authentication authentication, Model model) {
        requireAdmin(authentication);
        model.addAttribute("active", "schema-reset");
        return "admin/database-recreate";
    }

    @PostMapping
    public String recreate(@RequestParam String confirmation,
                           @RequestParam(defaultValue = "false") boolean confirmReset,
                           Authentication authentication,
                           RedirectAttributes attributes) {
        requireAdmin(authentication);
        if (!CONFIRMATION.equals(confirmation) || !confirmReset) {
            attributes.addFlashAttribute("flashError",
                    messageSource.getMessage("admin.schemaReset.confirmationRequired",
                            null, LocaleContextHolder.getLocale()));
            return "redirect:/admin/database/recreate";
        }

        int droppedTables = resetService.dropAndRecreate();
        log.warn("Application database schema recreated: actor={} droppedTables={}",
                authentication.getName(), droppedTables);
        return "admin/database-recreated";
    }

    private void requireAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator role required");
        }
    }
}
