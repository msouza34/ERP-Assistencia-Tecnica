package com.operonix.erp.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotBlank @Size(min = 3, max = 100) String username,
    @NotBlank @Size(min = 8, max = 128) String password,
    @NotBlank @Size(min = 2, max = 80) String tenantId
) {
}