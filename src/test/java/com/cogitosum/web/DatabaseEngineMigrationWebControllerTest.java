package com.cogitosum.web;

import com.cogitosum.service.DatabaseMigrationService;
import com.cogitosum.service.UserShortIdService;
import com.cogitosum.service.DatabaseMigrationService.MigrationException;
import com.cogitosum.service.DatabaseMigrationService.MysqlConnectionDetails;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(DatabaseEngineMigrationWebController.class)
@AutoConfigureMockMvc(addFilters = false)
class DatabaseEngineMigrationWebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DatabaseMigrationService migrationService;
    @MockitoBean
    private UserShortIdService shortIdService;

    @Test
    void rendersSqliteDestinationConfigurationForMysqlSource() throws Exception {
        when(migrationService.getSourceDatabaseType()).thenReturn("mysql");
        when(migrationService.getSourceMysqlConnectionDetails()).thenReturn(
                new MysqlConnectionDetails(
                        "jdbc:mysql://db.example.test:3306/accounting", "acct_admin", "super-secret"));
        Authentication admin = org.mockito.Mockito.mock(Authentication.class);
        when(admin.isAuthenticated()).thenReturn(true);
        doReturn(java.util.List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))).when(admin).getAuthorities();

        mockMvc.perform(get("/admin/database/migrate").principal(admin))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/database-migrate"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "/admin/database/migrate/sqlite")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "To migrate back to MySQL later")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "value=\"jdbc:mysql://db.example.test:3306/accounting\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"acct_admin\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "id=\"mysqlSourcePassword\" type=\"password\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("sqlitePath")));
    }

    @Test
    void rendersMysqlDestinationConfigurationForSqliteSource() throws Exception {
        when(migrationService.getSourceDatabaseType()).thenReturn("sqlite");
        Authentication admin = org.mockito.Mockito.mock(Authentication.class);
        when(admin.isAuthenticated()).thenReturn(true);
        doReturn(java.util.List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))).when(admin).getAuthorities();

        mockMvc.perform(get("/admin/database/migrate").principal(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "/admin/database/migrate/mysql")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("mysqlUrl")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("username")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("password")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Password")));
    }

    @Test
    void logsConfirmationRejectionsWithoutLoggingSubmittedValues() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(DatabaseEngineMigrationWebController.class);
        ListAppender<ILoggingEvent> appender = attachAppender(logger);
        try {
            when(shortIdService.getOrCreate("admin@example.test")).thenReturn("EXA001");
            mockMvc.perform(post("/admin/database/migrate/sqlite")
                            .principal(admin("admin@example.test"))
                            .param("confirmation", "not-MIGRATE")
                            .param("sqlitePath", "C:\\data\\sensitive.sqlite"))
                    .andExpect(status().is3xxRedirection());

            ILoggingEvent rejection = appender.list.stream()
                    .filter(event -> event.getFormattedMessage().contains("reason=confirmation-required"))
                    .findFirst()
                    .orElseThrow();
            org.junit.jupiter.api.Assertions.assertEquals("ERROR",
                    rejection.getMDCPropertyMap().get("logCategory"));
            org.junit.jupiter.api.Assertions.assertTrue(
                    rejection.getFormattedMessage().contains("actor=EXA001"));
            org.junit.jupiter.api.Assertions.assertFalse(
                    rejection.getFormattedMessage().contains("sensitive.sqlite"));
            org.junit.jupiter.api.Assertions.assertFalse(
                    rejection.getFormattedMessage().contains("not-MIGRATE"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void logsPredictableMysqlConfigurationRejectionsByReasonKey() throws Exception {
        when(migrationService.migrateToMysql(
                "jdbc:mysql://db.example.test:3306/accounting", "db_admin", "secret-value"))
                .thenThrow(new MigrationException("database.engineMigration.error.mysqlConfiguration"));
        Logger logger = (Logger) LoggerFactory.getLogger(DatabaseEngineMigrationWebController.class);
        ListAppender<ILoggingEvent> appender = attachAppender(logger);
        try {
            mockMvc.perform(post("/admin/database/migrate/mysql")
                            .principal(admin("admin@example.test"))
                            .param("confirmation", "MIGRATE")
                            .param("confirmReplacement", "true")
                            .param("mysqlUrl", "jdbc:mysql://db.example.test:3306/accounting")
                            .param("username", "db_admin")
                            .param("password", "secret-value"))
                    .andExpect(status().is3xxRedirection());

            ILoggingEvent rejection = appender.list.stream()
                    .filter(event -> event.getFormattedMessage()
                            .contains("database.engineMigration.error.mysqlConfiguration"))
                    .findFirst()
                    .orElseThrow();
            org.junit.jupiter.api.Assertions.assertEquals("ERROR",
                    rejection.getMDCPropertyMap().get("logCategory"));
            org.junit.jupiter.api.Assertions.assertTrue(
                    rejection.getFormattedMessage().contains("targetType=mysql"));
            org.junit.jupiter.api.Assertions.assertFalse(
                    rejection.getFormattedMessage().contains("secret-value"));
            org.junit.jupiter.api.Assertions.assertFalse(
                    rejection.getFormattedMessage().contains("db.example.test"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private ListAppender<ILoggingEvent> attachAppender(Logger logger) {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private Authentication admin(String username) {
        Authentication admin = org.mockito.Mockito.mock(Authentication.class);
        when(admin.isAuthenticated()).thenReturn(true);
        when(admin.getName()).thenReturn(username);
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))).when(admin).getAuthorities();
        return admin;
    }
}
