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

    public UserAccount createUser(UserAccount userAccount) {
        return userAccountRepository.save(userAccount);
    }

    public List<UserAccount> getUsersByCompany(Long companyId) {
        return userAccountRepository.findByCompanyId(companyId);
    }

    public List<UserAccount> getUsersByRole(UserRole role) {
        return userAccountRepository.findByRole(role);
    }

    public List<UserAccount> getAllUsers() {
        return userAccountRepository.findAll();
    }

    public Optional<UserAccount> getUserById(Long id) {
        return userAccountRepository.findById(id);
    }

    public Optional<UserAccount> getUserByEmail(String email) {
        return userAccountRepository.findByEmail(email);
    }

    public UserAccount updateUser(Long id, UserAccount updatedUser) {
        Optional<UserAccount> existing = userAccountRepository.findById(id);
        if (existing.isEmpty()) {
            return null;
        }
        UserAccount current = existing.get();
        current.setCompany(updatedUser.getCompany());
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
        userAccountRepository.deleteById(id);
    }

    public Company getCompanyForUser(UserAccount userAccount) {
        return userAccount == null ? null : userAccount.getCompany();
    }
}
