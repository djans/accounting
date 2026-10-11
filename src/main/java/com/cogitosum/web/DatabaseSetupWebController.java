package com.cogitosum.web;

import com.cogitosum.setup.DatabaseSetupException;
import com.cogitosum.setup.DatabaseSetupService;
import org.springframework.stereotype.Controller;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Controller
@ConditionalOnProperty(name = "app.bootstrap.mode", havingValue = "database-setup")
public class DatabaseSetupWebController {

    private final DatabaseSetupService setupService;
    private final String setupToken;

    public DatabaseSetupWebController(DatabaseSetupService setupService,
                                      @Value("${app.setup.token:}") String setupToken) {
        this.setupService = setupService;
        this.setupToken = setupToken;
    }

    @GetMapping({"/", "/setup"})
    public String setup(Model model) {
        return showSetup(model, "mysql", "jdbc:mysql://localhost:3306/accounting",
                "mysql", Path.of(System.getProperty("user.home"), ".accounting", "accounting.sqlite")
                        .toString(), null);
    }

    @PostMapping("/setup")
    public String save(@RequestParam(required = false) String databaseType,
                       @RequestParam(required = false) String mysqlUrl,
                       @RequestParam(required = false) String username,
                       @RequestParam(required = false) String password,
                       @RequestParam(required = false) String sqlitePath,
                       @RequestParam(required = false) String setupToken,
                       Model model) {
        if (!isAuthorized(setupToken)) {
            return showSetup(model, databaseType, mysqlUrl, username, sqlitePath,
                    this.setupToken.isBlank()
                            ? "database.setup.error.invalid"
                            : "database.setup.error.token");
        }
        try {
            setupService.configure(databaseType, mysqlUrl, username, password, sqlitePath);
            return "redirect:/setup/complete";
        } catch (IllegalArgumentException exception) {
            return showSetup(model, databaseType, mysqlUrl, username, sqlitePath,
                    "database.setup.error.invalid");
        } catch (DatabaseSetupException exception) {
            return showSetup(model, databaseType, mysqlUrl, username, sqlitePath, exception.messageKey());
        }
    }

    @GetMapping("/setup/complete")
    public String complete() {
        return "setup/database-complete";
    }

    private String showSetup(Model model, String databaseType, String mysqlUrl,
                             String username, String sqlitePath, String errorKey) {
        model.addAttribute("databaseType", databaseType == null ? "mysql" : databaseType);
        model.addAttribute("mysqlUrl", mysqlUrl == null ? "" : mysqlUrl);
        model.addAttribute("username", username == null ? "" : username);
        model.addAttribute("sqlitePath", sqlitePath == null ? "" : sqlitePath);
        model.addAttribute("setupErrorKey", errorKey);
        model.addAttribute("requiresSetupToken", !setupToken.isBlank());
        return "setup/database";
    }

    private boolean isAuthorized(String suppliedToken) {
        if (setupToken.isBlank()) {
            return true;
        }
        if (suppliedToken == null) {
            return false;
        }
        return MessageDigest.isEqual(setupToken.getBytes(StandardCharsets.UTF_8),
                suppliedToken.getBytes(StandardCharsets.UTF_8));
    }
}
