package com.operonix.erp.config;

import com.operonix.erp.security.AuthConfigurationException;
import com.operonix.erp.security.JwtAuthenticationFilter;
import com.operonix.erp.shared.tenant.TenantFilter;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        JwtAuthenticationFilter jwtAuthenticationFilter,
        TenantFilter tenantFilter
    ) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/actuator/health", "/api/v1/auth/**", "/error").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/ping").permitAll()

                .requestMatchers(HttpMethod.GET, "/api/v1/audit/events").hasRole("ADMIN")
                .requestMatchers("/api/v1/users/**").hasRole("ADMIN")

                .requestMatchers(HttpMethod.GET, "/api/v1/budgets", "/api/v1/budgets/**").hasAnyRole("ADMIN", "ATENDENTE", "TECNICO")
                .requestMatchers(HttpMethod.POST, "/api/v1/budgets", "/api/v1/budgets/**").hasAnyRole("ADMIN", "ATENDENTE")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/budgets/**").hasAnyRole("ADMIN", "ATENDENTE")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/budgets/**").hasAnyRole("ADMIN", "ATENDENTE")

                .requestMatchers(HttpMethod.GET, "/api/v1/finance/entries", "/api/v1/finance/entries/**").hasAnyRole("ADMIN", "ATENDENTE")
                .requestMatchers(HttpMethod.POST, "/api/v1/finance/entries", "/api/v1/finance/entries/**").hasAnyRole("ADMIN", "ATENDENTE")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/finance/entries/**").hasAnyRole("ADMIN", "ATENDENTE")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/finance/entries/**").hasAnyRole("ADMIN", "ATENDENTE")

                .requestMatchers(HttpMethod.POST, "/api/v1/inventory/**").hasAnyRole("ADMIN", "ATENDENTE", "TECNICO")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/inventory/**").hasAnyRole("ADMIN", "ATENDENTE", "TECNICO")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/inventory/**").hasAnyRole("ADMIN", "ATENDENTE")

                .requestMatchers(HttpMethod.GET, "/api/v1/customers", "/api/v1/customers/**").hasAnyRole("ADMIN", "ATENDENTE")
                .requestMatchers(HttpMethod.POST, "/api/v1/customers", "/api/v1/customers/**").hasAnyRole("ADMIN", "ATENDENTE")

                .requestMatchers(HttpMethod.POST, "/api/v1/work-orders", "/api/v1/work-orders/**").hasAnyRole("ADMIN", "ATENDENTE", "TECNICO")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/work-orders/**").hasAnyRole("ADMIN", "ATENDENTE", "TECNICO")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/work-orders/**").hasAnyRole("ADMIN", "ATENDENTE")

                .anyRequest().authenticated()
             )
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) -> response.sendError(HttpStatus.UNAUTHORIZED.value()))
                .accessDeniedHandler((request, response, accessDeniedException) -> response.sendError(HttpStatus.FORBIDDEN.value()))
            )
            .headers(headers -> headers
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000)
                )
                .frameOptions(frame -> frame.deny())
                .contentTypeOptions(Customizer.withDefaults())
            )
            .addFilterBefore(tenantFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties appProperties) {
        String configuredOrigins = appProperties.getCors().getAllowedOrigins();
        if (!StringUtils.hasText(configuredOrigins)) {
            throw new AuthConfigurationException("CORS_ALLOWED_ORIGINS deve ser configurado.");
        }

        List<String> allowedOrigins = Arrays.stream(configuredOrigins.split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .toList();

        if (allowedOrigins.isEmpty()) {
            throw new AuthConfigurationException("CORS_ALLOWED_ORIGINS nao pode ficar vazio.");
        }

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Tenant-Id"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}

