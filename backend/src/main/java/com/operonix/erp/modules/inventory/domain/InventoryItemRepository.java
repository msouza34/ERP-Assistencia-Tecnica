package com.operonix.erp.modules.inventory.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {

    List<InventoryItem> findByTenantIdOrderByUpdatedAtDesc(String tenantId);

    Optional<InventoryItem> findByIdAndTenantId(Long id, String tenantId);

    Optional<InventoryItem> findByTenantIdAndSkuIgnoreCase(String tenantId, String sku);

    @Query(
        """
        select count(i)
        from InventoryItem i
        where i.tenantId = :tenantId
          and (i.active is null or i.active = true)
          and i.quantity is not null
          and i.minQuantity is not null
          and i.quantity <= i.minQuantity
        """
    )
    long countLowStockByTenantId(@Param("tenantId") String tenantId);
}
