package com.leadmagnet.crm.auth.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;

import com.leadmagnet.crm.auth.domain.Operator;
import com.leadmagnet.crm.auth.domain.OperatorRepository;
import com.leadmagnet.crm.support.DatabaseCleaner;
import com.leadmagnet.crm.support.IntegrationTest;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import io.restassured.RestAssured;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** 관리자 API의 Bearer 검증(서명·만료·iss·aud)과 401·403 ProblemDetail 형식. */
@IntegrationTest
class AdminResourceServerTest {

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    OperatorRepository operators;

    @Autowired
    JwtEncoder jwtEncoder;

    long operatorId;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        DatabaseCleaner.truncateAll(jdbc);
        operatorId = operators.save(new Operator("owner@example.com", "{noop}unused")).getId();
    }

    @Test
    void 올바르게_서명한_토큰이면_me가_200이다() {
        String token = sign(jwtEncoder, claims(Instant.now(), "lead-magnet-crm", "lead-magnet-crm-admin"));

        me(token).then().statusCode(200).body("email", equalTo("owner@example.com"));
    }

    @Test
    void 토큰_없이_me를_부르면_401_ProblemDetail이다() {
        given().when().get("/api/admin/auth/me")
            .then().statusCode(401).contentType("application/problem+json")
            .body("status", equalTo(401))
            .body("code", equalTo("unauthorized"))
            .body("type", equalTo("https://leadmagnet.example/problems/unauthorized"));
    }

    @Test
    void 다른_키로_서명한_토큰은_401이다() {
        JwtEncoder otherKey = new NimbusJwtEncoder(new ImmutableSecret<>(
            new SecretKeySpec("another-secret-another-secret-xx".getBytes(StandardCharsets.UTF_8), "HmacSHA256")));

        me(sign(otherKey, claims(Instant.now(), "lead-magnet-crm", "lead-magnet-crm-admin")))
            .then().statusCode(401).contentType("application/problem+json").body("code", equalTo("unauthorized"));
    }

    @Test
    void 만료된_토큰은_401이다() {
        Instant issuedAt = Instant.now().minus(Duration.ofHours(1));

        me(sign(jwtEncoder, claims(issuedAt, "lead-magnet-crm", "lead-magnet-crm-admin")))
            .then().statusCode(401).body("code", equalTo("unauthorized"));
    }

    @Test
    void aud가_다른_토큰은_401이다() {
        me(sign(jwtEncoder, claims(Instant.now(), "lead-magnet-crm", "some-other-service")))
            .then().statusCode(401).body("code", equalTo("unauthorized"));
    }

    @Test
    void iss가_다른_토큰은_401이다() {
        me(sign(jwtEncoder, claims(Instant.now(), "evil-issuer", "lead-magnet-crm-admin")))
            .then().statusCode(401).body("code", equalTo("unauthorized"));
    }

    @Test
    void 형식이_깨진_토큰은_401이다() {
        me("not-a-jwt").then().statusCode(401).body("code", equalTo("unauthorized"));
    }

    @Test
    void 관리자_API_밖의_정의되지_않은_경로는_막는다() {
        given().when().get("/internal/anything")
            .then().statusCode(401).contentType("application/problem+json");
    }

    @Test
    void 인증_없이_OpenAPI_문서를_볼_수_있다() {
        given().when().get("/v3/api-docs")
            .then().statusCode(200).body("openapi", startsWith("3."));
    }

    private io.restassured.response.Response me(String token) {
        return given().auth().oauth2(token).when().get("/api/admin/auth/me");
    }

    private JwtClaimsSet claims(Instant issuedAt, String issuer, String audience) {
        return JwtClaimsSet.builder()
            .issuer(issuer)
            .audience(List.of(audience))
            .subject(String.valueOf(operatorId))
            .claim("email", "owner@example.com")
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plus(Duration.ofMinutes(15)))
            .build();
    }

    private static String sign(JwtEncoder encoder, JwtClaimsSet claims) {
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    }
}
