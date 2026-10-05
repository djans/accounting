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

import java.util.Optional;

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
        Optional<UserAccount> existingAdmin = bootstrapAdminEmail.isBlank()
                ? Optional.empty()
                : userAccountRepository.findByEmail(bootstrapAdminEmail);
        Company exampleCompany = companyRepository.count() == 0 ? createExampleCompany() : null;
        if (userAccountRepository.count() > 0) {
            if (existingAdmin.isPresent() && existingAdmin.get().getCompany() == null) {
                UserAccount admin = existingAdmin.get();
                Company company = exampleCompany == null ? findOrCreateBootstrapCompany() : exampleCompany;
                admin.setCompany(company);
                admin.setRole(UserRole.ADMIN);
                admin.setEnabled(true);
                userAccountRepository.save(admin);
            }
            return;
        }

        if (bootstrapAdminEmail.isBlank() || bootstrapAdminPassword.isBlank()) {
            if (userAccountRepository.count() == 0) {
                throw new IllegalStateException(
                    "No admin account exists yet, and no bootstrap credentials were supplied.\n"
                    + "Set APP_BOOTSTRAP_ADMIN_EMAIL and APP_BOOTSTRAP_ADMIN_PASSWORD before starting the app.");
            }
            return;
        }

        Company company = exampleCompany == null ? findOrCreateBootstrapCompany() : exampleCompany;
        UserAccount admin = new UserAccount();
        admin.setCompany(company);
        admin.setFullName("Administrator");
        admin.setEmail(bootstrapAdminEmail);
        admin.setPasswordHash(passwordEncoder.encode(bootstrapAdminPassword));
        admin.setRole(UserRole.ADMIN);
        admin.setEnabled(true);
        userAccountRepository.save(admin);
        log.info("Bootstrap administrator created: {} (company: {}).",
                bootstrapAdminEmail, company.getName());
    }

    private Company createExampleCompany() {
        Company company = new Company();
        company.setName("Example Company");
        company.setLegalName("Example Company");
        company.setEmail(bootstrapAdminEmail.isBlank() ? "example@example.com" : bootstrapAdminEmail);
        company.setPhone("Not configured");
        company.setAddress("Not configured");
        company.setCity("Not configured");
        company.setProvince("ON");
        company.setPostalCode("A1A 1A1");
        company.setCountry("Canada");
        company.setCurrency("CAD");
        company.setDefaultTaxProvince("ON");
        company.setFiscalYearStartMonth(1);
        return companyRepository.save(company);
    }

    private Company findOrCreateBootstrapCompany() {
        String companyEmail = bootstrapAdminEmail.isBlank() ? "example@example.com" : bootstrapAdminEmail;
        return companyRepository.findByEmail(companyEmail)
                .orElseGet(this::createExampleCompany);
    }
}
