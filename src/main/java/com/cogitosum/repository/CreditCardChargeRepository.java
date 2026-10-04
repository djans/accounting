package com.cogitosum.repository;

import com.cogitosum.entity.CreditCardCharge;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CreditCardChargeRepository extends JpaRepository<CreditCardCharge, Long> {
    Optional<CreditCardCharge> findByIdAndCompanyId(Long id, Long companyId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CreditCardCharge> findLockedByIdAndCompanyId(Long id, Long companyId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select charge from CreditCardCharge charge "
            + "where charge.id in :ids and charge.company.id = :companyId order by charge.id")
    List<CreditCardCharge> findAllLockedByIdInAndCompanyId(
            @Param("ids") Collection<Long> ids, @Param("companyId") Long companyId);
    List<CreditCardCharge> findAllByCompanyIdOrderByChargeDateDescIdDesc(Long companyId);
    List<CreditCardCharge> findByCompanyIdAndChargeDateBetweenOrderByChargeDateDescIdDesc(
            Long companyId, LocalDate startDate, LocalDate endDate);
}
