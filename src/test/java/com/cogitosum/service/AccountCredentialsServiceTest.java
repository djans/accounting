package com.cogitosum.service;

import com.cogitosum.entity.UserAccount;
import com.cogitosum.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountCredentialsServiceTest {

    @Mock private UserAccountRepository userAccountRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private AccountCredentialsService service;

    @BeforeEach
    void setUp() {
        service = new AccountCredentialsService(userAccountRepository, passwordEncoder);
    }

    @Test
    void updatesDefaultEmailAndPasswordTogether() {
        UserAccount user = new UserAccount();
        user.setId(1L);
        user.setEmail(AccountCredentialsService.DEFAULT_EMAIL);
        user.setPasswordHash("old-hash");
        when(userAccountRepository.findByEmail(AccountCredentialsService.DEFAULT_EMAIL))
                .thenReturn(Optional.of(user));
        when(userAccountRepository.findByEmailIgnoreCase("owner@example.test")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("CorrectHorseBattery12")).thenReturn("new-hash");

        AccountCredentialsService.UpdateResult result =
                service.update(AccountCredentialsService.DEFAULT_EMAIL, "owner@example.test",
                        "CorrectHorseBattery12");

        assertEquals(AccountCredentialsService.UpdateResult.SUCCESS, result);
        assertEquals("owner@example.test", user.getEmail());
        assertEquals("new-hash", user.getPasswordHash());
        verify(userAccountRepository).save(user);
    }

    @Test
    void rejectsTheDefaultPasswordForInitialCredentialChange() {
        UserAccount user = new UserAccount();
        user.setId(1L);
        user.setEmail(AccountCredentialsService.DEFAULT_EMAIL);
        user.setPasswordHash("old-hash");
        when(userAccountRepository.findByEmail(AccountCredentialsService.DEFAULT_EMAIL))
                .thenReturn(Optional.of(user));
        when(userAccountRepository.findByEmailIgnoreCase("owner@example.test")).thenReturn(Optional.empty());

        AccountCredentialsService.UpdateResult result =
                service.update(AccountCredentialsService.DEFAULT_EMAIL, "owner@example.test",
                        AccountCredentialsService.DEFAULT_PASSWORD);

        assertEquals(AccountCredentialsService.UpdateResult.DEFAULT_CREDENTIALS_MUST_CHANGE, result);
        verify(userAccountRepository, never()).save(user);
        verify(passwordEncoder, never()).encode(anyString());
    }
}
