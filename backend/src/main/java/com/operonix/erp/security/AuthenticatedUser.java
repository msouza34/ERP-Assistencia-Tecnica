package com.operonix.erp.security;

public record AuthenticatedUser(
    String username,
    String role,
    String tenantId
) {
}