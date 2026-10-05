package com.cogitosum.config;

import com.cogitosum.entity.Company;
import com.cogitosum.entity.UserRole;
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
            throw new IllegalStateException(
                "Reference-data seeding (tax codes, vendors, etc.) needs a bootstrap administrator to attach "
                + "the seeded data to.\n"
                + "Set these environment variables before starting the app:\n"
                + "  APP_BOOTSTRAP_ADMIN_EMAIL=you@example.com\n"
                + "  APP_BOOTSTRAP_ADMIN_PASSWORD=<a strong password>\n"
                + "Then restart. If you don't need reference-data seeding yet, disable it instead via\n"
                + "  APP_SEED_REFERENCE_DATA_ENABLED=false (and APP_SEED_VENDOR_DATA_ENABLED=false).");
        }
        return userAccountRepository.findByEmail(bootstrapAdminEmail)
            .filter(user -> user.isEnabled() && user.getCompany() != null)
            .map(user -> user.getCompany())
            .or(() -> userAccountRepository.findFirstByRoleOrderByIdAsc(UserRole.ADMIN)
                    .filter(user -> user.isEnabled() && user.getCompany() != null)
                    .map(user -> user.getCompany()))
            .orElseThrow(() -> new IllegalStateException(
                "No enabled administrator with an assigned company is available for reference-data seeding."));
    }
}
