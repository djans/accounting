package com.cogitosum.config;

import com.cogitosum.entity.Company;
import com.cogitosum.entity.UserAccount;
import com.cogitosum.entity.UserRole;
import com.cogitosum.repository.CompanyRepository;
import com.cogitosum.repository.UserAccountRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Order(0)
public class AuthBootstrap implements CommandLineRunner {

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
                    "Set APP_BOOTSTRAP_ADMIN_EMAIL and APP_BOOTSTRAP_ADMIN_PASSWORD before the first startup.");
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
        } else if (admin.getCompany() == null) {
            admin.setCompany(company);
            admin.setRole(UserRole.ADMIN);
            admin.setEnabled(true);
            userAccountRepository.save(admin);
        }
    }
}
