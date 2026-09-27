package com.cogitosum.repository;

import com.cogitosum.entity.TaxAgency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaxAgencyRepository extends JpaRepository<TaxAgency, Long> {
    List<TaxAgency> findAllByOrderByCodeAsc();
    List<TaxAgency> findByIsActiveOrderByCodeAsc(Boolean isActive);
    Optional<TaxAgency> findByCode(String code);
    Long countByIsActiveTrue();
    Optional<TaxAgency> findByIdAndCompanyId(Long id, Long companyId);
    List<TaxAgency> findAllByCompanyIdOrderByCodeAsc(Long companyId);
    List<TaxAgency> findByCompanyIdAndIsActiveOrderByCodeAsc(Long companyId, Boolean isActive);
    Optional<TaxAgency> findByCompanyIdAndCode(Long companyId, String code);
    Long countByCompanyIdAndIsActiveTrue(Long companyId);
}
