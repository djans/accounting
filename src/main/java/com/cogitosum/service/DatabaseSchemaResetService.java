package com.cogitosum.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

@Service
public class DatabaseSchemaResetService {

    private final DataSource dataSource;
    private final DatabaseSchemaVersionService schemaVersionService;

    public DatabaseSchemaResetService(
            DataSource dataSource,
            DatabaseSchemaVersionService schemaVersionService) {
        this.dataSource = dataSource;
        this.schemaVersionService = schemaVersionService;
    }

    public int dropAndRecreate() {
        int droppedTables;
        try (Connection connection = dataSource.getConnection()) {
            String catalog = connection.getCatalog();
            if (!StringUtils.hasText(catalog)) {
                throw new IllegalStateException("The active database catalog could not be determined.");
            }

            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(true);
            boolean foreignKeysDisabled = false;
            try {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("SET FOREIGN_KEY_CHECKS = 0");
                    foreignKeysDisabled = true;
                }

                List<String> tables = listTables(connection, catalog);
                String quote = connection.getMetaData().getIdentifierQuoteString().trim();
                for (String table : tables) {
                    String quotedTable = quote.isEmpty()
                            ? table
                            : quote + table.replace(quote, quote + quote) + quote;
                    try (Statement statement = connection.createStatement()) {
                        statement.execute("DROP TABLE IF EXISTS " + quotedTable);
                    }
                }

                ResourceDatabasePopulator schema = new ResourceDatabasePopulator(
                        new ClassPathResource("schema.sql"));
                schema.populate(connection);
                droppedTables = tables.size();
            } finally {
                try {
                    if (foreignKeysDisabled) {
                        try (Statement statement = connection.createStatement()) {
                            statement.execute("SET FOREIGN_KEY_CHECKS = 1");
                        }
                    }
                } finally {
                    connection.setAutoCommit(originalAutoCommit);
                }
            }
        } catch (SQLException exception) {
            throw new DataAccessResourceFailureException(
                    "Could not drop and recreate the application database schema.", exception);
        }
        schemaVersionService.applyPendingMigrations();
        return droppedTables;
    }

    private List<String> listTables(Connection connection, String catalog) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (ResultSet results = connection.getMetaData()
                .getTables(catalog, null, "%", new String[]{"TABLE"})) {
            while (results.next()) {
                tables.add(results.getString("TABLE_NAME"));
            }
        }
        return tables;
    }
}
