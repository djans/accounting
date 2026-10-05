package com.cogitosum.service;

import com.cogitosum.entity.UserAccount;
import com.cogitosum.repository.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class AccountCredentialsService {

    public static final String DEFAULT_EMAIL = "root@example.com";
    public static final String DEFAULT_PASSWORD = "password";
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final int MINIMUM_PASSWORD_LENGTH = 12;

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public AccountCredentialsService(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public boolean usesDefaultCredentials(String email) {
        if (DEFAULT_EMAIL.equalsIgnoreCase(email)) {
            return true;
        }
        return userAccountRepository.findByEmail(email)
                .map(user -> passwordEncoder.matches(DEFAULT_PASSWORD, user.getPasswordHash()))
                .orElse(false);
    }

    @Transactional
    public UpdateResult update(String currentEmail, String requestedEmail, String requestedPassword) {
        UserAccount user = userAccountRepository.findByEmail(currentEmail).orElse(null);
        if (user == null) {
            return UpdateResult.ACCOUNT_NOT_FOUND;
        }

        String newEmail = requestedEmail == null ? "" : requestedEmail.trim();
        if (!EMAIL_PATTERN.matcher(newEmail).matches()) {
            return UpdateResult.INVALID_EMAIL;
        }
        newEmail = newEmail.toLowerCase(Locale.ROOT);

        var emailOwner = userAccountRepository.findByEmailIgnoreCase(newEmail);
        if (emailOwner.isPresent() && !emailOwner.get().getId().equals(user.getId())) {
            return UpdateResult.EMAIL_IN_USE;
        }

        String newPassword = requestedPassword == null ? "" : requestedPassword;
        boolean changingDefaults = usesDefaultCredentials(currentEmail);
        if (changingDefaults && (DEFAULT_EMAIL.equalsIgnoreCase(newEmail)
                || newPassword.isBlank() || DEFAULT_PASSWORD.equals(newPassword))) {
            return UpdateResult.DEFAULT_CREDENTIALS_MUST_CHANGE;
        }
        if (!newPassword.isBlank() && newPassword.length() < MINIMUM_PASSWORD_LENGTH) {
            return UpdateResult.PASSWORD_TOO_SHORT;
        }

        user.setEmail(newEmail);
        if (!newPassword.isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(newPassword));
        }
        userAccountRepository.save(user);
        return UpdateResult.SUCCESS;
    }

    public enum UpdateResult {
        SUCCESS,
        ACCOUNT_NOT_FOUND,
        INVALID_EMAIL,
        EMAIL_IN_USE,
        PASSWORD_TOO_SHORT,
        DEFAULT_CREDENTIALS_MUST_CHANGE
    }
}
