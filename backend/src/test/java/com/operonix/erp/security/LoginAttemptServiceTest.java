package com.operonix.erp.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.operonix.erp.config.AppProperties;
import org.junit.jupiter.api.Test;

class LoginAttemptServiceTest {

    @Test
    void shouldLockAfterMaxFailures() {
        AppProperties appProperties = properties(3, 15, 15);
        LoginAttemptService service = new LoginAttemptService(appProperties);

        service.recordFailure("admin", "10.0.0.1");
        service.recordFailure("admin", "10.0.0.1");
        service.recordFailure("admin", "10.0.0.1");

        assertThrows(TooManyLoginAttemptsException.class, () -> service.checkAccess("admin", "10.0.0.1"));
    }

    @Test
    void shouldClearLockStateOnSuccess() {
        AppProperties appProperties = properties(3, 15, 15);
        LoginAttemptService service = new LoginAttemptService(appProperties);

        service.recordFailure("admin", "10.0.0.2");
        service.recordSuccess("admin", "10.0.0.2");

        assertDoesNotThrow(() -> service.checkAccess("admin", "10.0.0.2"));
    }

    private AppProperties properties(int maxAttempts, int lockMinutes, int windowMinutes) {
        AppProperties appProperties = new AppProperties();
        appProperties.getSecurity().getLogin().setMaxAttempts(maxAttempts);
        appProperties.getSecurity().getLogin().setLockMinutes(lockMinutes);
        appProperties.getSecurity().getLogin().setWindowMinutes(windowMinutes);
        return appProperties;
    }
}