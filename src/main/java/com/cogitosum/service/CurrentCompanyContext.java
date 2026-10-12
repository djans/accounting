package com.cogitosum.service;

import com.cogitosum.entity.Company;
import com.cogitosum.entity.CompanyOwned;
import com.cogitosum.entity.UserAccount;
import com.cogitosum.entity.UserRole;
import com.cogitosum.repository.CompanyMembershipRepository;
import com.cogitosum.repository.CompanyRepository;
import com.cogitosum.repository.UserAccountRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

/**
 * Resolves the tenant from the authenticated application user, never from
 * request data.
 */
@Component
public class CurrentCompanyContext {

    public static final String SELECTED_COMPANY_SESSION_KEY = "selectedCompanyId";

    private final UserAccountRepository userAccountRepository;
    private final CompanyMembershipRepository membershipRepository;
    private final CompanyRepository companyRepository;
    private final SuperAdminAccess superAdminAccess;

    public CurrentCompanyContext(
            UserAccountRepository userAccountRepository,
            CompanyMembershipRepository membershipRepository,
            CompanyRepository companyRepository,
            SuperAdminAccess superAdminAccess) {
        this.userAccountRepository = userAccountRepository;
        this.membershipRepository = membershipRepository;
        this.companyRepository = companyRepository;
        this.superAdminAccess = superAdminAccess;
    }

    @Transactional(readOnly = true)
    public Company requireCompany() {
        UserAccount user = currentUser();
        return companyForUser(user)
            .orElseThrow(() -> new AccessDeniedException("The authenticated user is not a member of a company"));
    }

    public void selectCompany(Long companyId) {
        UserAccount user = currentUser();
        if (!(isSuperAdmin(user) ? companyRepository.existsById(companyId)
                : membershipRepository.isMember(user.getId(), companyId))) {
            throw new AccessDeniedException("The requested company is not available to this user");
        }
        HttpSession session = currentSession(true);
        if (session == null) {
            throw new IllegalStateException("Company selection requires an HTTP request");
        }
        session.setAttribute(SELECTED_COMPANY_SESSION_KEY, companyId);
    }

    public List<Company> getAccessibleCompanies() {
        UserAccount user = currentUser();
        LinkedHashMap<Long, Company> companies = new LinkedHashMap<>();
        accessibleCompanies(user)
            .forEach(company -> companies.put(company.getId(), company));
        return companies.values().stream()
            .sorted(java.util.Comparator.comparing(Company::getName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Company::getId))
            .toList();
    }

    public boolean canAccessCompany(Long companyId) {
        UserAccount user = currentUser();
        return isSuperAdmin(user)
            ? companyRepository.existsById(companyId)
            : membershipRepository.isMember(user.getId(), companyId);
    }

    public boolean isSuperAdmin() {
        return superAdminAccess.isSuperAdmin(currentUser().getEmail());
    }

    public void requireSuperAdmin() {
        if (!isSuperAdmin()) {
            throw new AccessDeniedException("Super administrator role required");
        }
    }

    public boolean canAdministerCompany(Long companyId) {
        UserAccount user = currentUser();
        return isSuperAdmin(user)
            || membershipRepository.findRole(user.getId(), companyId)
                .filter(role -> role == UserRole.ADMIN)
                .isPresent();
    }

    public UserAccount currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            throw new AccessDeniedException("An authenticated company user is required");
        }
        return userAccountRepository.findByEmail(authentication.getName())
            .filter(UserAccount::isEnabled)
            .orElseThrow(() -> new AccessDeniedException("The authenticated user is not active"));
    }

    @Transactional(readOnly = true)
    public Optional<UserRole> currentMembershipRole() {
        UserAccount user = currentUser();
        return companyForUser(user)
            .flatMap(company -> isSuperAdmin(user)
                ? Optional.of(UserRole.ADMIN)
                : membershipRepository.findRole(user.getId(), company.getId()));
    }

    public UserRole requireCurrentRole() {
        return currentMembershipRole()
            .orElseThrow(() -> new AccessDeniedException("The authenticated user has no company role"));
    }

    private Optional<Company> companyForUser(UserAccount user) {
        List<Company> accessibleCompanies = accessibleCompanies(user);
        if (accessibleCompanies.isEmpty()) {
            return Optional.empty();
        }
        HttpSession session = currentSession(false);
        if (session != null) {
            Object selected = session.getAttribute(SELECTED_COMPANY_SESSION_KEY);
            if (selected instanceof Number selectedId) {
                Optional<Company> selectedCompany = accessibleCompanies.stream()
                    .filter(company -> company.getId().equals(selectedId.longValue()))
                    .findFirst();
                if (selectedCompany.isPresent()) {
                    return selectedCompany;
                }
                session.removeAttribute(SELECTED_COMPANY_SESSION_KEY);
            }
        }
        return accessibleCompanies.stream()
            .filter(company -> company.getId().equals(user.getCompany().getId()))
            .findFirst()
            .or(() -> accessibleCompanies.stream().findFirst());
    }

    private List<Company> accessibleCompanies(UserAccount user) {
        return isSuperAdmin(user)
            ? companyRepository.findAll()
            : membershipRepository.findCompaniesByUserId(user.getId());
    }

    private boolean isSuperAdmin(UserAccount user) {
        return superAdminAccess.isSuperAdmin(user.getEmail());
    }

    private HttpSession currentSession(boolean create) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes servletAttributes
            ? servletAttributes.getRequest().getSession(create) : null;
    }

    public Long requireCompanyId() {
        return requireCompany().getId();
    }

    public <T extends CompanyOwned> T assignCurrentCompany(T record) {
        record.setCompany(requireCompany());
        return record;
    }

    public void requireCurrentCompany(CompanyOwned record) {
        if (record == null || record.getCompany() == null
                || !requireCompanyId().equals(record.getCompany().getId())) {
            throw new AccessDeniedException("The requested record does not belong to your company");
        }
    }

    public void requireCurrentCompany(Company company) {
        if (company == null || !requireCompanyId().equals(company.getId())) {
            throw new AccessDeniedException("The requested company does not belong to the authenticated user");
        }
    }
}
