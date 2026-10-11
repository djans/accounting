package com.cogitosum.service;

import com.cogitosum.entity.Company;
import com.cogitosum.entity.UserAccount;
import com.cogitosum.entity.UserRole;
import com.cogitosum.repository.CompanyMembershipRepository.Member;
import com.cogitosum.repository.CompanyMembershipRepository;
import com.cogitosum.repository.UserAccountRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class UserAccountService {

    private final UserAccountRepository userAccountRepository;
    private final CurrentCompanyContext companyContext;
    private final CompanyMembershipRepository membershipRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserShortIdService shortIdService;

    public UserAccountService(
            UserAccountRepository userAccountRepository,
            CurrentCompanyContext companyContext,
            CompanyMembershipRepository membershipRepository,
            PasswordEncoder passwordEncoder,
            UserShortIdService shortIdService) {
        this.userAccountRepository = userAccountRepository;
        this.companyContext = companyContext;
        this.membershipRepository = membershipRepository;
        this.passwordEncoder = passwordEncoder;
        this.shortIdService = shortIdService;
    }

    @Transactional(readOnly = true)
    public List<Member> getCurrentCompanyUsers() {
        requireAdministrator();
        return membershipRepository.findMembersByCompanyId(companyContext.requireCompanyId());
    }

    @Transactional(readOnly = true)
    public Long getCurrentUserId() {
        return requireAdministrator().getId();
    }

    public List<UserRole> getAvailableRoles() {
        return List.of(UserRole.ADMIN, UserRole.ACCOUNTANT, UserRole.BOOKKEEPER,
            UserRole.PAYMENT_OPERATOR, UserRole.AUDITOR);
    }

    @Transactional
    public UserAccount createAccount(String fullName, String email, String rawPassword, UserRole role) {
        requireAdministrator();
        Company company = companyContext.requireCompany();
        String normalizedName = fullName == null ? "" : fullName.trim();
        String normalizedEmail = normalizeEmail(email);
        if (normalizedName.isBlank()) {
            throw new UserAdministrationException("admin.users.error.invalidName");
        }
        validateEmail(normalizedEmail);
        validatePassword(rawPassword);
        validateAssignableRole(role);
        if (userAccountRepository.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            throw new UserAdministrationException("admin.users.error.emailInUse");
        }

        UserAccount user = new UserAccount();
        user.setCompany(company);
        user.setFullName(normalizedName);
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(UserRole.ADMIN);
        user.setEnabled(true);
        UserAccount saved = userAccountRepository.save(user);
        membershipRepository.grant(saved.getId(), company.getId(), role);
        shortIdService.getOrCreate(saved.getEmail());
        return saved;
    }

    @Transactional
    public void grantAccess(String email, UserRole role) {
        requireAdministrator();
        Long companyId = companyContext.requireCompanyId();
        String normalizedEmail = normalizeEmail(email);
        validateEmail(normalizedEmail);
        validateAssignableRole(role);
        UserAccount user = userAccountRepository.findByEmailIgnoreCase(normalizedEmail)
            .orElseThrow(() -> new UserAdministrationException("admin.users.error.accountNotFound"));
        if (!user.isEnabled()) {
            throw new UserAdministrationException("admin.users.error.accountDisabled");
        }
        if (membershipRepository.isMember(user.getId(), companyId)) {
            throw new UserAdministrationException("admin.users.error.alreadyMember");
        }
        membershipRepository.grant(user.getId(), companyId, role);
        shortIdService.getOrCreate(user.getEmail());
    }

    @Transactional
    public void updateRole(Long userId, UserRole role) {
        UserAccount administrator = requireAdministrator();
        Long companyId = companyContext.requireCompanyId();
        validateAssignableRole(role);
        if (!membershipRepository.isMember(userId, companyId)) {
            throw new UserAdministrationException("admin.users.error.notMember");
        }
        if (administrator.getId().equals(userId) && role != UserRole.ADMIN) {
            throw new UserAdministrationException("admin.users.error.cannotDemoteSelf");
        }
        UserRole currentRole = membershipRepository.findRole(userId, companyId)
            .orElseThrow(() -> new UserAdministrationException("admin.users.error.notMember"));
        if (currentRole == UserRole.ADMIN && role != UserRole.ADMIN
                && countEnabledAdministrators(companyId) <= 1) {
            throw new UserAdministrationException("admin.users.error.lastAdministrator");
        }
        membershipRepository.updateRole(userId, companyId, role);
    }

    @Transactional
    public void revokeAccess(Long userId) {
        UserAccount administrator = requireAdministrator();
        Long companyId = companyContext.requireCompanyId();
        if (administrator.getId().equals(userId)) {
            throw new UserAdministrationException("admin.users.error.cannotRemoveSelf");
        }
        if (!membershipRepository.isMember(userId, companyId)) {
            throw new UserAdministrationException("admin.users.error.notMember");
        }

        var target = membershipRepository.findMembersByCompanyId(companyId).stream()
            .filter(member -> member.userId().equals(userId))
            .findFirst()
            .orElseThrow(() -> new UserAdministrationException("admin.users.error.accountNotFound"));
        if (target.enabled() && target.role() == UserRole.ADMIN && countEnabledAdministrators(companyId) <= 1) {
            throw new UserAdministrationException("admin.users.error.lastAdministrator");
        }
        membershipRepository.revoke(userId, companyId);
    }

    private long countEnabledAdministrators(Long companyId) {
        return membershipRepository.findMembersByCompanyId(companyId).stream()
            .filter(member -> member.enabled() && member.role() == UserRole.ADMIN)
            .count();
    }

    private UserAccount requireAdministrator() {
        UserAccount currentUser = companyContext.currentUser();
        if (companyContext.requireCurrentRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Administrator role required");
        }
        return currentUser;
    }

    private static void validateAssignableRole(UserRole role) {
        if (role == null) {
            throw new UserAdministrationException("admin.users.error.invalidRole");
        }
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static void validateEmail(String email) {
        if (!AccountCredentialsService.isValidEmailAddress(email)) {
            throw new UserAdministrationException("admin.users.error.invalidEmail");
        }
    }

    private static void validatePassword(String password) {
        if (!AccountCredentialsService.isValidPassword(password)) {
            throw new UserAdministrationException(password == null
                    || password.length() < AccountCredentialsService.MINIMUM_PASSWORD_LENGTH
                ? "admin.users.error.passwordTooShort"
                : "admin.users.error.passwordComplexity");
        }
    }
}
