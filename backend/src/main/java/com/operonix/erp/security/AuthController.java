package com.operonix.erp.security;

import com.operonix.erp.security.dto.LoginRequest;
import com.operonix.erp.security.dto.LoginResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationService authenticationService;

    public AuthController(JwtTokenProvider jwtTokenProvider, AuthenticationService authenticationService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.authenticationService = authenticationService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
        @Valid @RequestBody LoginRequest request,
        HttpServletRequest httpRequest
    ) {
        String clientIp = extractClientIp(httpRequest);
        AuthenticatedUser user = authenticationService.authenticate(request, clientIp);

        String token = jwtTokenProvider.generateToken(user.username(), user.role(), user.tenantId());
        return ResponseEntity.ok(new LoginResponse(token, user.username(), user.role(), user.tenantId()));
    }

    private String extractClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (!isTrustedProxyAddress(remoteAddr)) {
            return remoteAddr;
        }

        String forwarded = request.getHeader("X-Forwarded-For");
        if (!StringUtils.hasText(forwarded)) {
            return remoteAddr;
        }

        String[] chain = forwarded.split(",");
        for (int index = chain.length - 1; index >= 0; index--) {
            String candidate = chain[index].trim();
            if (!candidate.isBlank()) {
                return candidate;
            }
        }

        return remoteAddr;
    }

    private boolean isTrustedProxyAddress(String address) {
        if (!StringUtils.hasText(address)) {
            return false;
        }

        return address.equals("127.0.0.1")
            || address.equals("::1")
            || address.startsWith("10.")
            || address.startsWith("192.168.")
            || is172Private(address);
    }

    private boolean is172Private(String address) {
        if (!address.startsWith("172.")) {
            return false;
        }

        String[] octets = address.split("\\.");
        if (octets.length < 2) {
            return false;
        }

        try {
            int second = Integer.parseInt(octets[1]);
            return second >= 16 && second <= 31;
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}
