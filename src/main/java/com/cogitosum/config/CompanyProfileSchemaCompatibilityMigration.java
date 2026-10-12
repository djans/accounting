package com.cogitosum.config;

import com.cogitosum.service.DatabaseSchemaVersionService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(-1)
public class CompanyProfileSchemaCompatibilityMigration implements CommandLineRunner {

    private final DatabaseSchemaVersionService schemaVersionService;

    public CompanyProfileSchemaCompatibilityMigration(DatabaseSchemaVersionService schemaVersionService) {
        this.schemaVersionService = schemaVersionService;
    }

    @Override
    public void run(String... args) {
        schemaVersionService.ensureCompanyProfileColumnsBeforeAuthentication();
    }
}
