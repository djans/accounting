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

    public CurrentCompanyContext(
            UserAccountRepository userAccountRepository,
            CompanyMembershipRepository membershipRepository,
            CompanyRepository companyRepository) {
        this.userAccountRepository = userAccountRepository;
        this.membershipRepository = membershipRepository;
        this.companyRepository = companyRepository;
    }

    @Transactional(readOnly = true)
    public Company requireCompany() {
        UserAccount user = currentUser();
        return companyForUser(user)
            .orElseThrow(() -> new AccessDeniedException("The authenticated user is not a member of a company"));
    }

    public void selectCompany(Long companyId) {
        UserAccount user = currentUser();
        if (!membershipRepository.isMember(user.getId(), companyId)) {
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
        membershipRepository.findCompaniesByUserId(user.getId())
            .forEach(company -> companies.put(company.getId(), company));
        return companies.values().stream()
            .sorted(java.util.Comparator.comparing(Company::getName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Company::getId))
            .toList();
    }

    public boolean canAccessCompany(Long companyId) {
        UserAccount user = currentUser();
        return membershipRepository.isMember(user.getId(), companyId);
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
            .flatMap(company -> membershipRepository.findRole(user.getId(), company.getId()));
    }

    public UserRole requireCurrentRole() {
        return currentMembershipRole()
            .orElseThrow(() -> new AccessDeniedException("The authenticated user has no company role"));
    }

    private Optional<Company> companyForUser(UserAccount user) {
        List<Company> accessibleCompanies = membershipRepository.findCompaniesByUserId(user.getId());
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
