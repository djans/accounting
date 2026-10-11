package com.cogitosum.setup;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Properties;

public class DatabaseConfigurationStore {

    private final Path configurationFile;

    public DatabaseConfigurationStore() {
        this(defaultConfigurationPath());
    }

    public DatabaseConfigurationStore(Path configurationFile) {
        this.configurationFile = configurationFile.toAbsolutePath().normalize();
    }

    private static Path defaultConfigurationPath() {
        String configuredPath = System.getenv("APP_DATABASE_CONFIG_PATH");
        if (configuredPath != null && !configuredPath.isBlank()) {
            return Path.of(configuredPath.trim());
        }
        return Path.of(System.getProperty("user.home"), ".accounting", "database.properties");
    }

    public boolean isConfigured() {
        return Files.isRegularFile(configurationFile);
    }

    public Properties load() {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(configurationFile)) {
            properties.load(input);
            return properties;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read the database configuration.", exception);
        }
    }

    public void save(Properties properties) {
        Path directory = configurationFile.getParent();
        Path temporaryFile = null;
        try {
            Files.createDirectories(directory);
            setOwnerOnlyPermissions(directory, true);
            temporaryFile = Files.createTempFile(directory, "database-", ".properties");
            setOwnerOnlyPermissions(temporaryFile, false);
            try (OutputStream output = Files.newOutputStream(temporaryFile)) {
                properties.store(output, "Accounting database configuration");
            }
            try {
                Files.move(temporaryFile, configurationFile,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryFile, configurationFile, StandardCopyOption.REPLACE_EXISTING);
            }
            setOwnerOnlyPermissions(configurationFile, false);
        } catch (IOException exception) {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException cleanupException) {
                    exception.addSuppressed(cleanupException);
                }
            }
            throw new IllegalStateException("Could not save the database configuration.", exception);
        }
    }

    private void setOwnerOnlyPermissions(Path path, boolean directory) throws IOException {
        try {
            Files.setPosixFilePermissions(path, directory
                    ? EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.OWNER_EXECUTE)
                    : EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
            // Windows user-profile directories inherit user-only ACLs by default.
        }
    }
}
