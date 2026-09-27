package com.cogitosum.repository;

import com.cogitosum.entity.BankReconciliationSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BankReconciliationSessionRepository extends JpaRepository<BankReconciliationSession, Long> {
    Optional<BankReconciliationSession> findFirstByCompanyIdAndBankAccountIdOrderByStatementDateDescIdDesc(
            Long companyId, Long bankAccountId);
}
