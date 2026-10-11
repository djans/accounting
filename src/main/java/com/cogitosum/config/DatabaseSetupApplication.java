package com.cogitosum.config;

import com.cogitosum.setup.DatabaseConfigurationStore;
import com.cogitosum.setup.DatabaseSetupService;
import com.cogitosum.web.DatabaseSetupWebController;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.bootstrap.mode", havingValue = "database-setup")
@EnableAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
})
@Import({DatabaseSetupWebController.class, DatabaseSetupSecurityConfiguration.class})
public class DatabaseSetupApplication {

    public static void run(String... args) {
        String bindAddress = System.getenv().getOrDefault("APP_SETUP_BIND_ADDRESS", "127.0.0.1");
        String setupToken = System.getenv("APP_SETUP_TOKEN");
        if (!"127.0.0.1".equals(bindAddress) && (setupToken == null || setupToken.isBlank())) {
            throw new IllegalStateException(
                    "Set APP_SETUP_TOKEN before exposing the initial database setup server.");
        }

        SpringApplication application = new SpringApplication(DatabaseSetupApplication.class);
        application.setDefaultProperties(Map.of(
                "app.bootstrap.mode", "database-setup",
                "server.port", "8080",
                "app.setup.token", setupToken == null ? "" : setupToken,
                "spring.thymeleaf.cache", "false"));
        application.addInitializers(context -> context.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("databaseSetupLoopback",
                        Map.of("server.address", bindAddress))));
        application.run(args);
    }

    @Bean
    public DatabaseConfigurationStore databaseConfigurationStore() {
        return new DatabaseConfigurationStore();
    }

    @Bean
    public DatabaseSetupService databaseSetupService(DatabaseConfigurationStore store) {
        return new DatabaseSetupService(store);
    }
}
