package com.operonix.erp.modules.inventory.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {

    List<InventoryMovement> findByTenantIdAndItemIdOrderByCreatedAtDesc(String tenantId, Long itemId);

    List<InventoryMovement> findTop100ByTenantIdOrderByCreatedAtDesc(String tenantId);

    void deleteByTenantIdAndItemId(String tenantId, Long itemId);
}
