package com.operonix.erp.modules.workorder.infrastructure;

import com.operonix.erp.modules.workorder.domain.WorkOrderTimelineEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkOrderTimelineEventRepository extends JpaRepository<WorkOrderTimelineEvent, Long> {

    List<WorkOrderTimelineEvent> findByTenantIdAndWorkOrderIdOrderByCreatedAtAsc(String tenantId, Long workOrderId);

    void deleteByTenantIdAndWorkOrderId(String tenantId, Long workOrderId);
}
