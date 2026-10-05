package com.cogitosum.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;

@Component
@Order(1)
public class TaxReturnLineMappingMigration implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public TaxReturnLineMappingMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) throws Exception {
        ensureColumn("tax_items", "sales_return_line", "VARCHAR(20) NULL");
        ensureColumn("tax_items", "purchase_return_line", "VARCHAR(20) NULL");
        ensureColumn("bills", "tax_regime", "VARCHAR(30) NULL");
    }

    private void ensureColumn(String table, String column, String definition) throws Exception {
        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            String actualTableName = findTableName(metadata, connection, table);
            if (actualTableName == null || columnExists(metadata, connection, actualTableName, column)) {
                return;
            }
        }
        jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
    }

    private String findTableName(DatabaseMetaData metadata, Connection connection, String table) throws Exception {
        try (ResultSet tables = metadata.getTables(connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                if (table.equalsIgnoreCase(tableName)) {
                    return tableName;
                }
            }
        }
        return null;
    }

    private boolean columnExists(DatabaseMetaData metadata, Connection connection, String table, String column)
            throws Exception {
        try (ResultSet columns = metadata.getColumns(connection.getCatalog(), null, table, "%")) {
            while (columns.next()) {
                if (column.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }
}
