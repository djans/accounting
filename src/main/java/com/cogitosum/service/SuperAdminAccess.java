package com.cogitosum.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SuperAdminAccess {

    private final Set<String> superAdminEmails;

    public SuperAdminAccess(@Value("${app.security.super-admin-emails:}") String configuredEmails) {
        superAdminEmails = Arrays.stream(configuredEmails.split(","))
            .map(String::trim)
            .filter(email -> !email.isEmpty())
            .map(email -> email.toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isSuperAdmin(String email) {
        return email != null && superAdminEmails.contains(email.trim().toLowerCase(Locale.ROOT));
    }
}
