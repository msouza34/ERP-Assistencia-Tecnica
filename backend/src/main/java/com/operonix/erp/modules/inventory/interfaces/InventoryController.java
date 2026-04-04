package com.operonix.erp.modules.inventory.interfaces;

import com.operonix.erp.modules.inventory.domain.InventoryItem;
import com.operonix.erp.modules.inventory.domain.InventoryItemRepository;
import com.operonix.erp.modules.inventory.domain.InventoryMovement;
import com.operonix.erp.modules.inventory.domain.InventoryMovementRepository;
import com.operonix.erp.modules.inventory.domain.InventoryMovementType;
import com.operonix.erp.shared.audit.application.AuditService;
import com.operonix.erp.shared.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryMovementRepository movementRepository;
    private final AuditService auditService;

    public InventoryController(
        InventoryItemRepository inventoryItemRepository,
        InventoryMovementRepository movementRepository,
        AuditService auditService
    ) {
        this.inventoryItemRepository = inventoryItemRepository;
        this.movementRepository = movementRepository;
        this.auditService = auditService;
    }

    @GetMapping("/items")
    public ResponseEntity<List<InventoryItemResponse>> list(
        @RequestParam(name = "search", required = false) String search,
        @RequestParam(name = "category", required = false) String category,
        @RequestParam(name = "onlyLowStock", required = false) Boolean onlyLowStock,
        @RequestParam(name = "active", required = false) Boolean active
    ) {
        String tenantId = TenantContext.getTenantId();
        String normalizedSearch = normalizeSearch(search);
        String normalizedCategory = normalizeSearch(category);

        Stream<InventoryItem> stream = inventoryItemRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId).stream();

        if (normalizedSearch != null) {
            stream = stream.filter(item ->
                contains(item.getName(), normalizedSearch)
                    || contains(item.getSku(), normalizedSearch)
                    || contains(item.getCategory(), normalizedSearch)
                    || contains(item.getSupplierName(), normalizedSearch)
                    || contains(item.getLocation(), normalizedSearch)
            );
        }

        if (normalizedCategory != null) {
            stream = stream.filter(item -> contains(item.getCategory(), normalizedCategory));
        }

        if (onlyLowStock != null && onlyLowStock) {
            stream = stream.filter(InventoryItem::isLowStock);
        }

        if (active != null) {
            stream = stream.filter(item -> item.isActiveSafe() == active);
        }

        List<InventoryItemResponse> response = stream
            .map(this::toItemResponse)
            .toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/items/{id}")
    public ResponseEntity<InventoryItemResponse> details(@PathVariable Long id) {
        return ResponseEntity.ok(toItemResponse(requireItem(id)));
    }

    @PostMapping("/items")
    public ResponseEntity<InventoryItemResponse> create(@Valid @RequestBody CreateInventoryItemRequest request) {
        String tenantId = TenantContext.getTenantId();

        inventoryItemRepository.findByTenantIdAndSkuIgnoreCase(tenantId, request.sku().trim())
            .ifPresent(item -> {
                throw new IllegalArgumentException("Ja existe item com esse SKU neste tenant.");
            });

        InventoryItem item = new InventoryItem();
        item.setSku(request.sku().trim());
        item.setName(request.name().trim());
        item.setCategory(trimToNull(request.category()));
        item.setSupplierName(trimToNull(request.supplierName()));
        item.setLocation(trimToNull(request.location()));
        item.setQuantity(request.quantity());
        item.setMinQuantity(request.minQuantity());
        item.setUnitCost(request.unitCost());
        item.setSalePrice(request.salePrice());
        item.setNotes(trimToNull(request.notes()));
        item.setActive(request.active() == null || request.active());

        InventoryItem saved = inventoryItemRepository.save(item);
        if (saved.getQuantity() != null && saved.getQuantity() > 0) {
            createMovement(saved, InventoryMovementType.ENTRY, saved.getQuantity(), 0, saved.getQuantity(), "Estoque inicial", null);
        }

        auditService.record(
            "INVENTORY",
            "CREATE_ITEM",
            "INVENTORY_ITEM",
            String.valueOf(saved.getId()),
            "Item criado: " + saved.getSku() + " - " + saved.getName()
        );

        return ResponseEntity.ok(toItemResponse(saved));
    }

    @PatchMapping("/items/{id}")
    public ResponseEntity<InventoryItemResponse> update(
        @PathVariable Long id,
        @Valid @RequestBody UpdateInventoryItemRequest request
    ) {
        InventoryItem item = requireItem(id);

        if (!item.getSku().equalsIgnoreCase(request.sku().trim())) {
            inventoryItemRepository.findByTenantIdAndSkuIgnoreCase(TenantContext.getTenantId(), request.sku().trim())
                .ifPresent(other -> {
                    if (!other.getId().equals(item.getId())) {
                        throw new IllegalArgumentException("Ja existe item com esse SKU neste tenant.");
                    }
                });
        }

        item.setSku(request.sku().trim());
        item.setName(request.name().trim());
        item.setCategory(trimToNull(request.category()));
        item.setSupplierName(trimToNull(request.supplierName()));
        item.setLocation(trimToNull(request.location()));
        item.setMinQuantity(request.minQuantity());
        item.setUnitCost(request.unitCost());
        item.setSalePrice(request.salePrice());
        item.setNotes(trimToNull(request.notes()));
        item.setActive(request.active() == null || request.active());

        InventoryItem saved = inventoryItemRepository.save(item);
        auditService.record(
            "INVENTORY",
            "UPDATE_ITEM",
            "INVENTORY_ITEM",
            String.valueOf(saved.getId()),
            "Item atualizado: " + saved.getSku() + " - " + saved.getName()
        );

        return ResponseEntity.ok(toItemResponse(saved));
    }

    @PatchMapping("/items/{id}/movement")
    public ResponseEntity<InventoryItemResponse> movement(
        @PathVariable Long id,
        @Valid @RequestBody MovementRequest request
    ) {
        InventoryItem item = requireItem(id);
        if (!item.isActiveSafe()) {
            throw new IllegalArgumentException("Item inativo. Reative antes de movimentar.");
        }

        int previous = safeQuantity(item.getQuantity());
        int delta;
        int next;

        switch (request.type()) {
            case ENTRY -> {
                int quantity = requiredPositive(request.quantity(), "Quantidade de entrada invalida.");
                delta = quantity;
                next = previous + quantity;
            }
            case EXIT -> {
                int quantity = requiredPositive(request.quantity(), "Quantidade de saida invalida.");
                if (quantity > previous) {
                    throw new IllegalArgumentException("Saida maior que estoque atual.");
                }
                delta = -quantity;
                next = previous - quantity;
            }
            case ADJUSTMENT -> {
                if (request.targetQuantity() == null || request.targetQuantity() < 0) {
                    throw new IllegalArgumentException("Quantidade de ajuste invalida.");
                }
                next = request.targetQuantity();
                delta = next - previous;
            }
            default -> throw new IllegalArgumentException("Tipo de movimentacao nao suportado.");
        }

        item.setQuantity(next);
        InventoryItem saved = inventoryItemRepository.save(item);

        createMovement(
            saved,
            request.type(),
            delta,
            previous,
            next,
            trimToNull(request.reason()),
            trimToNull(request.referenceCode())
        );

        auditService.record(
            "INVENTORY",
            "STOCK_MOVEMENT",
            "INVENTORY_ITEM",
            String.valueOf(saved.getId()),
            "Movimento " + request.type() + " delta=" + delta + " novoSaldo=" + next
        );

        return ResponseEntity.ok(toItemResponse(saved));
    }

    @Transactional
    @DeleteMapping("/items/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        InventoryItem item = requireItem(id);
        String tenantId = TenantContext.getTenantId();

        movementRepository.deleteByTenantIdAndItemId(tenantId, item.getId());
        inventoryItemRepository.delete(item);

        auditService.record(
            "INVENTORY",
            "DELETE_ITEM",
            "INVENTORY_ITEM",
            String.valueOf(item.getId()),
            "Item removido: " + item.getSku() + " - " + item.getName()
        );

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/items/{id}/movements")
    public ResponseEntity<List<InventoryMovementResponse>> movements(@PathVariable Long id) {
        InventoryItem item = requireItem(id);
        String tenantId = TenantContext.getTenantId();

        List<InventoryMovementResponse> response = movementRepository
            .findByTenantIdAndItemIdOrderByCreatedAtDesc(tenantId, item.getId())
            .stream()
            .map(this::toMovementResponse)
            .toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/metrics")
    public ResponseEntity<InventoryMetricsResponse> metrics() {
        String tenantId = TenantContext.getTenantId();
        List<InventoryItem> items = inventoryItemRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId);

        long totalItems = items.size();
        long activeItems = items.stream().filter(InventoryItem::isActiveSafe).count();
        long lowStockItems = items.stream().filter(InventoryItem::isLowStock).count();
        long totalUnits = items.stream().mapToLong(item -> safeQuantity(item.getQuantity())).sum();

        BigDecimal totalStockValue = items.stream()
            .map(this::stockValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Long> byCategory = items.stream()
            .filter(item -> StringUtils.hasText(item.getCategory()))
            .collect(
                java.util.stream.Collectors.groupingBy(
                    item -> item.getCategory().trim(),
                    LinkedHashMap::new,
                    java.util.stream.Collectors.counting()
                )
            );

        return ResponseEntity.ok(new InventoryMetricsResponse(
            totalItems,
            activeItems,
            lowStockItems,
            totalUnits,
            totalStockValue.setScale(2, RoundingMode.HALF_UP),
            byCategory
        ));
    }

    @GetMapping("/alerts/low-stock")
    public ResponseEntity<List<InventoryItemResponse>> lowStockAlerts() {
        String tenantId = TenantContext.getTenantId();

        List<InventoryItemResponse> response = inventoryItemRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId)
            .stream()
            .filter(item -> item.isActiveSafe() && item.isLowStock())
            .sorted(Comparator.comparingInt(item -> safeQuantity(item.getQuantity()) - safeQuantity(item.getMinQuantity())))
            .map(this::toItemResponse)
            .toList();

        return ResponseEntity.ok(response);
    }

    private InventoryItem requireItem(Long id) {
        return inventoryItemRepository.findByIdAndTenantId(id, TenantContext.getTenantId())
            .orElseThrow(() -> new IllegalArgumentException("Item de estoque nao encontrado."));
    }

    private InventoryItemResponse toItemResponse(InventoryItem item) {
        return new InventoryItemResponse(
            item.getId(),
            item.getSku(),
            item.getName(),
            item.getCategory(),
            item.getSupplierName(),
            item.getLocation(),
            safeQuantity(item.getQuantity()),
            safeQuantity(item.getMinQuantity()),
            item.getUnitCost(),
            item.getSalePrice(),
            stockValue(item),
            item.getNotes(),
            item.isActiveSafe(),
            item.isLowStock(),
            item.getCreatedAt() == null ? null : item.getCreatedAt().toString(),
            item.getUpdatedAt() == null ? null : item.getUpdatedAt().toString()
        );
    }

    private InventoryMovementResponse toMovementResponse(InventoryMovement movement) {
        return new InventoryMovementResponse(
            movement.getId(),
            movement.getItemId(),
            movement.getMovementType(),
            movement.getDelta(),
            movement.getPreviousQuantity(),
            movement.getNewQuantity(),
            movement.getReason(),
            movement.getReferenceCode(),
            movement.getCreatedBy(),
            movement.getCreatedAt().toString()
        );
    }

    private void createMovement(
        InventoryItem item,
        InventoryMovementType type,
        int delta,
        int previous,
        int next,
        String reason,
        String referenceCode
    ) {
        InventoryMovement movement = new InventoryMovement();
        movement.setItemId(item.getId());
        movement.setMovementType(type);
        movement.setDelta(delta);
        movement.setPreviousQuantity(previous);
        movement.setNewQuantity(next);
        movement.setReason(reason);
        movement.setReferenceCode(referenceCode);
        movement.setCreatedBy(currentUsername());
        movementRepository.save(movement);
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return "system";
        }
        return authentication.getName();
    }

    private int requiredPositive(Integer value, String message) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private int safeQuantity(Integer value) {
        return value == null ? 0 : value;
    }

    private BigDecimal stockValue(InventoryItem item) {
        if (item.getUnitCost() == null || item.getQuantity() == null) {
            return BigDecimal.ZERO;
        }
        return item.getUnitCost().multiply(BigDecimal.valueOf(item.getQuantity()));
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeSearch(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean contains(String value, String search) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(search);
    }

    public record CreateInventoryItemRequest(
        @NotBlank String sku,
        @NotBlank String name,
        String category,
        String supplierName,
        String location,
        @NotNull @Min(0) Integer quantity,
        @NotNull @Min(0) Integer minQuantity,
        @DecimalMin("0.00") BigDecimal unitCost,
        @DecimalMin("0.00") BigDecimal salePrice,
        String notes,
        Boolean active
    ) {
    }

    public record UpdateInventoryItemRequest(
        @NotBlank String sku,
        @NotBlank String name,
        String category,
        String supplierName,
        String location,
        @NotNull @Min(0) Integer minQuantity,
        @DecimalMin("0.00") BigDecimal unitCost,
        @DecimalMin("0.00") BigDecimal salePrice,
        String notes,
        Boolean active
    ) {
    }

    public record MovementRequest(
        @NotNull InventoryMovementType type,
        Integer quantity,
        Integer targetQuantity,
        String reason,
        String referenceCode
    ) {
    }

    public record InventoryItemResponse(
        Long id,
        String sku,
        String name,
        String category,
        String supplierName,
        String location,
        Integer quantity,
        Integer minQuantity,
        BigDecimal unitCost,
        BigDecimal salePrice,
        BigDecimal stockValue,
        String notes,
        boolean active,
        boolean lowStock,
        String createdAt,
        String updatedAt
    ) {
    }

    public record InventoryMovementResponse(
        Long id,
        Long itemId,
        InventoryMovementType type,
        Integer delta,
        Integer previousQuantity,
        Integer newQuantity,
        String reason,
        String referenceCode,
        String createdBy,
        String createdAt
    ) {
    }

    public record InventoryMetricsResponse(
        long totalItems,
        long activeItems,
        long lowStockItems,
        long totalUnits,
        BigDecimal totalStockValue,
        Map<String, Long> byCategory
    ) {
    }
}
