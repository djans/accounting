package com.cogitosum.web;

import com.cogitosum.service.DatabaseSchemaVersionService;
import com.cogitosum.service.DatabaseSchemaVersionService.SchemaMigration;
import com.cogitosum.service.DatabaseSchemaVersionService.Status;
import com.cogitosum.service.DatabaseSchemaVersionService.VersionEntry;
import com.cogitosum.service.SchemaVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabaseSchemaVersionWebControllerTest {

    @Mock private DatabaseSchemaVersionService schemaVersionService;
    @Mock private MessageSource messageSource;
    @Mock private Authentication authentication;

    private DatabaseSchemaVersionWebController controller;

    @BeforeEach
    void setUp() {
        controller = new DatabaseSchemaVersionWebController(schemaVersionService, messageSource);
        when(authentication.isAuthenticated()).thenReturn(true);
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .when(authentication).getAuthorities();
    }

    @Test
    void listsAllKnownVersionsForTheDatabasePage() {
        when(authentication.getName()).thenReturn("admin@example.test");
        when(schemaVersionService.getStatus()).thenReturn(status(false, false));
        when(messageSource.getMessage(any(), any(Object[].class), any(Locale.class))).thenAnswer(invocation ->
                invocation.getArgument(0));
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.page(authentication, model);

        assertEquals("database/schema-version", view);
        assertEquals("1.0.0", ((Status) model.get("schemaStatus")).currentVersion());
        assertEquals(2, ((List<?>) model.get("schemaVersions")).size());
    }

    @Test
    void requiresBackupConfirmationForRiskyMigrations() {
        when(schemaVersionService.getStatus()).thenReturn(status(true, false));
        when(messageSource.getMessage(
                eq("database.schema.backupConfirmationRequired"), any(Object[].class), any(Locale.class)))
                .thenReturn("Backup required");
        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

        String view = controller.apply(false, authentication, attributes);

        assertEquals("redirect:/database/schema", view);
        assertEquals("Backup required", attributes.getFlashAttributes().get("flashError"));
        verify(schemaVersionService, never()).applyPendingMigrations();
    }

    @Test
    void appliesAllPendingMigrationsAfterAdministratorApproval() {
        when(schemaVersionService.getStatus()).thenReturn(status(false, false));
        when(schemaVersionService.applyPendingMigrations()).thenReturn(status(false, true));
        when(messageSource.getMessage(
                eq("database.schema.updated"), any(Object[].class), any(Locale.class)))
                .thenReturn("Database schema updated");
        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

        String view = controller.apply(false, authentication, attributes);

        assertEquals("redirect:/database/schema", view);
        assertEquals("Database schema updated", attributes.getFlashAttributes().get("flashSuccess"));
        verify(schemaVersionService).applyPendingMigrations();
    }

    private Status status(boolean requiresBackup, boolean upToDate) {
        List<SchemaMigration> pending = upToDate ? List.of() : List.of(new SchemaMigration(
                new SchemaVersion(1, 1, 0), "title", "description", "migration.sql", requiresBackup));
        return new Status("1.0.0", "1.1.0",
                List.of(
                        new VersionEntry("1.0.0", "base.title", "base.description", true, true, false),
                        new VersionEntry("1.1.0", "next.title", "next.description", false, false,
                                requiresBackup)),
                pending, true, upToDate, null);
    }
}
