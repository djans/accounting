package com.cogitosum.repository;

import com.cogitosum.entity.UserAccount;
import com.cogitosum.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByEmail(String email);
    Optional<UserAccount> findByEmailIgnoreCase(String email);
    List<UserAccount> findByCompanyId(Long companyId);
    Optional<UserAccount> findFirstByEnabledTrueOrderByIdAsc();
    Optional<UserAccount> findByIdAndCompanyId(Long id, Long companyId);
}
