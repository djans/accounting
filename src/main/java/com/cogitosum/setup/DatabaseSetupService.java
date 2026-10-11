package com.cogitosum.setup;

import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

@Service
@ConditionalOnProperty(name = "app.bootstrap.mode", havingValue = "database-setup")
public class DatabaseSetupService {

    private static final String MYSQL_PREFIX = "jdbc:mysql://";

    private final DatabaseConfigurationStore configurationStore;

    public DatabaseSetupService(DatabaseConfigurationStore configurationStore) {
        this.configurationStore = configurationStore;
    }

    public void configure(String databaseType, String mysqlUrl, String username,
                          String password, String sqlitePath) {
        if ("mysql".equalsIgnoreCase(databaseType)) {
            configureMySql(mysqlUrl, username, password);
        } else if ("sqlite".equalsIgnoreCase(databaseType)) {
            configureSqlite(sqlitePath);
        } else {
            throw new IllegalArgumentException("Unsupported database type");
        }
    }

    private void configureMySql(String urlValue, String usernameValue, String passwordValue) {
        String url = normalizeMySqlUrl(urlValue);
        String username = usernameValue == null ? "" : usernameValue.trim();
        if (username.isEmpty()) {
            throw new IllegalArgumentException("MySQL username is required");
        }

        Properties credentials = new Properties();
        credentials.setProperty("user", username);
        credentials.setProperty("password", passwordValue == null ? "" : passwordValue);
        try (Connection connection = DriverManager.getConnection(url, credentials)) {
            if (!connection.isValid(5)) {
                throw new SQLException("The MySQL connection is not valid");
            }
        } catch (SQLException exception) {
            throw new DatabaseSetupException("database.setup.error.connection", exception);
        }

        Properties configuration = new Properties();
        configuration.setProperty("app.database.type", "mysql");
        configuration.setProperty("spring.datasource.url", url);
        configuration.setProperty("spring.datasource.username", username);
        configuration.setProperty("spring.datasource.password", passwordValue == null ? "" : passwordValue);
        configuration.setProperty("spring.datasource.driver-class-name", "com.mysql.cj.jdbc.Driver");
        configuration.setProperty("spring.jpa.database-platform", "org.hibernate.dialect.MySQLDialect");
        configuration.setProperty("spring.sql.init.schema-locations", "classpath:schema.sql");
        saveConfiguration(configuration);
    }

    private void configureSqlite(String pathValue) {
        if (pathValue == null || pathValue.isBlank()) {
            throw new IllegalArgumentException("SQLite database path is required");
        }
        Path databaseFile = Path.of(pathValue.trim()).toAbsolutePath().normalize();
        Path parent = databaseFile.getParent();
        if (parent == null || Files.isDirectory(databaseFile)) {
            throw new IllegalArgumentException("SQLite database path must point to a file");
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException exception) {
            throw new DatabaseSetupException("database.setup.error.sqlitePath", exception);
        }

        String url = "jdbc:sqlite:" + databaseFile.toString().replace('\\', '/');
        try (Connection connection = DriverManager.getConnection(url);
             Statement statement = connection.createStatement()) {
            boolean hasObjects;
            try (ResultSet tables = statement.executeQuery(
                    "SELECT COUNT(*) FROM sqlite_master "
                            + "WHERE type IN ('table', 'view', 'trigger', 'index') "
                            + "AND name NOT LIKE 'sqlite_%'")) {
                hasObjects = tables.next() && tables.getLong(1) > 0;
            }
            if (hasObjects) {
                throw new DatabaseSetupException("database.setup.sqliteOnlyEmpty",
                        new IllegalStateException("SQLite database already contains tables"));
            }
            statement.execute("PRAGMA foreign_keys = ON");
        } catch (SQLException exception) {
            throw new DatabaseSetupException("database.setup.error.connection", exception);
        }

        Properties configuration = new Properties();
        configuration.setProperty("app.database.type", "sqlite");
        configuration.setProperty("spring.datasource.url", url);
        configuration.setProperty("spring.datasource.username", "");
        configuration.setProperty("spring.datasource.password", "");
        configuration.setProperty("spring.datasource.driver-class-name", "org.sqlite.JDBC");
        configuration.setProperty("spring.datasource.hikari.connection-init-sql", "PRAGMA foreign_keys=ON");
        configuration.setProperty("spring.jpa.database-platform", "org.hibernate.community.dialect.SQLiteDialect");
        configuration.setProperty("spring.sql.init.schema-locations", "classpath:schema-sqlite.sql");
        saveConfiguration(configuration);
    }

    private void saveConfiguration(Properties configuration) {
        try {
            configurationStore.save(configuration);
        } catch (IllegalStateException exception) {
            throw new DatabaseSetupException("database.setup.error.save", exception);
        }
    }

    private String normalizeMySqlUrl(String value) {
        if (value == null || !value.trim().startsWith(MYSQL_PREFIX)) {
            throw new IllegalArgumentException("A jdbc:mysql URL is required");
        }
        String url = value.trim();
        try {
            URI uri = URI.create(url.substring("jdbc:".length()));
            String databasePath = uri.getRawPath();
            if (uri.getHost() == null || databasePath == null
                    || !databasePath.matches("/[A-Za-z0-9_$-]+")) {
                throw new IllegalArgumentException("MySQL URL must include a server and database name");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid MySQL URL");
        }
        String querySeparator = url.contains("?") ? "&" : "?";
        if (url.matches("(?i).*([?&])createDatabaseIfNotExist=[^&]*.*")) {
            return url.replaceAll("(?i)([?&])createDatabaseIfNotExist=[^&]*", "$1createDatabaseIfNotExist=true");
        }
        return url + querySeparator + "createDatabaseIfNotExist=true";
    }
}
