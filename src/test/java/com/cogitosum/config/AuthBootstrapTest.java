package com.cogitosum.config;

import com.cogitosum.entity.Company;
import com.cogitosum.entity.UserAccount;
import com.cogitosum.repository.CompanyRepository;
import com.cogitosum.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthBootstrapTest {

    @Mock private CompanyRepository companyRepository;
    @Mock private UserAccountRepository userAccountRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @Test
    void createsRequestedInitialAdminWhenNoAccountsExist() {
        AuthBootstrap bootstrap = new AuthBootstrap(companyRepository, userAccountRepository,
                passwordEncoder, "root@example.com", "password");
        when(userAccountRepository.findByEmail("root@example.com")).thenReturn(Optional.empty());
        when(userAccountRepository.count()).thenReturn(0L);
        when(companyRepository.findByEmail("root@example.com")).thenReturn(Optional.empty());
        when(companyRepository.save(any(Company.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordEncoder.encode("password")).thenReturn("encoded-default");
        when(userAccountRepository.save(any(UserAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        bootstrap.run();

        ArgumentCaptor<UserAccount> savedAdmin = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountRepository).save(savedAdmin.capture());
        assertEquals("root@example.com", savedAdmin.getValue().getEmail());
        assertEquals("encoded-default", savedAdmin.getValue().getPasswordHash());
        assertEquals("Example Company", savedAdmin.getValue().getCompany().getName());
    }

    @Test
    void doesNotRecreateDefaultAdminAfterExistingUserChangesTheirEmail() {
        AuthBootstrap bootstrap = new AuthBootstrap(companyRepository, userAccountRepository,
                passwordEncoder, "root@example.com", "password");
        when(userAccountRepository.findByEmail("root@example.com")).thenReturn(Optional.empty());
        when(userAccountRepository.count()).thenReturn(1L);
        when(companyRepository.count()).thenReturn(1L);

        bootstrap.run();

        verify(userAccountRepository, org.mockito.Mockito.never()).save(any(UserAccount.class));
        verify(companyRepository, org.mockito.Mockito.never()).save(any(Company.class));
    }

    @Test
    void createsExampleCompanyWhenUsersExistButNoCompanyDoes() {
        AuthBootstrap bootstrap = new AuthBootstrap(companyRepository, userAccountRepository,
                passwordEncoder, "", "");
        when(userAccountRepository.count()).thenReturn(1L);
        when(companyRepository.count()).thenReturn(0L);
        when(companyRepository.save(any(Company.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        bootstrap.run();

        ArgumentCaptor<Company> savedCompany = ArgumentCaptor.forClass(Company.class);
        verify(companyRepository).save(savedCompany.capture());
        assertEquals("Example Company", savedCompany.getValue().getName());
        assertEquals("example@example.com", savedCompany.getValue().getEmail());
    }
}
