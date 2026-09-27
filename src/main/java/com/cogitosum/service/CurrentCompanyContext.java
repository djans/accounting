package com.cogitosum.service;

import com.cogitosum.entity.Company;
import com.cogitosum.entity.CompanyOwned;
import com.cogitosum.entity.UserAccount;
import com.cogitosum.repository.UserAccountRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the tenant from the authenticated application user, never from
 * request data.
 */
@Component
public class CurrentCompanyContext {

    private final UserAccountRepository userAccountRepository;

    public CurrentCompanyContext(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional(readOnly = true)
    public Company requireCompany() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            throw new AccessDeniedException("An authenticated company user is required");
        }

        UserAccount user = userAccountRepository.findByEmail(authentication.getName())
            .filter(UserAccount::isEnabled)
            .orElseThrow(() -> new AccessDeniedException("The authenticated user is not active"));
        if (user.getCompany() == null) {
            throw new AccessDeniedException("The authenticated user is not assigned to a company");
        }
        return user.getCompany();
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
