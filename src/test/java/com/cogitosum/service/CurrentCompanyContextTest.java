package com.cogitosum.service;

import com.cogitosum.entity.Company;
import com.cogitosum.entity.Customer;
import com.cogitosum.entity.UserAccount;
import com.cogitosum.repository.UserAccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentCompanyContextTest {

    @Mock
    private UserAccountRepository users;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolvesTheCompanyFromTheAuthenticatedUser() {
        Company company = company(10L);
        UserAccount user = new UserAccount();
        user.setCompany(company);
        user.setEnabled(true);
        when(users.findByEmail("bookkeeper@example.test")).thenReturn(Optional.of(user));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("bookkeeper@example.test", "ignored", List.of()));

        CurrentCompanyContext context = new CurrentCompanyContext(users);

        assertEquals(10L, context.requireCompanyId());
    }

    @Test
    void rejectsRecordsOwnedByAnotherCompany() {
        Company currentCompany = company(10L);
        UserAccount user = new UserAccount();
        user.setCompany(currentCompany);
        user.setEnabled(true);
        when(users.findByEmail("viewer@example.test")).thenReturn(Optional.of(user));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("viewer@example.test", "ignored", List.of()));

        Customer otherCompanyCustomer = new Customer();
        otherCompanyCustomer.setCompany(company(20L));

        CurrentCompanyContext context = new CurrentCompanyContext(users);

        assertThrows(AccessDeniedException.class, () -> context.requireCurrentCompany(otherCompanyCustomer));
    }

    private Company company(Long id) {
        Company company = new Company();
        company.setId(id);
        return company;
    }
}
