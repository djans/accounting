package com.cogitosum.config;

import com.cogitosum.entity.Company;
import com.cogitosum.entity.UserAccount;
import com.cogitosum.entity.UserRole;
import com.cogitosum.repository.CompanyRepository;
import com.cogitosum.repository.UserAccountRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AuthBootstrap implements CommandLineRunner {

    private final CompanyRepository companyRepository;
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthBootstrap(CompanyRepository companyRepository,
                        UserAccountRepository userAccountRepository,
                        PasswordEncoder passwordEncoder) {
        this.companyRepository = companyRepository;
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userAccountRepository.count() == 0) {
            Company company = companyRepository.findByEmail("admin@accounting.local").orElseGet(() -> {
                Company newCompany = new Company();
                newCompany.setName("Canadian Accounting Demo");
                newCompany.setLegalName("Canadian Accounting Demo Ltd.");
                newCompany.setEmail("admin@accounting.local");
                newCompany.setPhone("(416) 555-0101");
                newCompany.setAddress("100 King Street West");
                newCompany.setCity("Toronto");
                newCompany.setProvince("ON");
                newCompany.setPostalCode("M5H 3T3");
                newCompany.setCountry("Canada");
                newCompany.setBusinessNumber("123456789RT0001");
                newCompany.setGstNumber("123456789RT0001");
                newCompany.setQstNumber("123456789QZ0001");
                newCompany.setCurrency("CAD");
                newCompany.setDefaultTaxProvince("ON");
                newCompany.setFiscalYearStartMonth(1);
                return companyRepository.save(newCompany);
            });

            UserAccount admin = new UserAccount();
            admin.setCompany(company);
            admin.setFullName("Demo Administrator");
            admin.setEmail("admin@accounting.local");
            admin.setPasswordHash(passwordEncoder.encode("admin123"));
            admin.setRole(UserRole.ADMIN);
            admin.setEnabled(true);
            userAccountRepository.save(admin);
        }
    }
}
