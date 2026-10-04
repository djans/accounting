package com.cogitosum.repository;

import com.cogitosum.entity.WrittenCheque;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.List;

public interface WrittenChequeRepository extends JpaRepository<WrittenCheque, Long> {
    Optional<WrittenCheque> findTopByOrderByIdDesc();
    Optional<WrittenCheque> findByIdAndCompanyId(Long id, Long companyId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WrittenCheque> findLockedByIdAndCompanyId(Long id, Long companyId);
    Optional<WrittenCheque> findTopByCompanyIdOrderByIdDesc(Long companyId);
    List<WrittenCheque> findAllByCompanyIdOrderByChequeDateDescIdDesc(Long companyId);
    List<WrittenCheque> findByCompanyIdAndChequeDateBetweenOrderByChequeDateDescIdDesc(
            Long companyId, LocalDate startDate, LocalDate endDate);
}
