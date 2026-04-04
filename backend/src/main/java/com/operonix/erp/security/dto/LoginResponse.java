package com.operonix.erp.security.dto;

public record LoginResponse(
    String token,
    String username,
    String role,
    String tenantId
) {
}
