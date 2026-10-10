package com.cogitosum.repository;

import com.cogitosum.entity.Company;

import java.util.List;

public interface CompanyMembershipRepository {

    boolean isMember(Long userId, Long companyId);

    List<Company> findCompaniesByUserId(Long userId);

    void grant(Long userId, Long companyId);
}
