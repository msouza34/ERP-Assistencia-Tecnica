package com.operonix.erp.shared.tenant;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

public class TenantEntityListener {

    @PrePersist
    @PreUpdate
    public void applyTenant(TenantEntity entity) {
        if (entity.getTenantId() == null || entity.getTenantId().isBlank()) {
            String tenantId = TenantContext.getTenantId();
            if (tenantId == null || tenantId.isBlank()) {
                throw new IllegalStateException("Tenant nao informado para persistencia.");
            }
            entity.setTenantId(tenantId);
        }
    }
}
