package com.cogitosum.repository;

import com.cogitosum.entity.TaxFilingPeriod;
import com.cogitosum.entity.TaxFilingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TaxFilingPeriodRepository extends JpaRepository<TaxFilingPeriod, Long> {
    List<TaxFilingPeriod> findByAgencyId(Long agencyId);
    List<TaxFilingPeriod> findByStatus(TaxFilingStatus status);
    List<TaxFilingPeriod> findByAgencyIdAndStatus(Long agencyId, TaxFilingStatus status);
    List<TaxFilingPeriod> findByPeriodStartBetween(LocalDate from, LocalDate to);
    Long countByAgencyId(Long agencyId);
    Optional<TaxFilingPeriod> findByIdAndCompanyId(Long id, Long companyId);
    List<TaxFilingPeriod> findByCompanyIdAndAgencyId(Long companyId, Long agencyId);
    List<TaxFilingPeriod> findAllByCompanyId(Long companyId);
    List<TaxFilingPeriod> findByCompanyIdAndStatus(Long companyId, TaxFilingStatus status);
    Long countByCompanyIdAndAgencyId(Long companyId, Long agencyId);
}
