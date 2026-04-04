package com.operonix.erp.modules.budget.interfaces;

import com.operonix.erp.modules.budget.domain.Budget;
import com.operonix.erp.modules.budget.domain.BudgetRepository;
import com.operonix.erp.modules.budget.domain.BudgetStatus;
import com.operonix.erp.shared.audit.application.AuditService;
import com.operonix.erp.shared.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/budgets")
public class BudgetController {

    private static final Set<BudgetStatus> PIPELINE_STATUSES = Set.of(BudgetStatus.RASCUNHO, BudgetStatus.ENVIADO);

    private final BudgetRepository budgetRepository;
    private final AuditService auditService;

    public BudgetController(BudgetRepository budgetRepository, AuditService auditService) {
        this.budgetRepository = budgetRepository;
        this.auditService = auditService;
    }

    @GetMapping
    public ResponseEntity<List<BudgetResponse>> list(
        @RequestParam(name = "status", required = false) BudgetStatus status,
        @RequestParam(name = "search", required = false) String search
    ) {
        String tenantId = TenantContext.getTenantId();
        String normalizedSearch = normalizeSearch(search);

        Stream<Budget> stream = budgetRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId).stream();

        if (status != null) {
            stream = stream.filter(item -> item.getStatus() == status);
        }

        if (normalizedSearch != null) {
            stream = stream.filter(item ->
                contains(item.getBudgetNumber(), normalizedSearch)
                    || contains(item.getCustomerName(), normalizedSearch)
                    || contains(item.getEquipment(), normalizedSearch)
            );
        }

