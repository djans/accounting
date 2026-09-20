package com.cogitosum.repository;

import com.cogitosum.entity.ChartOfAccount;
import com.cogitosum.entity.AccountType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChartOfAccountRepository extends JpaRepository<ChartOfAccount, Long> {
    List<ChartOfAccount> findAllByOrderByAccountNumberAsc();
    Optional<ChartOfAccount> findByAccountNumber(String accountNumber);
    public List<ChartOfAccount> findByAccountTypeOrderByAccountNumberAsc(AccountType accountType);
    List<ChartOfAccount> findByIsActiveOrderByAccountNumberAsc(Boolean isActive);
}

