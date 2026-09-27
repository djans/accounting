package com.cogitosum.repository;

import com.cogitosum.entity.BankTransaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {
    boolean existsByCompanyIdAndBankAccountIdAndSourceHash(Long companyId, Long bankAccountId, String sourceHash);

    List<BankTransaction> findByCompanyIdAndBankAccountIdAndSourceRowHashIn(
            Long companyId, Long bankAccountId, Collection<String> sourceRowHashes);

    List<BankTransaction> findByCompanyIdAndBankAccountIdAndReconciledFalseOrderByTransactionDateAscIdAsc(
            Long companyId, Long bankAccountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<BankTransaction> findByIdInAndCompanyIdAndBankAccountId(
            Collection<Long> ids, Long companyId, Long bankAccountId);
}
