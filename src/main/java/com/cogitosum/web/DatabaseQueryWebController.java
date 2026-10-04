package com.cogitosum.web;

import com.cogitosum.service.DatabaseQueryService;
import com.cogitosum.service.DatabaseQueryService.ConfirmationRequiredException;
import com.cogitosum.service.DatabaseQueryService.QueryResult;
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

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Controller
@RequestMapping("/database/query")
public class DatabaseQueryWebController {

    private static final Logger log = LoggerFactory.getLogger(DatabaseQueryWebController.class);

    private final DatabaseQueryService queryService;
    private final MessageSource messageSource;

    public DatabaseQueryWebController(DatabaseQueryService queryService, MessageSource messageSource) {
        this.queryService = queryService;
        this.messageSource = messageSource;
    }

    @GetMapping
    public String page(@RequestParam(required = false) String table,
                       Authentication authentication, Model model) throws SQLException {
        requireAdmin(authentication);
        List<String> tables = queryService.getApplicationTables();
        String selectedTable = table != null && tables.contains(table)
                ? table
                : tables.stream().findFirst().orElse("");
        populatePage(model, tables, selectedTable,
                selectedTable.isEmpty() ? "" : queryService.defaultStatement(selectedTable));
        return "database/query";
    }

    @PostMapping
    public String execute(@RequestParam String table,
                          @RequestParam String sql,
                          @RequestParam(defaultValue = "false") boolean confirmChanges,
                          Authentication authentication, Model model) throws SQLException {
        requireAdmin(authentication);
        List<String> tables = queryService.getApplicationTables();
        populatePage(model, tables, table, sql);
        model.addAttribute("requiresConfirmation", queryService.requiresConfirmation(sql));
        long startedAt = System.nanoTime();
        String actor = authentication.getName();
        String command = queryService.statementType(sql);

        try {
            QueryResult result = queryService.execute(table, sql, confirmChanges);
            model.addAttribute("queryResult", result);
            logExecution(actor, table, command, sql, result, elapsedMillis(startedAt));
        } catch (ConfirmationRequiredException ex) {
            log.warn("Administrative SQL rejected: user={} table={} command={} durationMs={} "
                            + "reason=confirmation-required statement={}",
                    actor, table, command, elapsedMillis(startedAt), auditText(sql));
            model.addAttribute("flashError", message(ex.getMessage()));
            model.addAttribute("requiresConfirmation", true);
        } catch (IllegalArgumentException ex) {
            log.warn("Administrative SQL rejected: user={} table={} command={} durationMs={} "
                            + "reason={} statement={}",
                    actor, table, command, elapsedMillis(startedAt), ex.getMessage(), auditText(sql));
            model.addAttribute("flashError", message(ex.getMessage()));
        } catch (SQLException ex) {
            log.error("Administrative SQL failed: user={} table={} command={} durationMs={} sqlState={} "
                            + "vendorCode={} error={} statement={}",
                    actor, table, command, elapsedMillis(startedAt), ex.getSQLState(), ex.getErrorCode(),
                    auditText(ex.getMessage()), auditText(sql));
            model.addAttribute("flashError",
                    message("database.query.error.execute", ex.getMessage()));
        }
        return "database/query";
    }

    private void populatePage(Model model, List<String> tables, String selectedTable, String sql) {
        model.addAttribute("tables", tables);
        model.addAttribute("selectedTable", selectedTable);
        model.addAttribute("sql", sql);
        model.addAttribute("requiresConfirmation", queryService.requiresConfirmation(sql));
        model.addAttribute("active", "database-query");
    }

    private void requireAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator role required");
        }
    }

    private String message(String code, Object... arguments) {
        return messageSource.getMessage(code, arguments, code, LocaleContextHolder.getLocale());
    }

    private void logExecution(String actor, String table, String command, String sql,
                              QueryResult result, long durationMillis) {
        String outcome = result.resultSet()
                ? "returnedRows=" + result.rows().size()
                    + ", returnedColumns=" + result.columns().size()
                    + ", rowsTruncated=" + result.rowsTruncated()
                    + ", columnsTruncated=" + result.columnsTruncated()
                : "affectedRows=" + (result.affectedRows() >= 0
                    ? Long.toString(result.affectedRows())
                    : "not-reported");
        if (queryService.requiresConfirmation(sql)) {
            log.warn("Administrative SQL completed: user={} table={} command={} durationMs={} "
                            + "outcome={} statement={}",
                    actor, table, command, durationMillis, outcome, auditText(sql));
        } else {
            log.info("Administrative SQL completed: user={} table={} command={} durationMs={} "
                            + "outcome={} statement={}",
                    actor, table, command, durationMillis, outcome, auditText(sql));
        }
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private String auditText(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder singleLine = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '\\') {
                singleLine.append("\\\\");
            } else if (Character.isISOControl(character)) {
                singleLine.append(String.format("\\u%04x", (int) character));
            } else {
                singleLine.append(character);
            }
        }
        return singleLine.toString();
    }
}
