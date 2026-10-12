package com.cogitosum.service;

import com.cogitosum.entity.Company;
import com.cogitosum.entity.Customer;
import com.cogitosum.entity.UserAccount;
import com.cogitosum.repository.CompanyMembershipRepository;
import com.cogitosum.repository.CompanyRepository;
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
    @Mock
    private CompanyMembershipRepository memberships;
    @Mock
    private CompanyRepository companies;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolvesTheCompanyFromTheAuthenticatedUser() {
        Company company = company(10L);
        UserAccount user = new UserAccount();
        user.setId(1L);
        user.setCompany(company);
        user.setEnabled(true);
        when(users.findByEmail("bookkeeper@example.test")).thenReturn(Optional.of(user));
        when(memberships.findCompaniesByUserId(1L)).thenReturn(List.of(company));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("bookkeeper@example.test", "ignored", List.of()));

        CurrentCompanyContext context = context();

        assertEquals(10L, context.requireCompanyId());
    }

    @Test
    void rejectsRecordsOwnedByAnotherCompany() {
        Company currentCompany = company(10L);
        UserAccount user = new UserAccount();
        user.setId(1L);
        user.setCompany(currentCompany);
        user.setEnabled(true);
        when(users.findByEmail("viewer@example.test")).thenReturn(Optional.of(user));
        when(memberships.findCompaniesByUserId(1L)).thenReturn(List.of(currentCompany));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("viewer@example.test", "ignored", List.of()));

        Customer otherCompanyCustomer = new Customer();
        otherCompanyCustomer.setCompany(company(20L));

        CurrentCompanyContext context = context();

        assertThrows(AccessDeniedException.class, () -> context.requireCurrentCompany(otherCompanyCustomer));
    }

    @Test
    void deniesSwitchingToACompanyOutsideTheUsersMemberships() {
        UserAccount user = new UserAccount();
        user.setId(7L);
        user.setCompany(company(10L));
        user.setEnabled(true);
        when(users.findByEmail("bookkeeper@example.test")).thenReturn(Optional.of(user));
        when(memberships.isMember(7L, 99L)).thenReturn(false);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("bookkeeper@example.test", "ignored", List.of()));

        CurrentCompanyContext context = context();

        assertThrows(AccessDeniedException.class, () -> context.selectCompany(99L));
    }

    @Test
    void listsOnlyMembershipCompanies() {
        UserAccount user = new UserAccount();
        user.setId(7L);
        user.setCompany(company(10L));
        user.setEnabled(true);
        when(users.findByEmail("bookkeeper@example.test")).thenReturn(Optional.of(user));
        Company additional = company(20L);
        additional.setName("Additional");
        when(memberships.findCompaniesByUserId(7L)).thenReturn(List.of(additional));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("bookkeeper@example.test", "ignored", List.of()));

        CurrentCompanyContext context = context();

        assertEquals(List.of(20L),
            context.getAccessibleCompanies().stream().map(Company::getId).sorted().toList());
    }

    @Test
    void fallsBackToAnotherMembershipWhenThePrimaryCompanyAccessWasRevoked() {
        UserAccount user = new UserAccount();
        user.setId(7L);
        user.setCompany(company(10L));
        user.setEnabled(true);
        Company remaining = company(20L);
        when(users.findByEmail("bookkeeper@example.test")).thenReturn(Optional.of(user));
        when(memberships.findCompaniesByUserId(7L)).thenReturn(List.of(remaining));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("bookkeeper@example.test", "ignored", List.of()));

        CurrentCompanyContext context = context();

        assertEquals(20L, context.requireCompanyId());
    }

    @Test
    void deniesAccessWhenAllCompanyMembershipsHaveBeenRevoked() {
        UserAccount user = new UserAccount();
        user.setId(7L);
        user.setCompany(company(10L));
        user.setEnabled(true);
        when(users.findByEmail("bookkeeper@example.test")).thenReturn(Optional.of(user));
        when(memberships.findCompaniesByUserId(7L)).thenReturn(List.of());
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("bookkeeper@example.test", "ignored", List.of()));

        CurrentCompanyContext context = context();

        assertThrows(AccessDeniedException.class, context::requireCompany);
    }

    @Test
    void superAdminCanAccessAllCompaniesWithoutMemberships() {
        Company primary = company(10L);
        Company other = company(20L);
        UserAccount user = new UserAccount();
        user.setId(7L);
        user.setCompany(primary);
        user.setEmail("root@example.test");
        user.setEnabled(true);
        when(users.findByEmail("root@example.test")).thenReturn(Optional.of(user));
        when(companies.findAll()).thenReturn(List.of(primary, other));
        when(companies.existsById(20L)).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("root@example.test", "ignored", List.of()));

        CurrentCompanyContext context = new CurrentCompanyContext(
            users, memberships, companies, new SuperAdminAccess("ROOT@example.test"));

        assertEquals(List.of(10L, 20L),
            context.getAccessibleCompanies().stream().map(Company::getId).toList());
        assertEquals(Optional.of(com.cogitosum.entity.UserRole.ADMIN), context.currentMembershipRole());
        org.junit.jupiter.api.Assertions.assertTrue(context.canAccessCompany(20L));
    }

    @Test
    void companyAdministratorCannotAdministerAnotherMembershipCompany() {
        Company company = company(10L);
        UserAccount user = new UserAccount();
        user.setId(8L);
        user.setCompany(company);
        user.setEmail("admin@example.test");
        user.setEnabled(true);
        when(users.findByEmail("admin@example.test")).thenReturn(Optional.of(user));
        when(memberships.findRole(8L, 10L)).thenReturn(Optional.of(com.cogitosum.entity.UserRole.ADMIN));
        when(memberships.findRole(8L, 20L)).thenReturn(Optional.of(com.cogitosum.entity.UserRole.AUDITOR));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("admin@example.test", "ignored", List.of()));

        CurrentCompanyContext context = context();

        org.junit.jupiter.api.Assertions.assertTrue(context.canAdministerCompany(10L));
        org.junit.jupiter.api.Assertions.assertFalse(context.canAdministerCompany(20L));
    }

    private CurrentCompanyContext context() {
        return new CurrentCompanyContext(users, memberships, companies, new SuperAdminAccess(""));
    }

    private Company company(Long id) {
        Company company = new Company();
        company.setId(id);
        company.setName("Company " + id);
        return company;
    }
}
