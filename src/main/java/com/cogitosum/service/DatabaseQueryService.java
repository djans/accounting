package com.cogitosum.service;

import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class DatabaseQueryService {

    private static final int MAX_SQL_LENGTH = 20_000;
    private static final int MAX_RESULT_ROWS = 200;
    private static final int MAX_RESULT_COLUMNS = 50;
    private static final int MAX_CELL_LENGTH = 1_000;
    private static final Set<String> READ_ONLY_COMMANDS =
            Set.of("SELECT", "SHOW", "DESCRIBE", "DESC", "EXPLAIN");
    private static final Set<String> SYSTEM_SCHEMAS =
            Set.of("INFORMATION_SCHEMA", "MYSQL", "PERFORMANCE_SCHEMA", "SYS");

    private final DataSource dataSource;

    public DatabaseQueryService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<String> getApplicationTables() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            boolean sqlite = metadata.getDatabaseProductName().toLowerCase(Locale.ROOT).contains("sqlite");
            String catalog = sqlite ? null : connection.getCatalog();
            String schema = sqlite ? null : connection.getSchema();
            List<String> tables = new ArrayList<>();
            try (ResultSet results = metadata.getTables(catalog, schema, "%", new String[]{"TABLE"})) {
                while (results.next()) {
                    String tableCatalog = results.getString("TABLE_CAT");
                    String tableSchema = results.getString("TABLE_SCHEM");
                    String tableName = results.getString("TABLE_NAME");
                    if (isApplicationTable(catalog, schema, tableCatalog, tableSchema, tableName)) {
                        tables.add(tableName);
                    }
                }
            }
            return tables.stream().distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        }
    }

    public String defaultStatement(String tableName) throws SQLException {
        if (!getApplicationTables().contains(tableName)) {
            throw new IllegalArgumentException("database.query.error.table");
        }
        try (Connection connection = dataSource.getConnection()) {
            String quote = connection.getMetaData().getIdentifierQuoteString().trim();
            String identifier = quote.isEmpty()
                    ? tableName
                    : quote + tableName.replace(quote, quote + quote) + quote;
            return "SELECT * FROM " + identifier + " LIMIT 100;";
        }
    }

    public boolean requiresConfirmation(String sql) {
        String command = statementType(sql);
        String upperStatement = sql == null ? "" : sql.toUpperCase(Locale.ROOT);
        return !READ_ONLY_COMMANDS.contains(command)
                || (command.equals("SELECT")
                && (upperStatement.matches("(?s).*\\bINTO\\b.*")
                || upperStatement.matches("(?s).*\\bFOR\\s+UPDATE\\b.*")));
    }

    public String statementType(String sql) {
        if (sql == null) {
            return "";
        }
        int start = skipLeadingCommentsAndWhitespace(sql);
        int end = start;
        while (end < sql.length() && Character.isLetter(sql.charAt(end))) {
            end++;
        }
        return sql.substring(start, end).toUpperCase(Locale.ROOT);
    }

    public QueryResult execute(String selectedTable, String sql, boolean changesConfirmed) throws SQLException {
        if (selectedTable == null || !getApplicationTables().contains(selectedTable)) {
            throw new IllegalArgumentException("database.query.error.table");
        }
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("database.query.error.empty");
        }
        if (sql.length() > MAX_SQL_LENGTH) {
            throw new IllegalArgumentException("database.query.error.length");
        }
        validateSingleStatement(sql);
        if (requiresConfirmation(sql) && !changesConfirmed) {
            throw new ConfirmationRequiredException();
        }

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(30);
            statement.setMaxRows(MAX_RESULT_ROWS + 1);
            boolean hasResultSet = statement.execute(sql);
            if (!hasResultSet) {
                return new QueryResult(false, List.of(), List.of(),
                        statement.getLargeUpdateCount(), false, false);
            }

            try (ResultSet resultSet = statement.getResultSet()) {
                ResultSetMetaData metadata = resultSet.getMetaData();
                int visibleColumns = Math.min(metadata.getColumnCount(), MAX_RESULT_COLUMNS);
                List<String> columns = new ArrayList<>(visibleColumns);
                for (int i = 1; i <= visibleColumns; i++) {
                    columns.add(metadata.getColumnLabel(i));
                }

                List<List<String>> rows = new ArrayList<>();
                boolean rowsTruncated = false;
                while (resultSet.next()) {
                    if (rows.size() == MAX_RESULT_ROWS) {
                        rowsTruncated = true;
                        break;
                    }
                    List<String> row = new ArrayList<>(visibleColumns);
                    for (int i = 1; i <= visibleColumns; i++) {
                        row.add(formatValue(resultSet.getObject(i)));
                    }
                    rows.add(row);
                }
                return new QueryResult(true, columns, rows, -1, rowsTruncated,
                        metadata.getColumnCount() > MAX_RESULT_COLUMNS);
            }
        }
    }

    private boolean isApplicationTable(String catalog, String schema, String tableCatalog,
                                       String tableSchema, String tableName) {
        if (tableName == null || tableName.toLowerCase(Locale.ROOT).startsWith("flyway")
                || tableName.toLowerCase(Locale.ROOT).startsWith("sqlite_")) {
            return false;
        }
        if (catalog != null && tableCatalog != null && !catalog.equalsIgnoreCase(tableCatalog)) {
            return false;
        }
        if (schema != null && tableSchema != null && !schema.equalsIgnoreCase(tableSchema)) {
            return false;
        }
        return tableSchema == null || !SYSTEM_SCHEMAS.contains(tableSchema.toUpperCase(Locale.ROOT));
    }

    private int skipLeadingCommentsAndWhitespace(String sql) {
        int position = 0;
        while (position < sql.length()) {
            while (position < sql.length() && Character.isWhitespace(sql.charAt(position))) {
                position++;
            }
            if (position + 1 < sql.length() && sql.charAt(position) == '-'
                    && sql.charAt(position + 1) == '-'
                    && (position + 2 == sql.length() || Character.isWhitespace(sql.charAt(position + 2)))) {
                position = skipLine(sql, position + 2);
            } else if (position < sql.length() && sql.charAt(position) == '#') {
                position = skipLine(sql, position + 1);
            } else if (position + 1 < sql.length() && sql.charAt(position) == '/'
                    && sql.charAt(position + 1) == '*') {
                int endComment = sql.indexOf("*/", position + 2);
                if (endComment < 0) {
                    return sql.length();
                }
                position = endComment + 2;
            } else {
                return position;
            }
        }
        return position;
    }

    private int skipLine(String sql, int position) {
        while (position < sql.length() && sql.charAt(position) != '\n' && sql.charAt(position) != '\r') {
            position++;
        }
        return position;
    }

    private String formatValue(Object value) throws SQLException {
        if (value == null) {
            return "";
        }
        if (value instanceof byte[] bytes) {
            return "[binary data: " + bytes.length + " bytes]";
        }
        if (value instanceof Blob blob) {
            return "[BLOB: " + blob.length() + " bytes]";
        }
        if (value instanceof Clob clob) {
            return "[CLOB: " + clob.length() + " characters]";
        }
        String text = value.toString();
        return text.length() <= MAX_CELL_LENGTH
                ? text
                : text.substring(0, MAX_CELL_LENGTH) + "...";
    }

    private void validateSingleStatement(String sql) {
        char quote = 0;
        boolean lineComment = false;
        boolean blockComment = false;
        boolean statementEnded = false;

        for (int i = 0; i < sql.length(); i++) {
            char current = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';

            if (lineComment) {
                if (current == '\n' || current == '\r') {
                    lineComment = false;
                }
                continue;
            }
            if (blockComment) {
                if (current == '*' && next == '/') {
                    blockComment = false;
                    i++;
                }
                continue;
            }
            if (quote != 0) {
                if (current == '\\' && quote != '`' && i + 1 < sql.length()) {
                    i++;
                } else if (current == quote) {
                    if (next == quote) {
                        i++;
                    } else {
                        quote = 0;
                    }
                }
                continue;
            }
            if (current == '-' && next == '-' && (i + 2 == sql.length()
                    || Character.isWhitespace(sql.charAt(i + 2)))) {
                lineComment = true;
                i++;
            } else if (current == '#') {
                lineComment = true;
            } else if (current == '/' && next == '*') {
                blockComment = true;
                i++;
            } else if (current == '\'' || current == '"' || current == '`') {
                if (statementEnded) {
                    throw new IllegalArgumentException("database.query.error.multiple");
                }
                quote = current;
            } else if (current == ';') {
                if (statementEnded) {
                    throw new IllegalArgumentException("database.query.error.multiple");
                }
                statementEnded = true;
            } else if (statementEnded && !Character.isWhitespace(current)) {
                throw new IllegalArgumentException("database.query.error.multiple");
            }
        }

        if (quote != 0 || blockComment) {
            throw new IllegalArgumentException("database.query.error.unterminated");
        }
    }

    public record QueryResult(boolean resultSet, List<String> columns, List<List<String>> rows,
                              long affectedRows, boolean rowsTruncated, boolean columnsTruncated) {
    }

    public static class ConfirmationRequiredException extends IllegalArgumentException {
        public ConfirmationRequiredException() {
            super("database.query.error.confirmation");
        }
    }
}
