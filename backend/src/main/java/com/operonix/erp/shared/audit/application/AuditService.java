package com.operonix.erp.shared.audit.application;

import com.operonix.erp.shared.audit.domain.AuditEvent;
import com.operonix.erp.shared.audit.domain.AuditEventRepository;
import com.operonix.erp.shared.tenant.TenantContext;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    public AuditService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    public void record(String moduleName, String action, String resourceType, String resourceId, String details) {
        AuditEvent event = new AuditEvent();
        event.setModuleName(normalizeRequired(moduleName));
        event.setAction(normalizeRequired(action));
        event.setResourceType(normalizeRequired(resourceType));
        event.setResourceId(normalizeOptional(resourceId));
        event.setDetails(normalizeOptional(details));
        event.setActor(currentUsername());

        auditEventRepository.save(event);
    }

    public List<AuditEvent> listRecent() {
        return auditEventRepository.findTop200ByTenantIdOrderByCreatedAtDesc(TenantContext.getTenantId());
    }

    private String normalizeRequired(String value) {
        if (!StringUtils.hasText(value)) {
            return "UNDEFINED";
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !StringUtils.hasText(authentication.getName())) {
            return "system";
        }
        return authentication.getName();
    }
}
