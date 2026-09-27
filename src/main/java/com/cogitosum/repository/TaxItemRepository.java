package com.cogitosum.repository;

import com.cogitosum.entity.TaxItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaxItemRepository extends JpaRepository<TaxItem, Long> {
    List<TaxItem> findAllByOrderByCodeAsc();
    Optional<TaxItem> findByCode(String code);
    List<TaxItem> findByAgencyIdOrderByCodeAsc(Long agencyId);
    List<TaxItem> findByIsActiveOrderByCodeAsc(Boolean isActive);
    Long countByAgencyId(Long agencyId);
    Optional<TaxItem> findByIdAndCompanyId(Long id, Long companyId);
    List<TaxItem> findAllByCompanyIdOrderByCodeAsc(Long companyId);
    Optional<TaxItem> findByCompanyIdAndCode(Long companyId, String code);
    List<TaxItem> findByCompanyIdAndAgencyIdOrderByCodeAsc(Long companyId, Long agencyId);
    List<TaxItem> findByCompanyIdAndIsActiveOrderByCodeAsc(Long companyId, Boolean isActive);
    Long countByCompanyIdAndAgencyId(Long companyId, Long agencyId);
}
