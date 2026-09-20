package com.cogitosum.repository;

import com.cogitosum.entity.UserAccount;
import com.cogitosum.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByEmail(String email);
    List<UserAccount> findByCompanyId(Long companyId);
    List<UserAccount> findByRole(UserRole role);
}
