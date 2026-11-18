package com.luckyflow.auth.service;

import com.luckyflow.auth.config.AuthProperties;
import com.luckyflow.user.constant.UserRoleConstants;
import com.luckyflow.user.entity.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** 签发短有效期、带完整性保护的 Bearer JWT。 */
@Service
public class JwtTokenService {

    private final JwtEncoder encoder;
    private final AuthProperties properties;
    private final Clock clock;

    public JwtTokenService(JwtEncoder encoder, AuthProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedToken issue(User user) {
        Instant issuedAt = clock.instant();
        long expiresIn = properties.getJwt().getAccessTokenTtl().toSeconds();
        Instant expiresAt = issuedAt.plusSeconds(expiresIn);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.getJwt().getIssuer())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(Long.toString(user.getId()))
                .claim("username", user.getAccount())
                .claim("roles", List.of(user.getRole() == null || user.getRole().isBlank()
                        ? UserRoleConstants.USER
                        : user.getRole()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, expiresIn);
    }

    public record IssuedToken(String value, long expiresInSeconds) {
    }
}