        List<BudgetResponse> response = stream
            .map(this::toResponse)
            .toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/metrics")
    public ResponseEntity<BudgetMetricsResponse> metrics() {
        String tenantId = TenantContext.getTenantId();

        long total = budgetRepository.findByTenantIdOrderByUpdatedAtDesc(tenantId).size();
        long draft = budgetRepository.countByTenantIdAndStatus(tenantId, BudgetStatus.RASCUNHO);
        long sent = budgetRepository.countByTenantIdAndStatus(tenantId, BudgetStatus.ENVIADO);
        long approved = budgetRepository.countByTenantIdAndStatus(tenantId, BudgetStatus.APROVADO);
        long rejected = budgetRepository.countByTenantIdAndStatus(tenantId, BudgetStatus.REPROVADO);
        long expired = budgetRepository.countByTenantIdAndStatus(tenantId, BudgetStatus.EXPIRADO);

        BigDecimal approvedTotal = budgetRepository.sumTotalByStatuses(tenantId, List.of(BudgetStatus.APROVADO));
        BigDecimal pipelineTotal = budgetRepository.sumTotalByStatuses(tenantId, PIPELINE_STATUSES);

        return ResponseEntity.ok(new BudgetMetricsResponse(
            total,
            draft,
            sent,
            approved,
            rejected,
            expired,
            approvedTotal,
            pipelineTotal
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BudgetResponse> details(@PathVariable Long id) {
        return ResponseEntity.ok(toResponse(requireBudget(id)));
    }

    @PostMapping
    public ResponseEntity<BudgetResponse> create(@Valid @RequestBody CreateBudgetRequest request) {
        Budget budget = new Budget();
        applyRequest(
            budget,
            request.customerName(),
            request.customerPhone(),
            request.equipment(),
            request.problemDescription(),
            request.itemsDescription(),
            request.laborCost(),
            request.partsCost(),
            request.discountAmount(),
            request.validUntil(),
            request.notes()
        );

        Budget saved = budgetRepository.save(budget);
        auditService.record(
            "BUDGET",
            "CREATE",
            "BUDGET",
            String.valueOf(saved.getId()),
            "Orcamento criado: " + saved.getBudgetNumber() + " para " + saved.getCustomerName()
        );

        return ResponseEntity.ok(toResponse(saved));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<BudgetResponse> update(
        @PathVariable Long id,
        @Valid @RequestBody UpdateBudgetRequest request
    ) {
        Budget budget = requireBudget(id);
        applyRequest(
            budget,
            request.customerName(),
            request.customerPhone(),
            request.equipment(),
            request.problemDescription(),
            request.itemsDescription(),
            request.laborCost(),
            request.partsCost(),
            request.discountAmount(),
            request.validUntil(),
            request.notes()
        );

        Budget saved = budgetRepository.save(budget);
        auditService.record(
            "BUDGET",
            "UPDATE",
            "BUDGET",
            String.valueOf(saved.getId()),
            "Orcamento atualizado: " + saved.getBudgetNumber()
        );

        return ResponseEntity.ok(toResponse(saved));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<BudgetResponse> updateStatus(
        @PathVariable Long id,
        @Valid @RequestBody UpdateBudgetStatusRequest request
    ) {
        Budget budget = requireBudget(id);
        BudgetStatus previousStatus = budget.getStatus();
        budget.setStatus(request.status());

        Budget saved = budgetRepository.save(budget);
        auditService.record(
            "BUDGET",
            "UPDATE_STATUS",
            "BUDGET",
            String.valueOf(saved.getId()),
            "Status alterado de " + previousStatus.name() + " para " + saved.getStatus().name()
        );

        return ResponseEntity.ok(toResponse(saved));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Budget budget = requireBudget(id);

        budgetRepository.delete(budget);
        auditService.record(
            "BUDGET",
            "DELETE",
            "BUDGET",
            String.valueOf(id),
            "Orcamento removido: " + budget.getBudgetNumber()
        );

        return ResponseEntity.noContent().build();
    }

    private Budget requireBudget(Long id) {
        String tenantId = TenantContext.getTenantId();
        return budgetRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Orcamento nao encontrado"));
    }

    private void applyRequest(
        Budget budget,
        String customerName,
        String customerPhone,
        String equipment,
        String problemDescription,
        String itemsDescription,
        BigDecimal laborCost,
        BigDecimal partsCost,
        BigDecimal discountAmount,
        LocalDate validUntil,
        String notes
    ) {
        budget.setCustomerName(requiredText(customerName, "customerName"));
        budget.setCustomerPhone(normalizeOptional(customerPhone));
        budget.setEquipment(requiredText(equipment, "equipment"));
        budget.setProblemDescription(requiredText(problemDescription, "problemDescription"));
        budget.setItemsDescription(normalizeOptional(itemsDescription));
        budget.setLaborCost(nonNullMoney(laborCost));
        budget.setPartsCost(nonNullMoney(partsCost));
        budget.setDiscountAmount(nonNullMoney(discountAmount));
        budget.setValidUntil(validUntil);
        budget.setNotes(normalizeOptional(notes));
        budget.recalculateTotal();
    }

    private BudgetResponse toResponse(Budget item) {
        return new BudgetResponse(
            item.getId(),
            item.getBudgetNumber(),
            item.getCustomerName(),
            item.getCustomerPhone(),
            item.getEquipment(),
            item.getProblemDescription(),
            item.getItemsDescription(),
            item.getLaborCost(),
            item.getPartsCost(),
            item.getDiscountAmount(),
            item.getTotalAmount(),
            item.getStatus(),
            item.getValidUntil() == null ? null : item.getValidUntil().toString(),
            item.getNotes(),
            item.getCreatedAt().toString(),
            item.getUpdatedAt().toString()
        );
    }

    private String normalizeSearch(String search) {
        if (!StringUtils.hasText(search)) {
            return null;
        }
        return search.trim().toLowerCase(Locale.ROOT);
    }

    private boolean contains(String value, String search) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(search);
    }

    private String requiredText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Campo obrigatorio: " + field);
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private BigDecimal nonNullMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public record CreateBudgetRequest(
        @NotBlank String customerName,
        String customerPhone,
        @NotBlank String equipment,
        @NotBlank String problemDescription,
        String itemsDescription,
        @DecimalMin("0.00") BigDecimal laborCost,
        @DecimalMin("0.00") BigDecimal partsCost,
        @DecimalMin("0.00") BigDecimal discountAmount,
        LocalDate validUntil,
        String notes
    ) {
    }

    public record UpdateBudgetRequest(
        @NotBlank String customerName,
        String customerPhone,
        @NotBlank String equipment,
        @NotBlank String problemDescription,
        String itemsDescription,
        @DecimalMin("0.00") BigDecimal laborCost,
        @DecimalMin("0.00") BigDecimal partsCost,
        @DecimalMin("0.00") BigDecimal discountAmount,
        LocalDate validUntil,
        String notes
    ) {
    }

    public record UpdateBudgetStatusRequest(
        @NotNull BudgetStatus status
    ) {
    }

    public record BudgetResponse(
        Long id,
        String budgetNumber,
        String customerName,
        String customerPhone,
        String equipment,
        String problemDescription,
        String itemsDescription,
        BigDecimal laborCost,
        BigDecimal partsCost,
        BigDecimal discountAmount,
        BigDecimal totalAmount,
        BudgetStatus status,
        String validUntil,
        String notes,
        String createdAt,
        String updatedAt
    ) {
    }

    public record BudgetMetricsResponse(
        long total,
        long draft,
        long sent,
        long approved,
        long rejected,
        long expired,
        BigDecimal approvedTotal,
        BigDecimal pipelineTotal
    ) {
    }
}
