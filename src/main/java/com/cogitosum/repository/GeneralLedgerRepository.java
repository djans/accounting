package com.cogitosum.repository;

import com.cogitosum.entity.GeneralLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GeneralLedgerRepository extends JpaRepository<GeneralLedger, Long> {
    Optional<GeneralLedger> findByAccountId(Long accountId);
}

