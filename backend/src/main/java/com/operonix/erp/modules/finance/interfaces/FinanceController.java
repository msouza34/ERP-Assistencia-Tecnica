package com.operonix.erp.modules.finance.interfaces;

import com.operonix.erp.modules.finance.domain.FinanceEntry;
import com.operonix.erp.modules.finance.domain.FinanceEntryRepository;
import com.operonix.erp.modules.finance.domain.FinanceEntryType;
import com.operonix.erp.shared.audit.application.AuditService;
import com.operonix.erp.shared.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/finance/entries")
public class FinanceController {

    private final FinanceEntryRepository financeEntryRepository;
    private final AuditService auditService;

    public FinanceController(FinanceEntryRepository financeEntryRepository, AuditService auditService) {
        this.financeEntryRepository = financeEntryRepository;
        this.auditService = auditService;
    }

    @GetMapping
    public ResponseEntity<List<FinanceEntry>> list() {
        return ResponseEntity.ok(financeEntryRepository.findByTenantIdOrderByDueDateAsc(TenantContext.getTenantId()));
    }

    @PostMapping
    public ResponseEntity<FinanceEntry> create(@Valid @RequestBody CreateFinanceEntryRequest request) {
        FinanceEntry entry = new FinanceEntry();
        applyRequest(entry, request.description(), request.type(), request.amount(), request.dueDate(), request.paid());

        FinanceEntry saved = financeEntryRepository.save(entry);
        auditService.record(
            "FINANCE",
            "CREATE_ENTRY",
            "FINANCE_ENTRY",
            String.valueOf(saved.getId()),
            "Lancamento criado com valor " + saved.getAmount() + " e tipo " + saved.getType()
        );

        return ResponseEntity.ok(saved);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<FinanceEntry> update(
        @PathVariable Long id,
        @Valid @RequestBody UpdateFinanceEntryRequest request
    ) {
        FinanceEntry entry = requireEntry(id);
        applyRequest(entry, request.description(), request.type(), request.amount(), request.dueDate(), request.paid());

        FinanceEntry saved = financeEntryRepository.save(entry);
        auditService.record(
            "FINANCE",
            "UPDATE_ENTRY",
            "FINANCE_ENTRY",
            String.valueOf(saved.getId()),
            "Lancamento atualizado com valor " + saved.getAmount() + " e tipo " + saved.getType()
        );

        return ResponseEntity.ok(saved);
    }

    @PatchMapping("/{id}/paid")
    public ResponseEntity<FinanceEntry> markAsPaid(
        @PathVariable Long id,
        @RequestBody(required = false) UpdateFinanceEntryStatusRequest request
    ) {
        FinanceEntry entry = requireEntry(id);

        boolean paid = request == null || request.paid();
        entry.setPaid(paid);
        FinanceEntry saved = financeEntryRepository.save(entry);
        auditService.record(
            "FINANCE",
            "UPDATE_ENTRY_STATUS",
            "FINANCE_ENTRY",
            String.valueOf(saved.getId()),
            "Lancamento marcado como " + (saved.isPaid() ? "pago" : "aberto")
        );

        return ResponseEntity.ok(saved);
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        FinanceEntry entry = requireEntry(id);

        financeEntryRepository.delete(entry);
        auditService.record(
            "FINANCE",
            "DELETE_ENTRY",
            "FINANCE_ENTRY",
            String.valueOf(id),
            "Lancamento removido: " + entry.getDescription()
        );

        return ResponseEntity.noContent().build();
    }
    private FinanceEntry requireEntry(Long id) {
        return financeEntryRepository.findByIdAndTenantId(id, TenantContext.getTenantId())
            .orElseThrow(() -> new IllegalArgumentException("Lancamento nao encontrado"));
    }

    private void applyRequest(
        FinanceEntry entry,
        String description,
        FinanceEntryType type,
        BigDecimal amount,
        LocalDate dueDate,
        boolean paid
    ) {
        entry.setDescription(description.trim());
        entry.setType(type);
        entry.setAmount(amount);
        entry.setDueDate(dueDate);
        entry.setPaid(paid);
    }

    public record CreateFinanceEntryRequest(
        @NotBlank String description,
        @NotNull FinanceEntryType type,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotNull LocalDate dueDate,
        boolean paid
    ) {
    }

    public record UpdateFinanceEntryRequest(
        @NotBlank String description,
        @NotNull FinanceEntryType type,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotNull LocalDate dueDate,
        boolean paid
    ) {
    }

    public record UpdateFinanceEntryStatusRequest(boolean paid) {
    }
}
