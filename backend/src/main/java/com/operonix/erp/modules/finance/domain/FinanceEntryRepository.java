package com.operonix.erp.modules.finance.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FinanceEntryRepository extends JpaRepository<FinanceEntry, Long> {

    List<FinanceEntry> findByTenantIdOrderByDueDateAsc(String tenantId);

    Optional<FinanceEntry> findByIdAndTenantId(Long id, String tenantId);

    @Query("""
        select coalesce(sum(f.amount), 0)
        from FinanceEntry f
        where f.tenantId = :tenantId
          and f.type = :type
          and f.paid = false
        """)
    BigDecimal sumOpenAmountByType(@Param("tenantId") String tenantId, @Param("type") FinanceEntryType type);
}
