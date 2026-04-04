package com.operonix.erp.modules.user.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemUserRepository extends JpaRepository<SystemUser, Long> {

    List<SystemUser> findByTenantIdOrderByDisplayNameAsc(String tenantId);

    Optional<SystemUser> findByIdAndTenantId(Long id, String tenantId);

    Optional<SystemUser> findByTenantIdAndUsernameIgnoreCase(String tenantId, String username);
}
