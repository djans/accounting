package com.cogitosum.service;

import com.cogitosum.entity.UserAccount;
import com.cogitosum.repository.UserAccountRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserAccountRepository userAccountRepository;

    public AppUserDetailsService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserAccount userAccount = userAccountRepository.findByEmail(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        return User.withUsername(userAccount.getEmail())
            .password(userAccount.getPasswordHash())
            .roles(userAccount.getRole().name())
            .accountLocked(!userAccount.isEnabled())
            .disabled(!userAccount.isEnabled())
            .build();
    }
}
