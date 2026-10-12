package com.cogitosum.repository;

import com.cogitosum.entity.BankCategorizationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BankCategorizationRuleRepository extends JpaRepository<BankCategorizationRule, Long> {
    List<BankCategorizationRule> findByCompanyIdOrderByPriorityAscIdAsc(Long companyId);
    Optional<BankCategorizationRule> findByIdAndCompanyId(Long id, Long companyId);
    List<BankCategorizationRule> findByCompanyIdAndActiveTrueOrderByPriorityAscIdAsc(Long companyId);
}
