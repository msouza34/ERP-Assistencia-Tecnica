package com.operonix.erp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String baseUrl;
    private final Cors cors = new Cors();
    private final Jwt jwt = new Jwt();
    private final Tenant tenant = new Tenant();
    private final Auth auth = new Auth();
    private final Security security = new Security();

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Cors getCors() {
        return cors;
    }

    public Jwt getJwt() {
        return jwt;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public Auth getAuth() {
        return auth;
    }

    public Security getSecurity() {
        return security;
    }

    public static class Cors {
        private String allowedOrigins;

        public String getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(String allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }

    public static class Jwt {
        private String secret;
        private long expirationMinutes = 120;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public long getExpirationMinutes() {
            return expirationMinutes;
        }

        public void setExpirationMinutes(long expirationMinutes) {
            this.expirationMinutes = expirationMinutes;
        }
    }

    public static class Tenant {
        private String defaultId = "public";

        public String getDefaultId() {
            return defaultId;
        }

        public void setDefaultId(String defaultId) {
            this.defaultId = defaultId;
        }
    }

    public static class Auth {
        private String username;
        private String passwordHash;
        private String role = "ADMIN";
        private boolean legacyEnabled = false;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPasswordHash() {
            return passwordHash;
        }

        public void setPasswordHash(String passwordHash) {
            this.passwordHash = passwordHash;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public boolean isLegacyEnabled() {
            return legacyEnabled;
        }

        public void setLegacyEnabled(boolean legacyEnabled) {
            this.legacyEnabled = legacyEnabled;
        }
    }

    public static class Security {
        private final Login login = new Login();

        public Login getLogin() {
            return login;
        }
    }

    public static class Login {
        private int maxAttempts = 5;
        private int lockMinutes = 15;
        private int windowMinutes = 15;

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public int getLockMinutes() {
            return lockMinutes;
        }

        public void setLockMinutes(int lockMinutes) {
            this.lockMinutes = lockMinutes;
        }

        public int getWindowMinutes() {
            return windowMinutes;
        }

        public void setWindowMinutes(int windowMinutes) {
            this.windowMinutes = windowMinutes;
        }
    }
}
