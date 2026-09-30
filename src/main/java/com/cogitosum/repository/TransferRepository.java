package com.cogitosum.repository;

import com.cogitosum.entity.Transfer;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransferRepository extends JpaRepository<Transfer, Long> {
    Optional<Transfer> findByIdAndCompanyId(Long id, Long companyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Transfer> findLockedByIdAndCompanyId(Long id, Long companyId);

    @Query("""
            SELECT transfer
            FROM Transfer transfer
            WHERE transfer.company.id = :companyId
            ORDER BY CASE WHEN transfer.transferDate IS NULL THEN 1 ELSE 0 END,
                     transfer.transferDate DESC,
                     transfer.id DESC
            """)
    List<Transfer> findAllByCompanyIdOrderByTransferDateDescIdDesc(@Param("companyId") Long companyId);
}
