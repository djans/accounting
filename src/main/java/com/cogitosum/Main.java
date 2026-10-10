package com.cogitosum;

import com.cogitosum.config.DatabaseSetupApplication;
import com.cogitosum.setup.DatabaseConfigurationStore;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Main {

    public static void main(String[] args) {
        DatabaseConfigurationStore databaseConfiguration = new DatabaseConfigurationStore();
        if (!databaseConfiguration.isConfigured()
                && !databaseConfiguration.importLegacyMySqlConfigurationIfAvailable()) {
            DatabaseSetupApplication.run(args);
            return;
        }
        SpringApplication.run(Main.class, args);
    }
}
