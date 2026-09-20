package com.cogitosum.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Types;
import java.sql.Timestamp;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DatabaseBackupService {

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;

    public DatabaseBackupService(JdbcTemplate jdbcTemplate, DataSource dataSource,
                                 ObjectMapper objectMapper, EntityManager entityManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public byte[] exportDatabase() throws IOException {
        ObjectNode backup = objectMapper.createObjectNode();
        backup.put("format", "cogitosum-accounting-backup");
        backup.put("version", 1);
        backup.put("createdAt", java.time.Instant.now().toString());

        ArrayNode tables = backup.putArray("tables");
        for (String table : getTableNames()) {
            ObjectNode tableNode = tables.addObject();
            tableNode.put("name", table);
            ArrayNode rows = tableNode.putArray("rows");
            jdbcTemplate.queryForList("SELECT * FROM " + quote(table)).forEach(row -> {
                ObjectNode rowNode = rows.addObject();
                row.forEach((column, value) -> rowNode.set(column, objectMapper.valueToTree(value)));
            });
        }
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(backup);
    }

    @Transactional
    public int importDatabase(byte[] content) throws IOException {
        JsonNode backup = objectMapper.readTree(content);
        if (!"cogitosum-accounting-backup".equals(backup.path("format").asText())
                || backup.path("version").asInt() != 1
                || !backup.path("tables").isArray()) {
            throw new IllegalArgumentException("Invalid or unsupported backup file");
        }

        List<JsonNode> backupTables = new ArrayList<>();
        backup.path("tables").forEach(backupTables::add);
        Set<String> availableTables = new HashSet<>(getTableNames());
        Set<String> requestedTables = new LinkedHashSet<>();
        for (JsonNode table : backupTables) {
            String name = table.path("name").asText();
            if (name.isBlank() || !availableTables.contains(name) || !requestedTables.add(name)) {
                throw new IllegalArgumentException("Backup contains an unknown or duplicate table: " + name);
            }
        }

        setReferentialIntegrity(false);
        try {
            for (String table : availableTables) {
                jdbcTemplate.update("DELETE FROM " + quote(table));
            }
            int importedRows = 0;
            for (JsonNode table : backupTables) {
                String tableName = table.path("name").asText();
                Map<String, Integer> columnTypes = getColumnTypes(tableName);
                Iterator<JsonNode> rows = table.path("rows").values().iterator();
                while (rows.hasNext()) {
                    JsonNode row = rows.next();
                    List<String> columns = new ArrayList<>();
                    List<Object> values = new ArrayList<>();
                    row.properties().forEach(field -> {
                        columns.add(field.getKey());
                        values.add(toJdbcValue(field.getValue(), columnTypes.get(field.getKey())));
                    });
                    if (!columns.isEmpty()) {
                        String columnList = columns.stream().map(this::quote).reduce((a, b) -> a + ", " + b).orElseThrow();
                        String placeholders = columns.stream().map(column -> "?").reduce((a, b) -> a + ", " + b).orElseThrow();
                        jdbcTemplate.update("INSERT INTO " + quote(tableName) + " (" + columnList + ") VALUES (" + placeholders + ")", values.toArray());
                        importedRows++;
                    }
                }
            }
            entityManager.clear();
            return importedRows;
        } finally {
            setReferentialIntegrity(true);
        }
    }

    private Set<String> getTableNames() {
        Set<String> tables = new LinkedHashSet<>();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            try (ResultSet result = metadata.getTables(connection.getCatalog(), connection.getSchema(), "%", new String[]{"TABLE"})) {
                while (result.next()) {
                    String name = result.getString("TABLE_NAME");
                    if (name != null && !name.startsWith("flyway")) {
                        tables.add(name);
                    }
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not inspect database tables", e);
        }
        return tables;
    }

    private Map<String, Integer> getColumnTypes(String tableName) {
        Map<String, Integer> columnTypes = new java.util.HashMap<>();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            try (ResultSet result = metadata.getColumns(connection.getCatalog(), connection.getSchema(), tableName, "%")) {
                while (result.next()) {
                    columnTypes.put(result.getString("COLUMN_NAME"), result.getInt("DATA_TYPE"));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not inspect columns for table " + tableName, e);
        }
        return columnTypes;
    }

    private Object toJdbcValue(JsonNode value, Integer sqlType) {
        if (value == null || value.isNull()) return null;
        if (value.isBoolean()) return value.booleanValue();
        if (value.isIntegralNumber()) return value.longValue();
        if (value.isFloatingPointNumber()) return value.decimalValue();
        String text = value.asText();
        if (sqlType == null) return text;
        try {
            if (sqlType == Types.DATE) {
                return Date.valueOf(LocalDate.parse(text.substring(0, 10)));
            }
            if (sqlType == Types.TIMESTAMP || sqlType == Types.TIMESTAMP_WITH_TIMEZONE) {
                return Timestamp.from(parseInstant(text));
            }
            if (sqlType == Types.TIME || sqlType == Types.TIME_WITH_TIMEZONE) {
                return java.sql.Time.valueOf(text.substring(0, 8));
            }
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid value for SQL type " + sqlType + ": " + text, e);
        }
        return text;
    }

    private Instant parseInstant(String text) {
        try {
            return Instant.parse(text);
        } catch (java.time.format.DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(text).toInstant();
            } catch (java.time.format.DateTimeParseException ignoredAgain) {
                return LocalDateTime.parse(text).toInstant(ZoneOffset.UTC);
            }
        }
    }

    private String quote(String identifier) {
        try (Connection connection = dataSource.getConnection()) {
            String quote = connection.getMetaData().getIdentifierQuoteString().trim();
            return quote.isEmpty() ? identifier : quote + identifier.replace(quote, quote + quote) + quote;
        } catch (Exception e) {
            throw new IllegalStateException("Could not quote database identifier", e);
        }
    }

    private void setReferentialIntegrity(boolean enabled) {
        String product;
        try (Connection connection = dataSource.getConnection()) {
            product = connection.getMetaData().getDatabaseProductName().toLowerCase();
        } catch (Exception e) {
            throw new IllegalStateException("Could not identify database", e);
        }
        if (product.contains("mysql")) {
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = " + (enabled ? "1" : "0"));
        } else if (product.contains("h2")) {
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY " + (enabled ? "TRUE" : "FALSE"));
        } else {
            throw new IllegalStateException("Database backup is not supported for " + product);
        }
    }
}
