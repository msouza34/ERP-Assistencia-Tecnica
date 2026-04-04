package com.operonix.erp.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.operonix.erp.config.AppProperties;
import com.operonix.erp.modules.user.domain.SystemUserRepository;
import com.operonix.erp.security.dto.LoginRequest;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthenticationServiceTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    void shouldAuthenticateWhenLegacyCredentialsAreValidAndLegacyEnabled() {
        AppProperties appProperties = configuredProperties(
            "admin",
            passwordEncoder.encode("admin12345"),
            "ADMIN",
            true
        );
        LoginAttemptService attempts = new LoginAttemptService(appProperties);
        SystemUserRepository users = mock(SystemUserRepository.class);
        when(users.findByTenantIdAndUsernameIgnoreCase("public", "admin")).thenReturn(Optional.empty());

        AuthenticationService authService = new AuthenticationService(appProperties, passwordEncoder, attempts, users);

        AuthenticatedUser user = authService.authenticate(
            new LoginRequest("admin", "admin12345", "public"),
            "127.0.0.1"
        );

        assertEquals("admin", user.username());
        assertEquals("ADMIN", user.role());
    }

    @Test
    void shouldRejectLegacyCredentialsWhenLegacyIsDisabled() {
        AppProperties appProperties = configuredProperties(
            "admin",
            passwordEncoder.encode("admin12345"),
            "ADMIN",
            false
        );
        LoginAttemptService attempts = new LoginAttemptService(appProperties);
        SystemUserRepository users = mock(SystemUserRepository.class);
        when(users.findByTenantIdAndUsernameIgnoreCase("public", "admin")).thenReturn(Optional.empty());

        AuthenticationService authService = new AuthenticationService(appProperties, passwordEncoder, attempts, users);

        assertThrows(
            InvalidCredentialsException.class,
            () -> authService.authenticate(new LoginRequest("admin", "admin12345", "public"), "127.0.0.1")
        );
    }

    @Test
    void shouldRejectInvalidPassword() {
        AppProperties appProperties = configuredProperties(
            "admin",
            passwordEncoder.encode("admin12345"),
            "ADMIN",
            true
        );
        LoginAttemptService attempts = new LoginAttemptService(appProperties);
        SystemUserRepository users = mock(SystemUserRepository.class);
        when(users.findByTenantIdAndUsernameIgnoreCase("public", "admin")).thenReturn(Optional.empty());

        AuthenticationService authService = new AuthenticationService(appProperties, passwordEncoder, attempts, users);

        assertThrows(
            InvalidCredentialsException.class,
            () -> authService.authenticate(new LoginRequest("admin", "wrong-pass", "public"), "127.0.0.1")
        );
    }

    @Test
    void shouldRejectWhenAuthIsNotConfigured() {
        AppProperties appProperties = configuredProperties(
            "CHANGE_ADMIN_USERNAME",
            "CHANGE_BCRYPT_HASH",
            "ADMIN",
            true
        );
        LoginAttemptService attempts = new LoginAttemptService(appProperties);
        SystemUserRepository users = mock(SystemUserRepository.class);
        when(users.findByTenantIdAndUsernameIgnoreCase("public", "admin")).thenReturn(Optional.empty());

        AuthenticationService authService = new AuthenticationService(appProperties, passwordEncoder, attempts, users);

        assertThrows(
            InvalidCredentialsException.class,
            () -> authService.authenticate(new LoginRequest("admin", "admin12345", "public"), "127.0.0.1")
        );
    }

    private AppProperties configuredProperties(String username, String passwordHash, String role, boolean legacyEnabled) {
        AppProperties appProperties = new AppProperties();
        appProperties.getAuth().setUsername(username);
        appProperties.getAuth().setPasswordHash(passwordHash);
        appProperties.getAuth().setRole(role);
        appProperties.getAuth().setLegacyEnabled(legacyEnabled);
        appProperties.getSecurity().getLogin().setMaxAttempts(5);
        appProperties.getSecurity().getLogin().setLockMinutes(15);
        appProperties.getSecurity().getLogin().setWindowMinutes(15);
        return appProperties;
    }
}
