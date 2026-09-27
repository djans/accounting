package com.cogitosum.repository;

import com.cogitosum.entity.FiscalYear;
import com.cogitosum.entity.FiscalYearStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface FiscalYearRepository extends JpaRepository<FiscalYear, Long> {
    List<FiscalYear> findByStatus(FiscalYearStatus status);

    // Fiscal years whose [startDate, endDate] range contains the given date.
    List<FiscalYear> findByStartDateLessThanEqualAndEndDateGreaterThanEqual(LocalDate start, LocalDate end);
    Optional<FiscalYear> findByIdAndCompanyId(Long id, Long companyId);
    List<FiscalYear> findAllByCompanyId(Long companyId);
    List<FiscalYear> findByCompanyIdAndStatus(Long companyId, FiscalYearStatus status);
}
