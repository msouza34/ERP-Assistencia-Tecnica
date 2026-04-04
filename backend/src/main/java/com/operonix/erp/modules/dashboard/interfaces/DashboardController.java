package com.operonix.erp.modules.dashboard.interfaces;

import com.operonix.erp.modules.finance.domain.FinanceEntryRepository;
import com.operonix.erp.modules.finance.domain.FinanceEntryType;
import com.operonix.erp.modules.inventory.domain.InventoryItemRepository;
import com.operonix.erp.modules.workorder.domain.WorkOrderStatus;
import com.operonix.erp.modules.workorder.infrastructure.WorkOrderRepository;
import com.operonix.erp.shared.tenant.TenantContext;
import java.math.BigDecimal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final WorkOrderRepository workOrderRepository;
    private final FinanceEntryRepository financeEntryRepository;
    private final InventoryItemRepository inventoryItemRepository;

    public DashboardController(
        WorkOrderRepository workOrderRepository,
        FinanceEntryRepository financeEntryRepository,
        InventoryItemRepository inventoryItemRepository
    ) {
        this.workOrderRepository = workOrderRepository;
        this.financeEntryRepository = financeEntryRepository;
        this.inventoryItemRepository = inventoryItemRepository;
    }

    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryResponse> summary() {
        String tenantId = TenantContext.getTenantId();

        long openWorkOrders = workOrderRepository.countByTenantIdAndStatus(tenantId, WorkOrderStatus.ENTRADA)
            + workOrderRepository.countByTenantIdAndStatus(tenantId, WorkOrderStatus.EM_ANALISE)
            + workOrderRepository.countByTenantIdAndStatus(tenantId, WorkOrderStatus.AGUARDANDO_APROVACAO)
            + workOrderRepository.countByTenantIdAndStatus(tenantId, WorkOrderStatus.EM_REPARO);

        long lowStockItems = inventoryItemRepository.countLowStockByTenantId(tenantId);

        BigDecimal payable = financeEntryRepository.sumOpenAmountByType(tenantId, FinanceEntryType.PAYABLE);
        BigDecimal receivable = financeEntryRepository.sumOpenAmountByType(tenantId, FinanceEntryType.RECEIVABLE);

        DashboardSummaryResponse response = new DashboardSummaryResponse(
            openWorkOrders,
            lowStockItems,
            payable == null ? BigDecimal.ZERO : payable,
            receivable == null ? BigDecimal.ZERO : receivable
        );

        return ResponseEntity.ok(response);
    }

    public record DashboardSummaryResponse(
        long openWorkOrders,
        long lowStockItems,
        BigDecimal totalPayable,
        BigDecimal totalReceivable
    ) {
    }
}
