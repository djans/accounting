package com.cogitosum.repository;

import com.cogitosum.entity.Company;
import com.cogitosum.entity.UserRole;

import java.util.List;
import java.util.Optional;

public interface CompanyMembershipRepository {

    boolean isMember(Long userId, Long companyId);

    List<Company> findCompaniesByUserId(Long userId);

    Optional<UserRole> findRole(Long userId, Long companyId);

    List<Member> findMembersByCompanyId(Long companyId);

    void grant(Long userId, Long companyId, UserRole role);

    void updateRole(Long userId, Long companyId, UserRole role);

    void revoke(Long userId, Long companyId);

    record Member(
            Long userId,
            String fullName,
            String email,
            boolean enabled,
            UserRole role,
            String shortId) {
    }
}
