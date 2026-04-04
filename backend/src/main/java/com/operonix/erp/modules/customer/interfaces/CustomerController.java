package com.operonix.erp.modules.customer.interfaces;

import com.operonix.erp.modules.customer.domain.Customer;
import com.operonix.erp.modules.customer.domain.CustomerRepository;
import com.operonix.erp.shared.audit.application.AuditService;
import com.operonix.erp.shared.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerRepository customerRepository;
    private final AuditService auditService;

    public CustomerController(CustomerRepository customerRepository, AuditService auditService) {
        this.customerRepository = customerRepository;
        this.auditService = auditService;
    }

    @GetMapping
    public ResponseEntity<List<Customer>> list() {
        return ResponseEntity.ok(customerRepository.findByTenantIdOrderByNameAsc(TenantContext.getTenantId()));
    }

    @PostMapping
    public ResponseEntity<Customer> create(@Valid @RequestBody CreateCustomerRequest request) {
        Customer customer = new Customer();
        customer.setName(request.name());
        customer.setDocument(request.document());
        customer.setPhone(request.phone());
        customer.setEmail(request.email());

        Customer saved = customerRepository.save(customer);
        auditService.record(
            "CUSTOMERS",
            "CREATE_CUSTOMER",
            "CUSTOMER",
            String.valueOf(saved.getId()),
            "Cliente criado: " + saved.getName()
        );

        return ResponseEntity.ok(saved);
    }

    public record CreateCustomerRequest(
        @NotBlank String name,
        @NotBlank String document,
        @NotBlank String phone,
        @NotBlank String email
    ) {
    }
}
