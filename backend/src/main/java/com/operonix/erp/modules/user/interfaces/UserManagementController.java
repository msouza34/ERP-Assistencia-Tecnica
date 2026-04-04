package com.operonix.erp.modules.user.interfaces;

import com.operonix.erp.modules.user.domain.SystemUser;
import com.operonix.erp.modules.user.domain.SystemUserRepository;
import com.operonix.erp.modules.user.domain.SystemUserRole;
import com.operonix.erp.shared.audit.application.AuditService;
import com.operonix.erp.shared.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserManagementController {

    private final SystemUserRepository systemUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserManagementController(
        SystemUserRepository systemUserRepository,
        PasswordEncoder passwordEncoder,
        AuditService auditService
    ) {
        this.systemUserRepository = systemUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @GetMapping
    public ResponseEntity<List<SystemUserResponse>> list() {
        String tenantId = TenantContext.getTenantId();

        List<SystemUserResponse> response = systemUserRepository.findByTenantIdOrderByDisplayNameAsc(tenantId)
            .stream()
            .map(this::toResponse)
            .toList();

        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<SystemUserResponse> create(@Valid @RequestBody CreateSystemUserRequest request) {
        String tenantId = TenantContext.getTenantId();
        String username = normalizeUsername(request.username());

        systemUserRepository.findByTenantIdAndUsernameIgnoreCase(tenantId, username)
            .ifPresent(existing -> {
                throw new IllegalArgumentException("Ja existe usuario com esse login neste tenant.");
            });

        SystemUser user = new SystemUser();
        user.setUsername(username);
        user.setDisplayName(request.displayName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        user.setActive(request.active() == null || request.active());

        SystemUser saved = systemUserRepository.save(user);
        auditService.record(
            "USERS",
            "CREATE_USER",
            "SYSTEM_USER",
            String.valueOf(saved.getId()),
            "Usuario criado: " + saved.getUsername() + " com perfil " + saved.getRole()
        );

        return ResponseEntity.ok(toResponse(saved));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<SystemUserResponse> update(
        @PathVariable Long id,
        @Valid @RequestBody UpdateSystemUserRequest request
    ) {
        String tenantId = TenantContext.getTenantId();
        SystemUser user = requireUser(id, tenantId);

        String username = normalizeUsername(request.username());
        if (!user.getUsername().equalsIgnoreCase(username)) {
            systemUserRepository.findByTenantIdAndUsernameIgnoreCase(tenantId, username)
                .ifPresent(existing -> {
                    if (!existing.getId().equals(user.getId())) {
                        throw new IllegalArgumentException("Ja existe usuario com esse login neste tenant.");
                    }
                });
        }

        user.setUsername(username);
        user.setDisplayName(request.displayName().trim());
        user.setRole(request.role());
        user.setActive(request.active());

        SystemUser saved = systemUserRepository.save(user);
        auditService.record(
            "USERS",
            "UPDATE_USER",
            "SYSTEM_USER",
            String.valueOf(saved.getId()),
            "Usuario atualizado: " + saved.getUsername() + " perfil " + saved.getRole() + " ativo=" + saved.isActiveSafe()
        );

        return ResponseEntity.ok(toResponse(saved));
    }

    @PatchMapping("/{id}/password")
    public ResponseEntity<SystemUserResponse> changePassword(
        @PathVariable Long id,
        @Valid @RequestBody ChangePasswordRequest request
    ) {
        String tenantId = TenantContext.getTenantId();
        SystemUser user = requireUser(id, tenantId);

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        SystemUser saved = systemUserRepository.save(user);

        auditService.record(
            "USERS",
            "CHANGE_PASSWORD",
            "SYSTEM_USER",
            String.valueOf(saved.getId()),
            "Senha alterada para usuario " + saved.getUsername()
        );

        return ResponseEntity.ok(toResponse(saved));
    }

    private SystemUser requireUser(Long id, String tenantId) {
        return systemUserRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Usuario nao encontrado."));
    }

    private String normalizeUsername(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Login de usuario obrigatorio.");
        }

        String normalized = value.trim().toLowerCase();
        if (!normalized.matches("[a-z0-9._-]{3,60}")) {
            throw new IllegalArgumentException("Login invalido. Use apenas letras minusculas, numeros, ponto, underline ou hifen.");
        }

        return normalized;
    }

    private SystemUserResponse toResponse(SystemUser user) {
        return new SystemUserResponse(
            user.getId(),
            user.getUsername(),
            user.getDisplayName(),
            user.getRole(),
            user.isActiveSafe(),
            user.getLastLoginAt() == null ? null : user.getLastLoginAt().toString(),
            user.getCreatedAt() == null ? null : user.getCreatedAt().toString(),
            user.getUpdatedAt() == null ? null : user.getUpdatedAt().toString()
        );
    }

    public record CreateSystemUserRequest(
        @NotBlank @Size(min = 3, max = 60) String username,
        @NotBlank @Size(min = 3, max = 120) String displayName,
        @NotBlank @Size(min = 8, max = 120) String password,
        @NotNull SystemUserRole role,
        Boolean active
    ) {
    }

    public record UpdateSystemUserRequest(
        @NotBlank @Size(min = 3, max = 60) String username,
        @NotBlank @Size(min = 3, max = 120) String displayName,
        @NotNull SystemUserRole role,
        @NotNull Boolean active
    ) {
    }

    public record ChangePasswordRequest(
        @NotBlank @Size(min = 8, max = 120) String newPassword
    ) {
    }

    public record SystemUserResponse(
        Long id,
        String username,
        String displayName,
        SystemUserRole role,
        boolean active,
        String lastLoginAt,
        String createdAt,
        String updatedAt
    ) {
    }
}
