package com.operonix.erp.modules.user.application;

import com.operonix.erp.config.AppProperties;
import com.operonix.erp.modules.user.domain.SystemUser;
import com.operonix.erp.modules.user.domain.SystemUserRepository;
import com.operonix.erp.modules.user.domain.SystemUserRole;
import com.operonix.erp.shared.tenant.TenantContext;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class UserBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(UserBootstrapService.class);

    private final SystemUserRepository systemUserRepository;
    private final AppProperties appProperties;

    public UserBootstrapService(SystemUserRepository systemUserRepository, AppProperties appProperties) {
        this.systemUserRepository = systemUserRepository;
        this.appProperties = appProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void bootstrap() {
        String username = normalizeUsername(appProperties.getAuth().getUsername());
        String passwordHash = normalizeHash(appProperties.getAuth().getPasswordHash());
        String role = normalizeRole(appProperties.getAuth().getRole());

        if (!StringUtils.hasText(username) || !StringUtils.hasText(passwordHash) || !StringUtils.hasText(role)) {
            log.info("Bootstrap de usuario ignorado: credenciais de AUTH no .env nao configuradas.");
            return;
        }

        if (!isBcrypt(passwordHash)) {
            log.warn("Bootstrap de usuario ignorado: AUTH_PASSWORD_HASH nao esta em formato bcrypt.");
            return;
        }

        String tenantId = appProperties.getTenant().getDefaultId();
        if (!StringUtils.hasText(tenantId)) {
            tenantId = "public";
        }

        boolean exists = systemUserRepository.findByTenantIdAndUsernameIgnoreCase(tenantId, username).isPresent();
        if (exists) {
            return;
        }

        TenantContext.setTenantId(tenantId);
        try {
            SystemUser user = new SystemUser();
            user.setUsername(username);
            user.setDisplayName("Administrador");
            user.setPasswordHash(passwordHash);
            user.setRole(SystemUserRole.valueOf(role));
            user.setActive(true);

            systemUserRepository.save(user);
            log.info("Usuario bootstrap criado para tenant '{}' com login '{}'.", tenantId, username);
        } finally {
            TenantContext.clear();
        }
    }

    private boolean isBcrypt(String hash) {
        return hash.startsWith("$2a$") || hash.startsWith("$2b$") || hash.startsWith("$2y$");
    }

    private String normalizeUsername(String username) {
        if (!StringUtils.hasText(username) || username.startsWith("CHANGE_")) {
            return null;
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeRole(String role) {
        if (!StringUtils.hasText(role)) {
            return null;
        }

        String normalized = role.trim().toUpperCase(Locale.ROOT);
        if (!(normalized.equals("ADMIN") || normalized.equals("ATENDENTE") || normalized.equals("TECNICO"))) {
            return null;
        }

        return normalized;
    }

    private String normalizeHash(String hash) {
        if (!StringUtils.hasText(hash) || hash.startsWith("CHANGE_")) {
            return null;
        }

        String normalized = hash.trim();
        if ((normalized.startsWith("\"") && normalized.endsWith("\"")) || (normalized.startsWith("'") && normalized.endsWith("'"))) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }

        return normalized;
    }
}
