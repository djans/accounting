package com.cogitosum.config;

import com.cogitosum.entity.Company;
import com.cogitosum.entity.UserAccount;
import com.cogitosum.entity.UserRole;
import com.cogitosum.repository.CompanyRepository;
import com.cogitosum.repository.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Order(0)
public class AuthBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AuthBootstrap.class);

    private final CompanyRepository companyRepository;
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final String bootstrapAdminEmail;
    private final String bootstrapAdminPassword;

    public AuthBootstrap(CompanyRepository companyRepository,
                        UserAccountRepository userAccountRepository,
                        PasswordEncoder passwordEncoder,
                        @Value("${app.bootstrap.admin.email:}") String bootstrapAdminEmail,
                        @Value("${app.bootstrap.admin.password:}") String bootstrapAdminPassword) {
        this.companyRepository = companyRepository;
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.bootstrapAdminEmail = bootstrapAdminEmail;
        this.bootstrapAdminPassword = bootstrapAdminPassword;
    }

    @Override
    public void run(String... args) {
        if (bootstrapAdminEmail.isBlank() || bootstrapAdminPassword.isBlank()) {
            if (userAccountRepository.count() == 0) {
                throw new IllegalStateException(
                    "No admin account exists yet, and no bootstrap credentials were supplied.\n"
                    + "To create the first administrator, set these environment variables before starting the app:\n"
                    + "  APP_BOOTSTRAP_ADMIN_EMAIL=you@example.com\n"
                    + "  APP_BOOTSTRAP_ADMIN_PASSWORD=<a strong password>\n"
                    + "Then restart the application. These variables are only needed once, to seed the initial\n"
                    + "administrator account; they can be removed afterwards.");
            }
            return;
        }
        Company company = companyRepository.findByEmail(bootstrapAdminEmail).orElseGet(() -> {
            Company newCompany = new Company();
            newCompany.setName("Accounting Company");
            newCompany.setLegalName("Accounting Company");
            newCompany.setEmail(bootstrapAdminEmail);
            newCompany.setPhone("Not configured");
            newCompany.setAddress("Not configured");
            newCompany.setCity("Not configured");
            newCompany.setProvince("ON");
            newCompany.setPostalCode("A1A 1A1");
            newCompany.setCountry("Canada");
            newCompany.setCurrency("CAD");
            newCompany.setDefaultTaxProvince("ON");
            newCompany.setFiscalYearStartMonth(1);
            return companyRepository.save(newCompany);
        });

        UserAccount admin = userAccountRepository.findByEmail(bootstrapAdminEmail).orElse(null);
        if (admin == null) {
            admin = new UserAccount();
            admin.setCompany(company);
            admin.setFullName("Administrator");
            admin.setEmail(bootstrapAdminEmail);
            admin.setPasswordHash(passwordEncoder.encode(bootstrapAdminPassword));
            admin.setRole(UserRole.ADMIN);
            admin.setEnabled(true);
            userAccountRepository.save(admin);
            log.info("Bootstrap administrator created: {} (company: {}). "
                + "You can now remove APP_BOOTSTRAP_ADMIN_EMAIL/APP_BOOTSTRAP_ADMIN_PASSWORD.",
                bootstrapAdminEmail, company.getName());
        } else if (admin.getCompany() == null) {
            admin.setCompany(company);
            admin.setRole(UserRole.ADMIN);
            admin.setEnabled(true);
            userAccountRepository.save(admin);
        }
    }
}
