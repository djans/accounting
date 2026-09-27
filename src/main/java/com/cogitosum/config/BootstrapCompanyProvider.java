package com.cogitosum.config;

import com.cogitosum.entity.Company;
import com.cogitosum.repository.UserAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Provides the explicitly configured bootstrap company to startup-only seeders.
 * Request-scoped company resolution must use CurrentCompanyContext instead.
 */
@Component
public class BootstrapCompanyProvider {

    private final UserAccountRepository userAccountRepository;
    private final String bootstrapAdminEmail;

    public BootstrapCompanyProvider(UserAccountRepository userAccountRepository,
                                   @Value("${app.bootstrap.admin.email:}") String bootstrapAdminEmail) {
        this.userAccountRepository = userAccountRepository;
        this.bootstrapAdminEmail = bootstrapAdminEmail;
    }

    public Company requireBootstrapCompany() {
        if (bootstrapAdminEmail.isBlank()) {
            throw new IllegalStateException("Reference-data seeding requires APP_BOOTSTRAP_ADMIN_EMAIL.");
        }
        return userAccountRepository.findByEmail(bootstrapAdminEmail)
            .filter(user -> user.isEnabled() && user.getCompany() != null)
            .map(user -> user.getCompany())
            .orElseThrow(() -> new IllegalStateException("The bootstrap administrator has no active company."));
    }
}
