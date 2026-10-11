package com.cogitosum.service;

import com.cogitosum.entity.Company;
import com.cogitosum.entity.UserAccount;
import com.cogitosum.entity.UserRole;
import com.cogitosum.repository.CompanyMembershipRepository;
import com.cogitosum.repository.CompanyMembershipRepository.Member;
import com.cogitosum.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccountServiceTest {

    @Mock
    private UserAccountRepository users;
    @Mock
    private CurrentCompanyContext companyContext;
    @Mock
    private CompanyMembershipRepository memberships;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserShortIdService shortIdService;

    @Test
    void createsAccountsWithTheSelectedCompanyRoleAndGrantsMembership() {
        Company company = company(10L);
        UserAccount admin = user(1L);
        when(companyContext.currentUser()).thenReturn(admin);
        when(companyContext.requireCurrentRole()).thenReturn(UserRole.ADMIN);
        when(companyContext.requireCompany()).thenReturn(company);
        when(users.findByEmailIgnoreCase("new@example.test")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("a-long-initial-password1")).thenReturn("encoded");
        when(users.save(any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount saved = invocation.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        UserAccount created = service().createAccount(
            "  New User ", "New@Example.Test ", "a-long-initial-password1", UserRole.ACCOUNTANT);

        assertEquals("New User", created.getFullName());
        assertEquals("new@example.test", created.getEmail());
        assertEquals(UserRole.ADMIN, created.getRole());
        assertEquals("encoded", created.getPasswordHash());
        verify(memberships).grant(2L, 10L, UserRole.ACCOUNTANT);
        verify(shortIdService).getOrCreate("new@example.test");
    }

    @Test
    void preventsTheLastEnabledCompanyAdministratorFromBeingRemoved() {
        UserAccount administrator = user(1L);
        when(companyContext.currentUser()).thenReturn(administrator);
        when(companyContext.requireCurrentRole()).thenReturn(UserRole.ADMIN);
        when(companyContext.requireCompanyId()).thenReturn(10L);
        when(memberships.isMember(2L, 10L)).thenReturn(true);
        when(memberships.findMembersByCompanyId(10L)).thenReturn(List.of(
            new Member(2L, "Only Admin", "admin@example.test", true, UserRole.ADMIN, "ADMI001")));

        UserAdministrationException exception = assertThrows(
            UserAdministrationException.class, () -> service().revokeAccess(2L));

        assertEquals("admin.users.error.lastAdministrator", exception.getMessage());
    }

    @Test
    void revokesOnlyCompanyMembershipWithoutDeletingTheGlobalAccount() {
        UserAccount administrator = user(1L);
        when(companyContext.currentUser()).thenReturn(administrator);
        when(companyContext.requireCurrentRole()).thenReturn(UserRole.ADMIN);
        when(companyContext.requireCompanyId()).thenReturn(10L);
        when(memberships.isMember(2L, 10L)).thenReturn(true);
        when(memberships.findMembersByCompanyId(10L)).thenReturn(List.of(
            new Member(2L, "Viewer", "viewer@example.test", true, UserRole.AUDITOR, "VIEW001")));

        service().revokeAccess(2L);

        verify(memberships).revoke(2L, 10L);
        verify(users, never()).delete(any(UserAccount.class));
    }

    @Test
    void deniesNonAdministratorsFromManagingCompanyUsers() {
        when(companyContext.currentUser()).thenReturn(user(3L));
        when(companyContext.requireCurrentRole()).thenReturn(UserRole.ACCOUNTANT);

        assertThrows(AccessDeniedException.class, () -> service().getCurrentCompanyUsers());
    }

    @Test
    void rejectsPasswordsWithoutAllRequiredCharacterClasses() {
        when(companyContext.currentUser()).thenReturn(user(1L));
        when(companyContext.requireCurrentRole()).thenReturn(UserRole.ADMIN);
        when(companyContext.requireCompany()).thenReturn(company(10L));

        UserAdministrationException exception = assertThrows(
            UserAdministrationException.class,
            () -> service().createAccount("New User", "new@example.test", "longpasswordonly", UserRole.AUDITOR));

        assertEquals("admin.users.error.passwordComplexity", exception.getMessage());
    }

    @Test
    void updatesTheRoleOnOnlyTheSelectedCompanyMembership() {
        UserAccount administrator = user(1L);
        when(companyContext.currentUser()).thenReturn(administrator);
        when(companyContext.requireCurrentRole()).thenReturn(UserRole.ADMIN);
        when(companyContext.requireCompanyId()).thenReturn(10L);
        when(memberships.isMember(2L, 10L)).thenReturn(true);
        when(memberships.findRole(2L, 10L)).thenReturn(Optional.of(UserRole.AUDITOR));

        service().updateRole(2L, UserRole.PAYMENT_OPERATOR);

        verify(memberships).updateRole(2L, 10L, UserRole.PAYMENT_OPERATOR);
    }

    private UserAccountService service() {
        return new UserAccountService(users, companyContext, memberships, passwordEncoder, shortIdService);
    }

    private static Company company(Long id) {
        Company company = new Company();
        company.setId(id);
        company.setName("Company " + id);
        return company;
    }

    private static UserAccount user(Long id) {
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setCompany(company(10L));
        user.setEnabled(true);
        return user;
    }

}
