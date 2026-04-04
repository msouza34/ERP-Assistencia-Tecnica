package com.operonix.erp.shared.audit.interfaces;

import com.operonix.erp.shared.audit.application.AuditService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/events")
    public ResponseEntity<List<AuditEventResponse>> list() {
        List<AuditEventResponse> response = auditService.listRecent().stream()
            .map(event -> new AuditEventResponse(
                event.getId(),
                event.getModuleName(),
                event.getAction(),
                event.getResourceType(),
                event.getResourceId(),
                event.getDetails(),
                event.getActor(),
                event.getCreatedAt().toString()
            ))
            .toList();

        return ResponseEntity.ok(response);
    }

    public record AuditEventResponse(
        Long id,
        String moduleName,
        String action,
        String resourceType,
        String resourceId,
        String details,
        String actor,
        String createdAt
    ) {
    }
}
