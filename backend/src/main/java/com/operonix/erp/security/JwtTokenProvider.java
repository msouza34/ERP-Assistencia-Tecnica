package com.operonix.erp.security;

import com.operonix.erp.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class JwtTokenProvider {

    private static final int MIN_SECRET_LENGTH = 32;

    private final SecretKey secretKey;
    private final long expirationMinutes;

    public JwtTokenProvider(AppProperties appProperties) {
        String configuredSecret = appProperties.getJwt().getSecret();

        if (!StringUtils.hasText(configuredSecret) || configuredSecret.startsWith("CHANGE_")) {
            throw new AuthConfigurationException("JWT_SECRET deve ser configurado por variavel de ambiente.");
        }

        if (configuredSecret.length() < MIN_SECRET_LENGTH) {
            throw new AuthConfigurationException("JWT_SECRET deve ter pelo menos 32 caracteres.");
        }

        this.secretKey = Keys.hmacShaKeyFor(configuredSecret.getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = appProperties.getJwt().getExpirationMinutes();
    }

    public String generateToken(String username, String role, String tenantId) {
        Instant now = Instant.now();
        Instant expiry = now.plus(expirationMinutes, ChronoUnit.MINUTES);

        return Jwts.builder()
            .subject(username)
            .claim("role", role)
            .claim("tenantId", tenantId)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .signWith(secretKey)
            .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
