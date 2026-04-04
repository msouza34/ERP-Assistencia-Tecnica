package com.operonix.erp.modules.budget.domain;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    List<Budget> findByTenantIdOrderByUpdatedAtDesc(String tenantId);

    Optional<Budget> findByIdAndTenantId(Long id, String tenantId);

    long countByTenantIdAndStatus(String tenantId, BudgetStatus status);

    @Query("""
        select coalesce(sum(b.totalAmount), 0)
        from Budget b
        where b.tenantId = :tenantId
          and b.status in :statuses
        """)
    BigDecimal sumTotalByStatuses(@Param("tenantId") String tenantId, @Param("statuses") Collection<BudgetStatus> statuses);
}
