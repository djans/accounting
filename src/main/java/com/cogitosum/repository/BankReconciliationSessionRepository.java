package com.cogitosum.repository;

import com.cogitosum.entity.BankReconciliationSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BankReconciliationSessionRepository extends JpaRepository<BankReconciliationSession, Long> {
    Optional<BankReconciliationSession> findFirstByCompanyIdAndBankAccountIdOrderByStatementDateDescIdDesc(
            Long companyId, Long bankAccountId);

    Optional<BankReconciliationSession> findByIdAndCompanyId(Long id, Long companyId);

    List<BankReconciliationSession> findByCompanyIdAndBankAccountIdOrderByStatementDateDescIdDesc(
            Long companyId, Long bankAccountId);

    @EntityGraph(attributePaths = "bankAccount")
    List<BankReconciliationSession> findByCompanyIdOrderByStatementDateDescIdDesc(Long companyId);
}
