package com.cogitosum.service;

import com.cogitosum.entity.Company;
import com.cogitosum.entity.UserAccount;
import com.cogitosum.entity.UserRole;
import com.cogitosum.repository.UserAccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserAccountService {

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private CurrentCompanyContext companyContext;

    public UserAccount createUser(UserAccount userAccount) {
        userAccount.setCompany(companyContext.requireCompany());
        return userAccountRepository.save(userAccount);
    }

    public List<UserAccount> getUsersByCompany(Long companyId) {
        Long currentCompanyId = companyContext.requireCompanyId();
        return currentCompanyId.equals(companyId)
                ? userAccountRepository.findByCompanyId(currentCompanyId)
                : List.of();
    }

    public List<UserAccount> getUsersByRole(UserRole role) {
        return userAccountRepository.findByCompanyIdAndRole(companyContext.requireCompanyId(), role);
    }

    public List<UserAccount> getAllUsers() {
        return userAccountRepository.findByCompanyId(companyContext.requireCompanyId());
    }

    public Optional<UserAccount> getUserById(Long id) {
        return userAccountRepository.findByIdAndCompanyId(id, companyContext.requireCompanyId());
    }

    public Optional<UserAccount> getUserByEmail(String email) {
        return userAccountRepository.findByEmail(email)
                .filter(user -> companyContext.requireCompanyId().equals(user.getCompany().getId()));
    }

    public UserAccount updateUser(Long id, UserAccount updatedUser) {
        Optional<UserAccount> existing = userAccountRepository.findByIdAndCompanyId(id, companyContext.requireCompanyId());
        if (existing.isEmpty()) {
            return null;
        }
        UserAccount current = existing.get();
        current.setFullName(updatedUser.getFullName());
        current.setEmail(updatedUser.getEmail());
        if (updatedUser.getPasswordHash() != null && !updatedUser.getPasswordHash().isBlank()) {
            current.setPasswordHash(updatedUser.getPasswordHash());
        }
        current.setRole(updatedUser.getRole());
        current.setEnabled(updatedUser.isEnabled());
        return userAccountRepository.save(current);
    }

    public void deleteUser(Long id) {
        userAccountRepository.findByIdAndCompanyId(id, companyContext.requireCompanyId())
                .ifPresent(userAccountRepository::delete);
    }

    public Company getCompanyForUser(UserAccount userAccount) {
        return userAccount == null ? null : userAccount.getCompany();
    }
}
