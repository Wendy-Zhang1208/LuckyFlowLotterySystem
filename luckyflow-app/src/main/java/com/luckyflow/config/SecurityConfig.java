package com.luckyflow.config;

import tools.jackson.databind.ObjectMapper;
import com.luckyflow.auth.config.AuthProperties;
import com.luckyflow.exception.ApiErrorCodes;
import com.luckyflow.response.ApiResponse;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

/** LuckyFlow 无状态 Bearer JWT 安全边界。 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectMapper objectMapper,
            JwtAuthenticationConverter jwtAuthenticationConverter
    ) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/verification-codes",
                                "/api/auth/register",
                                "/api/auth/login/password",
                                "/api/auth/login/code",
                                "/api/e2e/verification-code"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/activities", "/api/activities/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/e2e/inventory/*").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint((request, response, exception) -> writeError(
                                response,
                                objectMapper,
                                HttpServletResponse.SC_UNAUTHORIZED,
                                ApiErrorCodes.UNAUTHENTICATED,
                                "登录状态无效或已过期，请您重新登录"
                        ))
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> writeError(
                                response,
                                objectMapper,
                                HttpServletResponse.SC_UNAUTHORIZED,
                                ApiErrorCodes.UNAUTHENTICATED,
                                "请您先登录"
                        ))
                        .accessDeniedHandler((request, response, exception) -> writeError(
                                response,
                                objectMapper,
                                HttpServletResponse.SC_FORBIDDEN,
                                ApiErrorCodes.FORBIDDEN,
                                "您没有执行该操作的权限"
                        ))
                );
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecretKey luckyFlowJwtSecretKey(AuthProperties properties) {
        String encoded = properties.getJwt().getSecretBase64();
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException("LUCKYFLOW_JWT_SECRET must be configured as Base64");
        }
        final byte[] secret;
        try {
            secret = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException invalidBase64) {
            throw new IllegalStateException("LUCKYFLOW_JWT_SECRET must be valid Base64", invalidBase64);
        }
        if (secret.length < 32) {
            throw new IllegalStateException("LUCKYFLOW_JWT_SECRET must contain at least 256 bits");
        }
        Duration ttl = properties.getJwt().getAccessTokenTtl();
        if (ttl == null || ttl.isZero() || ttl.isNegative() || ttl.compareTo(Duration.ofHours(24)) > 0) {
            throw new IllegalStateException("JWT access-token TTL must be within (0, 24h]");
        }
        if (properties.getJwt().getIssuer() == null || properties.getJwt().getIssuer().isBlank()) {
            throw new IllegalStateException("JWT issuer must not be blank");
        }
        return new SecretKeySpec(secret, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey secretKey) {
        OctetSequenceKey jwk = new OctetSequenceKey.Builder(secretKey)
                .algorithm(JWSAlgorithm.HS256)
                .build();
        JWKSource<SecurityContext> source = new ImmutableJWKSet<>(new JWKSet(jwk));
        return new NimbusJwtEncoder(source);
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey secretKey, AuthProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.getJwt().getIssuer()));
        return decoder;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(AuthProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.getAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, "Idempotency-Key"));
        configuration.setExposedHeaders(List.of("Idempotency-Key"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(Duration.ofHours(1));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private void writeError(
            HttpServletResponse response,
            ObjectMapper objectMapper,
            int httpStatus,
            int code,
            String message
    ) throws IOException {
        response.setStatus(httpStatus);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(code, message));
    }
}
