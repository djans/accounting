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
            String product = connection.getMetaData().getDatabaseProductName().toLowerCase();
            boolean mysql = product.contains("mysql");
            boolean sqlite = product.contains("sqlite");
            if (!mysql && !sqlite) {
                throw new IllegalStateException("Schema reset is not supported for " + product);
            }
            String catalog = connection.getCatalog();
            if (mysql && !StringUtils.hasText(catalog)) {
                throw new IllegalStateException("The active database catalog could not be determined.");
            }

            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(true);
            boolean foreignKeysDisabled = false;
            try {
                setForeignKeyChecks(connection, mysql, false);
                foreignKeysDisabled = true;

                List<String> tables = listTables(connection, mysql ? catalog : null, sqlite);
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
                        new ClassPathResource(sqlite ? "schema-sqlite.sql" : "schema.sql"));
                schema.populate(connection);
                droppedTables = tables.size();
            } finally {
                try {
                    if (foreignKeysDisabled) {
                        setForeignKeyChecks(connection, mysql, true);
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

    private void setForeignKeyChecks(Connection connection, boolean mysql, boolean enabled)
            throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(mysql
                    ? "SET FOREIGN_KEY_CHECKS = " + (enabled ? "1" : "0")
                    : "PRAGMA foreign_keys = " + (enabled ? "ON" : "OFF"));
        }
    }

    private List<String> listTables(Connection connection, String catalog, boolean sqlite) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (ResultSet results = connection.getMetaData()
                .getTables(catalog, null, "%", new String[]{"TABLE"})) {
            while (results.next()) {
                String tableName = results.getString("TABLE_NAME");
                if (tableName != null && (!sqlite || !tableName.startsWith("sqlite_"))) {
                    tables.add(tableName);
                }
            }
        }
        return tables;
    }
}
