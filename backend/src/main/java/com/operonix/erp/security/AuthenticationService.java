package com.operonix.erp.security;

import com.operonix.erp.config.AppProperties;
import com.operonix.erp.modules.user.domain.SystemUser;
import com.operonix.erp.modules.user.domain.SystemUserRepository;
import com.operonix.erp.security.dto.LoginRequest;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuthenticationService {

    private static final Set<String> ALLOWED_ROLES = Set.of("ADMIN", "ATENDENTE", "TECNICO");

    private final AppProperties appProperties;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;
    private final SystemUserRepository systemUserRepository;

    public AuthenticationService(
        AppProperties appProperties,
        PasswordEncoder passwordEncoder,
        LoginAttemptService loginAttemptService,
        SystemUserRepository systemUserRepository
    ) {
        this.appProperties = appProperties;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
        this.systemUserRepository = systemUserRepository;
    }

    public AuthenticatedUser authenticate(LoginRequest request, String clientIp) {
        loginAttemptService.checkAccess(request.username(), clientIp);

        String tenantId = request.tenantId().trim();
        String username = request.username().trim().toLowerCase(Locale.ROOT);

        SystemUser databaseUser = systemUserRepository
            .findByTenantIdAndUsernameIgnoreCase(tenantId, username)
            .orElse(null);

        if (databaseUser != null) {
            if (!databaseUser.isActiveSafe()) {
                loginAttemptService.recordFailure(request.username(), clientIp);
                throw new InvalidCredentialsException("Usuario ou senha invalidos.");
            }

            boolean passwordMatches = passwordEncoder.matches(request.password(), databaseUser.getPasswordHash());
            if (!passwordMatches) {
                loginAttemptService.recordFailure(request.username(), clientIp);
                throw new InvalidCredentialsException("Usuario ou senha invalidos.");
            }

            databaseUser.setLastLoginAt(LocalDateTime.now());
            systemUserRepository.save(databaseUser);

            loginAttemptService.recordSuccess(request.username(), clientIp);
            return new AuthenticatedUser(databaseUser.getUsername(), databaseUser.getRole().name(), tenantId);
        }

        if (appProperties.getAuth().isLegacyEnabled() && authenticateWithLegacyEnv(request)) {
            loginAttemptService.recordSuccess(request.username(), clientIp);
            return new AuthenticatedUser(
                request.username().trim(),
                normalizeRole(appProperties.getAuth().getRole()),
                tenantId
            );
        }

        loginAttemptService.recordFailure(request.username(), clientIp);
        throw new InvalidCredentialsException("Usuario ou senha invalidos.");
    }

    private boolean authenticateWithLegacyEnv(LoginRequest request) {
        String configuredUsername = appProperties.getAuth().getUsername();
        String configuredPasswordHash = normalizeHash(appProperties.getAuth().getPasswordHash());

        if (isUnset(configuredUsername) || isUnset(configuredPasswordHash)) {
            return false;
        }

        normalizeRole(appProperties.getAuth().getRole());

        boolean usernameMatches = configuredUsername.equalsIgnoreCase(request.username().trim());
        boolean passwordMatches = passwordEncoder.matches(request.password(), configuredPasswordHash);

        return usernameMatches && passwordMatches;
    }

    private String normalizeRole(String role) {
        if (!StringUtils.hasText(role)) {
            throw new AuthConfigurationException("AUTH_ROLE deve ser configurado.");
        }

        String normalized = role.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_ROLES.contains(normalized)) {
            throw new AuthConfigurationException("AUTH_ROLE invalido. Use ADMIN, ATENDENTE ou TECNICO.");
        }

        return normalized;
    }

    private String normalizeHash(String hash) {
        if (!StringUtils.hasText(hash)) {
            return hash;
        }

        String normalized = hash.trim();
        if ((normalized.startsWith("\"") && normalized.endsWith("\"")) || (normalized.startsWith("'") && normalized.endsWith("'"))) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return normalized;
    }

    private boolean isUnset(String value) {
        return value == null || value.isBlank() || value.startsWith("CHANGE_");
    }
}
