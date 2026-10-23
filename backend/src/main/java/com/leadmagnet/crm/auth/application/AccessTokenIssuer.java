package com.leadmagnet.crm.auth.application;

import com.leadmagnet.crm.auth.domain.Operator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/** 운영자 access token(JWT HS256)을 발급한다. 검증 규칙은 auth.config.JwtConfig의 디코더가 같은 상수로 맞춘다. */
@Component
public class AccessTokenIssuer {

    public static final String ISSUER = "lead-magnet-crm";
    public static final String AUDIENCE = "lead-magnet-crm-admin";
    public static final Duration TTL = Duration.ofMinutes(15);

    private final JwtEncoder jwtEncoder;
    private final Clock clock;

    public AccessTokenIssuer(JwtEncoder jwtEncoder, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.clock = clock;
    }

    public String issue(Operator operator) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(ISSUER)
            .audience(List.of(AUDIENCE))
            .subject(String.valueOf(operator.getId()))
            .claim("email", operator.getEmail())
            .issuedAt(now)
            .expiresAt(now.plus(TTL))
            .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
