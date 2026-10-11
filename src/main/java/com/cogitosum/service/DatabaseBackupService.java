package com.cogitosum.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Base64;

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
            jdbcTemplate.queryForList("SELECT * FROM " + quote(dataSource, table)).forEach(row -> {
                ObjectNode rowNode = rows.addObject();
                row.forEach((column, value) -> rowNode.set(column, objectMapper.valueToTree(value)));
            });
        }
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(backup);
    }

    @Transactional
    public int importDatabase(byte[] content) throws IOException {
        JsonNode backup = objectMapper.readTree(content);
        int importedRows = importDatabase(backup, jdbcTemplate, dataSource);
        entityManager.clear();
        return importedRows;
    }

    public int importDatabaseInto(byte[] content, DataSource targetDataSource) throws IOException {
        JsonNode backup = objectMapper.readTree(content);
        JdbcTemplate targetJdbcTemplate = new JdbcTemplate(targetDataSource);
        Integer importedRows = new TransactionTemplate(new DataSourceTransactionManager(targetDataSource))
                .execute(status -> importDatabase(backup, targetJdbcTemplate, targetDataSource));
        if (importedRows == null) {
            throw new IllegalStateException("Database restore completed without a row count");
        }
        return importedRows;
    }

    private int importDatabase(JsonNode backup, JdbcTemplate targetJdbcTemplate, DataSource targetDataSource) {
        if (!"cogitosum-accounting-backup".equals(backup.path("format").asText())
                || backup.path("version").asInt() != 1
                || !backup.path("tables").isArray()) {
            throw new IllegalArgumentException("Invalid or unsupported backup file");
        }

        List<JsonNode> backupTables = new ArrayList<>();
        backup.path("tables").forEach(backupTables::add);
        Set<String> availableTables = new HashSet<>(getTableNames(targetDataSource));
        Set<String> requestedTables = new LinkedHashSet<>();
        Map<String, Map<String, SqlColumnType>> tableColumnTypes = new java.util.HashMap<>();
        for (JsonNode table : backupTables) {
            String name = table.path("name").asText();
            if (name.isBlank() || !availableTables.contains(name) || !requestedTables.add(name)) {
                throw new IllegalArgumentException("Backup contains an unknown or duplicate table: " + name);
            }
            if (!table.path("rows").isArray()) {
                throw new IllegalArgumentException("Backup table does not contain a row array: " + name);
            }
            Map<String, SqlColumnType> columnTypes = getColumnTypes(targetDataSource, name);
            Set<String> availableColumns = columnTypes.keySet().stream()
                    .map(column -> column.toLowerCase(Locale.ROOT))
                    .collect(java.util.stream.Collectors.toSet());
            for (JsonNode row : table.path("rows")) {
                if (!row.isObject()) {
                    throw new IllegalArgumentException("Backup contains an invalid row in table: " + name);
                }
                row.properties().forEach(field -> {
                    String column = field.getKey();
                    if (!availableColumns.contains(column.toLowerCase(Locale.ROOT))) {
                        throw new IllegalArgumentException(
                                "Backup contains an unknown column " + column + " in table " + name);
                    }
                });
            }
            tableColumnTypes.put(name, columnTypes);
        }

        setReferentialIntegrity(targetJdbcTemplate, targetDataSource, false);
        try {
            for (String table : availableTables) {
                targetJdbcTemplate.update("DELETE FROM " + quote(targetDataSource, table));
            }
            int importedRows = 0;
            for (JsonNode table : backupTables) {
                String tableName = table.path("name").asText();
                Map<String, SqlColumnType> columnTypes = tableColumnTypes.get(tableName);
                Iterator<JsonNode> rows = table.path("rows").values().iterator();
                while (rows.hasNext()) {
                    JsonNode row = rows.next();
                    List<String> columns = new ArrayList<>();
                    List<Object> values = new ArrayList<>();
                    row.properties().forEach(field -> {
                        columns.add(field.getKey());
                        values.add(toJdbcValue(field.getValue(), findColumnType(columnTypes, field.getKey())));
                    });
                    if (!columns.isEmpty()) {
                        String columnList = columns.stream().map(column -> quote(targetDataSource, column))
                                .reduce((a, b) -> a + ", " + b).orElseThrow();
                        String placeholders = columns.stream().map(column -> "?").reduce((a, b) -> a + ", " + b).orElseThrow();
                        targetJdbcTemplate.update("INSERT INTO " + quote(targetDataSource, tableName)
                                + " (" + columnList + ") VALUES (" + placeholders + ")", values.toArray());
                        importedRows++;
                    }
                }
            }
            return importedRows;
        } finally {
            setReferentialIntegrity(targetJdbcTemplate, targetDataSource, true);
        }
    }

    private Set<String> getTableNames() {
        return getTableNames(dataSource);
    }

    private Set<String> getTableNames(DataSource sourceDataSource) {
        Set<String> tables = new LinkedHashSet<>();
        try (Connection connection = sourceDataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            boolean sqlite = metadata.getDatabaseProductName().toLowerCase(Locale.ROOT).contains("sqlite");
            String catalog = sqlite ? null : connection.getCatalog();
            String schema = sqlite ? null : connection.getSchema();
            try (ResultSet result = metadata.getTables(catalog, schema, "%", new String[]{"TABLE"})) {
                while (result.next()) {
                    String name = result.getString("TABLE_NAME");
                    if (name != null && !name.startsWith("flyway")
                            && (!sqlite || !name.startsWith("sqlite_"))) {
                        tables.add(name);
                    }
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not inspect database tables", e);
        }
        return tables;
    }

    private Map<String, SqlColumnType> getColumnTypes(DataSource sourceDataSource, String tableName) {
        Map<String, SqlColumnType> columnTypes = new java.util.HashMap<>();
        try (Connection connection = sourceDataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            boolean sqlite = metadata.getDatabaseProductName().toLowerCase(Locale.ROOT).contains("sqlite");
            String catalog = sqlite ? null : connection.getCatalog();
            String schema = sqlite ? null : connection.getSchema();
            try (ResultSet result = metadata.getColumns(catalog, schema, tableName, "%")) {
                while (result.next()) {
                    columnTypes.put(result.getString("COLUMN_NAME"),
                            new SqlColumnType(result.getInt("DATA_TYPE"), result.getString("TYPE_NAME")));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not inspect columns for table " + tableName, e);
        }
        return columnTypes;
    }

    private Object toJdbcValue(JsonNode value, SqlColumnType columnType) {
        if (value == null || value.isNull()) return null;
        if (value.isBoolean()) return value.booleanValue();
        if (value.isIntegralNumber()) return value.longValue();
        if (value.isFloatingPointNumber()) return value.decimalValue();
        String text = value.asText();
        if (columnType == null) return text;
        int sqlType = columnType.jdbcType();
        try {
            if (sqlType == Types.BOOLEAN || sqlType == Types.BIT
                    || "BOOLEAN".equalsIgnoreCase(columnType.typeName())) {
                if ("true".equalsIgnoreCase(text) || "1".equals(text)) return true;
                if ("false".equalsIgnoreCase(text) || "0".equals(text)) return false;
                byte[] bits = Base64.getDecoder().decode(text);
                if (bits.length == 0) {
                    throw new IllegalArgumentException("Boolean value is empty");
                }
                for (byte bit : bits) {
                    if (bit != 0) return true;
                }
                return false;
            }
            if (sqlType == Types.DATE) {
                return Date.valueOf(LocalDate.parse(text.substring(0, 10)));
            }
            if (sqlType == Types.TIMESTAMP || sqlType == Types.TIMESTAMP_WITH_TIMEZONE) {
                return Timestamp.from(parseInstant(text));
            }
            if (sqlType == Types.TIME || sqlType == Types.TIME_WITH_TIMEZONE) {
                return java.sql.Time.valueOf(text.substring(0, 8));
            }
            if (sqlType == Types.BINARY || sqlType == Types.VARBINARY
                    || sqlType == Types.LONGVARBINARY || sqlType == Types.BLOB
                    || "BLOB".equalsIgnoreCase(columnType.typeName())) {
                return Base64.getDecoder().decode(text);
            }
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid value for SQL type " + sqlType + ": " + text, e);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid binary value for SQL type " + sqlType, e);
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

    private SqlColumnType findColumnType(Map<String, SqlColumnType> columnTypes, String columnName) {
        return columnTypes.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(columnName))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private record SqlColumnType(int jdbcType, String typeName) {
    }

    private String quote(DataSource sourceDataSource, String identifier) {
        try (Connection connection = sourceDataSource.getConnection()) {
            String quote = connection.getMetaData().getIdentifierQuoteString().trim();
            return quote.isEmpty() ? identifier : quote + identifier.replace(quote, quote + quote) + quote;
        } catch (Exception e) {
            throw new IllegalStateException("Could not quote database identifier", e);
        }
    }

    private void setReferentialIntegrity(JdbcTemplate targetJdbcTemplate,
                                        DataSource targetDataSource,
                                        boolean enabled) {
        String product;
        try (Connection connection = targetDataSource.getConnection()) {
            product = connection.getMetaData().getDatabaseProductName().toLowerCase();
        } catch (Exception e) {
            throw new IllegalStateException("Could not identify database", e);
        }
        if (product.contains("mysql")) {
            targetJdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = " + (enabled ? "1" : "0"));
        } else if (product.contains("h2")) {
            targetJdbcTemplate.execute("SET REFERENTIAL_INTEGRITY " + (enabled ? "TRUE" : "FALSE"));
        } else if (product.contains("sqlite")) {
            if (!enabled) {
                targetJdbcTemplate.execute("PRAGMA defer_foreign_keys = ON");
            }
        } else {
            throw new IllegalStateException("Database backup is not supported for " + product);
        }
    }
}
