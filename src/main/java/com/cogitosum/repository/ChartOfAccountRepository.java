package com.cogitosum.repository;

import com.cogitosum.entity.AccountCategory;
import com.cogitosum.entity.ChartOfAccount;
import com.cogitosum.entity.AccountType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChartOfAccountRepository extends JpaRepository<ChartOfAccount, Long> {
    List<ChartOfAccount> findAllByOrderByAccountNumberAsc();
    Optional<ChartOfAccount> findByAccountNumber(String accountNumber);
    public List<ChartOfAccount> findByAccountTypeOrderByAccountNumberAsc(AccountType accountType);
    List<ChartOfAccount> findByIsActiveOrderByAccountNumberAsc(Boolean isActive);
    List<ChartOfAccount> findByCategory(AccountCategory category);
    Optional<ChartOfAccount> findByIdAndCompanyId(Long id, Long companyId);
    List<ChartOfAccount> findAllByCompanyIdOrderByAccountNumberAsc(Long companyId);
    Optional<ChartOfAccount> findByCompanyIdAndAccountNumber(Long companyId, String accountNumber);
    List<ChartOfAccount> findByCompanyIdAndAccountTypeOrderByAccountNumberAsc(Long companyId, AccountType accountType);
    List<ChartOfAccount> findByCompanyIdAndIsActiveOrderByAccountNumberAsc(Long companyId, Boolean isActive);
    List<ChartOfAccount> findByCompanyIdAndCategory(Long companyId, AccountCategory category);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from ChartOfAccount account where account.id = :id and account.company.id = :companyId")
    Optional<ChartOfAccount> findLockedByIdAndCompanyId(@Param("id") Long id, @Param("companyId") Long companyId);
}
