package com.operonix.erp.modules.workorder.infrastructure;

import com.operonix.erp.modules.workorder.domain.WorkOrder;
import com.operonix.erp.modules.workorder.domain.WorkOrderStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long> {

    List<WorkOrder> findByTenantIdOrderByUpdatedAtDesc(String tenantId);

    Optional<WorkOrder> findByIdAndTenantId(Long id, String tenantId);

    long countByTenantIdAndStatus(String tenantId, WorkOrderStatus status);
}