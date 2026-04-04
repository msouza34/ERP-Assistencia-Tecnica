package com.operonix.erp.security;

import com.operonix.erp.config.AppProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LoginAttemptService {

    private final Map<String, AttemptState> attempts = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int maxAttempts;
    private final Duration lockDuration;
    private final Duration windowDuration;

    @Autowired
    public LoginAttemptService(AppProperties appProperties) {
        this(appProperties, Clock.systemUTC());
    }

    LoginAttemptService(AppProperties appProperties, Clock clock) {
        this.clock = clock;
        this.maxAttempts = appProperties.getSecurity().getLogin().getMaxAttempts();
        this.lockDuration = Duration.ofMinutes(appProperties.getSecurity().getLogin().getLockMinutes());
        this.windowDuration = Duration.ofMinutes(appProperties.getSecurity().getLogin().getWindowMinutes());
    }

    public void checkAccess(String username, String clientIp) {
        enforceLock(userKey(username));
        enforceLock(ipKey(clientIp));
    }

    public void recordFailure(String username, String clientIp) {
        registerFailure(userKey(username));
        registerFailure(ipKey(clientIp));
    }

    public void recordSuccess(String username, String clientIp) {
        attempts.remove(userKey(username));
        attempts.remove(ipKey(clientIp));
    }

    private void enforceLock(String key) {
        AttemptState state = attempts.get(key);
        if (state == null) {
            return;
        }

        Instant now = Instant.now(clock);

        if (state.lockedUntil != null && now.isBefore(state.lockedUntil)) {
            long retryAfter = Duration.between(now, state.lockedUntil).getSeconds();
            throw new TooManyLoginAttemptsException(
                "Muitas tentativas invalidas. Tente novamente mais tarde.",
                Math.max(retryAfter, 1)
            );
        }

        if (state.lockedUntil != null && !now.isBefore(state.lockedUntil)) {
            attempts.remove(key);
            return;
        }

        if (Duration.between(state.firstFailureAt, now).compareTo(windowDuration) > 0) {
            attempts.remove(key);
        }
    }

    private void registerFailure(String key) {
        Instant now = Instant.now(clock);

        attempts.compute(key, (ignored, current) -> {
            if (current == null || Duration.between(current.firstFailureAt, now).compareTo(windowDuration) > 0) {
                return new AttemptState(1, now, null);
            }

            int failures = current.failures + 1;
            Instant lockedUntil = current.lockedUntil;
            if (failures >= maxAttempts) {
                lockedUntil = now.plus(lockDuration);
            }

            return new AttemptState(failures, current.firstFailureAt, lockedUntil);
        });
    }

    private String userKey(String username) {
        String normalizedUser = username == null ? "" : username.trim().toLowerCase();
        return "user:" + normalizedUser;
    }

    private String ipKey(String clientIp) {
        return "ip:" + normalizeIp(clientIp);
    }

    private String normalizeIp(String clientIp) {
        return clientIp == null || clientIp.isBlank() ? "unknown" : clientIp.trim();
    }

    private record AttemptState(
        int failures,
        Instant firstFailureAt,
        Instant lockedUntil
    ) {
    }
}
