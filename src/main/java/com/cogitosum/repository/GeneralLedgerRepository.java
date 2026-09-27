package com.cogitosum.repository;

import com.cogitosum.entity.GeneralLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GeneralLedgerRepository extends JpaRepository<GeneralLedger, Long> {
    List<GeneralLedger> findAllByOrderByAccountAccountNumberAsc();
    Optional<GeneralLedger> findByAccountId(Long accountId);
    Optional<GeneralLedger> findByIdAndCompanyId(Long id, Long companyId);
    List<GeneralLedger> findAllByCompanyIdOrderByAccountAccountNumberAsc(Long companyId);
    Optional<GeneralLedger> findByCompanyIdAndAccountId(Long companyId, Long accountId);
}
