package com.cogitosum.config;

import com.cogitosum.service.UserShortIdService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(3)
public class UserShortIdMigration implements CommandLineRunner {

    private final UserShortIdService shortIdService;

    public UserShortIdMigration(UserShortIdService shortIdService) {
        this.shortIdService = shortIdService;
    }

    @Override
    public void run(String... args) {
        shortIdService.assignMissingShortIds();
    }
}
