package com.operonix.erp.modules.customer.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    List<Customer> findByTenantIdOrderByNameAsc(String tenantId);
}
