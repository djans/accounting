package com.cogitosum.service;

import com.cogitosum.config.LoggingCategory;
import com.cogitosum.setup.DatabaseConfigurationStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DatabaseMigrationService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMigrationService.class);
    private static final String MYSQL_PREFIX = "jdbc:mysql://";
    private static final String SQLITE_PREFIX = "jdbc:sqlite:";
    private static final String VERSION_TABLE = DatabaseSchemaVersionService.VERSION_TABLE;
    private static final Pattern CREATE_DATABASE_PARAMETER =
            Pattern.compile("(?i)([?&])createDatabaseIfNotExist=[^&]*");
    private static final Pattern MYSQL_URL_CREDENTIALS =
            Pattern.compile("(?i)[?&](user|password|passwd)=");
    private static final Pattern URL_PASSWORD_PARAMETER =
            Pattern.compile("(?i)([?&](?:password|passwd|pwd)=)[^&\\s,;]+");

    private final DataSource sourceDataSource;
    private final DatabaseBackupService backupService;
    private final DatabaseSchemaResetService schemaResetService;
    private final DatabaseSchemaVersionService schemaVersionService;
    private final DatabaseConfigurationStore configurationStore;
    private final ObjectMapper objectMapper;
    private final DataSourceProperties dataSourceProperties;

    public DatabaseMigrationService(
            DataSource sourceDataSource,
            DatabaseBackupService backupService,
            DatabaseSchemaResetService schemaResetService,
            DatabaseSchemaVersionService schemaVersionService,
            DatabaseConfigurationStore configurationStore,
            DataSourceProperties dataSourceProperties,
            ObjectMapper objectMapper) {
        this.sourceDataSource = sourceDataSource;
        this.backupService = backupService;
        this.schemaResetService = schemaResetService;
        this.schemaVersionService = schemaVersionService;
        this.configurationStore = configurationStore;
        this.dataSourceProperties = dataSourceProperties;
        this.objectMapper = objectMapper;
    }

    public String getSourceDatabaseType() {
        try (Connection connection = sourceDataSource.getConnection()) {
            return databaseType(connection.getMetaData());
        } catch (SQLException exception) {
            logTechnicalFailure("unknown", "unknown", exception, null);
            throw new MigrationException("database.engineMigration.error.source", exception);
        }
    }

    public MysqlConnectionDetails getSourceMysqlConnectionDetails() {
        if (!"mysql".equals(getSourceDatabaseType())) {
            throw new MigrationException("database.engineMigration.error.unsupportedSource");
        }

        String url = dataSourceProperties.getUrl();
        String username = dataSourceProperties.getUsername();
        if (url == null || url.isBlank() || username == null || username.isBlank()) {
            throw new MigrationException("database.engineMigration.error.source");
        }
        String safeUrl = url.replaceAll("(?i)([?&](?:user|password|passwd)=)[^&]*", "$1****");
        return new MysqlConnectionDetails(safeUrl, username, dataSourceProperties.getPassword());
    }

    public synchronized MigrationResult migrateToMysql(
            String urlValue, String usernameValue, String passwordValue) {
        String sourceType = getSourceDatabaseType();
        if ("mysql".equals(sourceType)) {
            throw new MigrationException("database.engineMigration.error.sameType");
        }

        String url = normalizeMysqlUrl(urlValue);
        String username = usernameValue == null ? "" : usernameValue.trim();
        if (username.isEmpty()) {
            throw new MigrationException("database.engineMigration.error.mysqlConfiguration");
        }

        Properties configuration = new Properties();
        configuration.setProperty("app.database.type", "mysql");
        configuration.setProperty("spring.datasource.url", url);
        configuration.setProperty("spring.datasource.username", username);
        configuration.setProperty("spring.datasource.password", passwordValue == null ? "" : passwordValue);
        configuration.setProperty("spring.datasource.driver-class-name", "com.mysql.cj.jdbc.Driver");
        configuration.setProperty("spring.jpa.database-platform", "org.hibernate.dialect.MySQLDialect");
        configuration.setProperty("spring.sql.init.schema-locations", "classpath:schema.sql");

        DriverManagerDataSource target = new DriverManagerDataSource();
        target.setDriverClassName("com.mysql.cj.jdbc.Driver");
        target.setUrl(url);
        target.setUsername(username);
        target.setPassword(passwordValue == null ? "" : passwordValue);
        return migrate(sourceType, "mysql", target, configuration);
    }

    public synchronized MigrationResult migrateToSqlite(String pathValue) {
        String sourceType = getSourceDatabaseType();
        if ("sqlite".equals(sourceType)) {
            throw new MigrationException("database.engineMigration.error.sameType");
        }
        if (pathValue == null || pathValue.isBlank() || pathValue.trim().startsWith(SQLITE_PREFIX)
                || ":memory:".equals(pathValue.trim())) {
            throw new MigrationException("database.engineMigration.error.sqliteConfiguration");
        }

        Path databaseFile;
        try {
            databaseFile = Path.of(pathValue.trim()).toAbsolutePath().normalize();
            if (databaseFile.getParent() == null || Files.isDirectory(databaseFile)) {
                throw new MigrationException("database.engineMigration.error.sqliteConfiguration");
            }
            Files.createDirectories(databaseFile.getParent());
        } catch (IOException exception) {
            logTechnicalFailure(sourceType, "sqlite", exception, null);
            throw new MigrationException("database.engineMigration.error.sqliteConfiguration", exception);
        } catch (RuntimeException exception) {
            if (exception instanceof MigrationException migrationException) {
                throw migrationException;
            }
            throw new MigrationException("database.engineMigration.error.sqliteConfiguration");
        }

        String url = SQLITE_PREFIX + databaseFile.toString().replace('\\', '/');
        Properties configuration = new Properties();
        configuration.setProperty("app.database.type", "sqlite");
        configuration.setProperty("spring.datasource.url", url);
        configuration.setProperty("spring.datasource.username", "");
        configuration.setProperty("spring.datasource.password", "");
        configuration.setProperty("spring.datasource.driver-class-name", "org.sqlite.JDBC");
        configuration.setProperty("spring.datasource.hikari.connection-init-sql", "PRAGMA foreign_keys=ON");
        configuration.setProperty("spring.jpa.database-platform", "org.hibernate.community.dialect.SQLiteDialect");
        configuration.setProperty("spring.sql.init.schema-locations", "classpath:schema-sqlite.sql");

        DriverManagerDataSource target = new DriverManagerDataSource();
        target.setDriverClassName("org.sqlite.JDBC");
        target.setUrl(url);
        return migrate(sourceType, "sqlite", target, configuration);
    }

    private MigrationResult migrate(String sourceType, String targetType, DataSource targetDataSource,
                                    Properties targetConfiguration) {
        try {
            DatabaseSchemaVersionService.Status sourceSchema = schemaVersionService.getStatus();
            if (!sourceSchema.migrationPossible() || !sourceSchema.upToDate()) {
                throw new MigrationException("database.engineMigration.error.schema");
            }

            byte[] backup = backupService.exportDatabase();
            Set<String> sourceTables = tableNamesInBackup(backup);
            prepareTargetSchema(targetDataSource, targetType, sourceTables);
            try (Connection targetConnection = targetDataSource.getConnection()) {
                schemaVersionService.applyPendingMigrations(targetConnection);
            }
            int importedRows = backupService.importDatabaseInto(backup, targetDataSource);
            validateSqliteForeignKeys(targetDataSource, targetType);
            try {
                configurationStore.save(targetConfiguration);
            } catch (IllegalStateException exception) {
                throw new MigrationException("database.engineMigration.error.configurationSave", exception);
            }
            try (LoggingCategory.Scope ignored = LoggingCategory.open(LoggingCategory.USER_ACTIVITY)) {
                log.info("Database migration completed: sourceType={} targetType={} importedRows={}",
                        sourceType, targetType, importedRows);
            }
            return new MigrationResult(sourceType, targetType, importedRows);
        } catch (MigrationException exception) {
            if (exception.getCause() != null) {
                logTechnicalFailure(sourceType, targetType, exception.getCause(),
                        targetConfiguration.getProperty("spring.datasource.password"));
            }
            throw exception;
        } catch (Exception exception) {
            logTechnicalFailure(sourceType, targetType, exception,
                    targetConfiguration.getProperty("spring.datasource.password"));
            throw new MigrationException("database.engineMigration.error.failed");
        }
    }

    private void logTechnicalFailure(String sourceType, String targetType,
                                     Throwable exception, String targetPassword) {
        StringWriter stackTrace = new StringWriter();
        exception.printStackTrace(new PrintWriter(stackTrace));
        String safeStackTrace = URL_PASSWORD_PARAMETER.matcher(stackTrace.toString())
                .replaceAll("$1****");
        safeStackTrace = redactSecret(safeStackTrace, targetPassword);
        safeStackTrace = redactSecret(safeStackTrace, dataSourceProperties.getPassword());
        try (LoggingCategory.Scope ignored = LoggingCategory.open(LoggingCategory.ERROR)) {
            log.error("Database migration technical failure: sourceType={} targetType={} errorType={}\n{}",
                    sourceType, targetType, exception.getClass().getSimpleName(), safeStackTrace);
        }
    }

    private String redactSecret(String message, String secret) {
        return secret == null || secret.isEmpty() ? message : message.replace(secret, "****");
    }

    private void prepareTargetSchema(DataSource targetDataSource, String targetType,
                                    Set<String> sourceTables) throws SQLException {
        try (Connection connection = targetDataSource.getConnection()) {
            String actualTargetType = databaseType(connection.getMetaData());
            if (!targetType.equals(actualTargetType)) {
                throw new MigrationException("database.engineMigration.error.targetType");
            }
            validateTargetObjects(connection, sourceTables, targetType);
            schemaResetService.recreateSchema(connection);
        }
    }

    private void validateTargetObjects(Connection connection, Set<String> sourceTables,
                                       String targetType) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        String catalog = "mysql".equals(targetType) ? connection.getCatalog() : null;
        Set<String> allowedTables = new HashSet<>(sourceTables);
        allowedTables.add(VERSION_TABLE.toLowerCase(Locale.ROOT));
        try (ResultSet tables = metadata.getTables(catalog, null, "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                String name = tables.getString("TABLE_NAME");
                if (name != null && !name.startsWith("sqlite_")
                        && !allowedTables.contains(name.toLowerCase(Locale.ROOT))) {
                    throw new MigrationException("database.engineMigration.error.unknownTargetObjects");
                }
            }
        }
        try (ResultSet views = metadata.getTables(catalog, null, "%", new String[]{"VIEW"})) {
            if (views.next()) {
                throw new MigrationException("database.engineMigration.error.unknownTargetObjects");
            }
        }
        if (hasTriggers(connection, targetType)) {
            throw new MigrationException("database.engineMigration.error.unknownTargetObjects");
        }
        if ("mysql".equals(targetType) && hasMySqlRoutinesOrEvents(connection)) {
            throw new MigrationException("database.engineMigration.error.unknownTargetObjects");
        }
    }

    private boolean hasMySqlRoutinesOrEvents(Connection connection) throws SQLException {
        String query = """
                SELECT
                    (SELECT COUNT(*) FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA = DATABASE())
                    + (SELECT COUNT(*) FROM information_schema.EVENTS WHERE EVENT_SCHEMA = DATABASE())
                """;
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(query)) {
            return result.next() && result.getLong(1) > 0;
        }
    }

    private void validateSqliteForeignKeys(DataSource targetDataSource, String targetType) throws SQLException {
        if (!"sqlite".equals(targetType)) {
            return;
        }
        try (Connection connection = targetDataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet violations = statement.executeQuery("PRAGMA foreign_key_check")) {
            if (violations.next()) {
                throw new MigrationException("database.engineMigration.error.foreignKeys");
            }
        }
    }

    private boolean hasTriggers(Connection connection, String databaseType) throws SQLException {
        String query = "mysql".equals(databaseType)
                ? "SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA = DATABASE()"
                : "SELECT COUNT(*) FROM sqlite_master WHERE type = 'trigger' AND name NOT LIKE 'sqlite_%'";
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(query)) {
            return result.next() && result.getLong(1) > 0;
        }
    }

    private Set<String> tableNamesInBackup(byte[] content) throws IOException {
        JsonNode backup = objectMapper.readTree(content);
        Set<String> tableNames = new HashSet<>();
        backup.path("tables").forEach(table ->
                tableNames.add(table.path("name").asText().toLowerCase(Locale.ROOT)));
        if (tableNames.isEmpty()) {
            throw new MigrationException("database.engineMigration.error.source");
        }
        return tableNames;
    }

    private String databaseType(DatabaseMetaData metadata) throws SQLException {
        String product = metadata.getDatabaseProductName().toLowerCase(Locale.ROOT);
        if (product.contains("mysql") || product.contains("mariadb")) {
            return "mysql";
        }
        if (product.contains("sqlite")) {
            return "sqlite";
        }
        throw new MigrationException("database.engineMigration.error.unsupportedSource");
    }

    private String normalizeMysqlUrl(String value) {
        if (value == null || !value.trim().startsWith(MYSQL_PREFIX)) {
            throw new MigrationException("database.engineMigration.error.mysqlConfiguration");
        }
        String url = value.trim();
        try {
            java.net.URI uri = java.net.URI.create(url.substring("jdbc:".length()));
            String databasePath = uri.getRawPath();
            String databaseName = databasePath == null ? "" : databasePath.substring(1);
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
                    || MYSQL_URL_CREDENTIALS.matcher(url).find()
                    || !databaseName.matches("[A-Za-z0-9_$-]+")
                    || Set.of("mysql", "information_schema", "performance_schema", "sys")
                            .contains(databaseName.toLowerCase(Locale.ROOT))) {
                throw new MigrationException("database.engineMigration.error.mysqlConfiguration");
            }
        } catch (IllegalArgumentException exception) {
            throw new MigrationException("database.engineMigration.error.mysqlConfiguration");
        }

        Matcher matcher = CREATE_DATABASE_PARAMETER.matcher(url);
        if (matcher.find()) {
            return matcher.replaceFirst("$1createDatabaseIfNotExist=true");
        }
        return url + (url.contains("?") ? "&" : "?") + "createDatabaseIfNotExist=true";
    }

    public record MigrationResult(String sourceType, String targetType, int importedRows) {
    }

    public record MysqlConnectionDetails(String jdbcUrl, String username, String password) {
    }

    public static final class MigrationException extends RuntimeException {
        private final String messageKey;

        public MigrationException(String messageKey) {
            super(messageKey);
            this.messageKey = messageKey;
        }

        public MigrationException(String messageKey, Throwable cause) {
            super(messageKey, cause);
            this.messageKey = messageKey;
        }

        public String messageKey() {
            return messageKey;
        }
    }
}
