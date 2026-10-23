package com.leadmagnet.crm.common.error;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

import com.leadmagnet.crm.auth.application.AccessTokenIssuer;
import com.leadmagnet.crm.support.ErrorProbeController;
import com.leadmagnet.crm.support.IntegrationTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/** 프레임워크 예외와 처리하지 못한 예외도 type·code·instance가 있는 ProblemDetail로 나온다. */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class ProblemDetailContractTest {

    static final String PROBLEM_JSON = "application/problem+json";

    @LocalServerPort
    int port;

    @Autowired
    JwtEncoder jwtEncoder;

    String token;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(AccessTokenIssuer.ISSUER)
            .audience(List.of(AccessTokenIssuer.AUDIENCE))
            .subject("1")
            .issuedAt(now)
            .expiresAt(now.plus(AccessTokenIssuer.TTL))
            .build();
        token = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    }

    @Test
    void 인증된_요청의_없는_관리자_경로는_404_not_found다() {
        given().auth().oauth2(token)
            .when().get("/api/admin/no-such-thing")
            .then().statusCode(404).contentType(PROBLEM_JSON)
            .body("code", equalTo("not_found"))
            .body("type", equalTo("https://leadmagnet.example/problems/not_found"))
            .body("instance", equalTo("/api/admin/no-such-thing"));
    }

    @Test
    void 허용하지_않는_메서드는_405_method_not_allowed다() {
        given().auth().oauth2(token)
            .when().post("/api/admin/auth/me")
            .then().statusCode(405).contentType(PROBLEM_JSON)
            .body("code", equalTo("method_not_allowed"))
            .body("type", equalTo("https://leadmagnet.example/problems/method_not_allowed"))
            .body("instance", equalTo("/api/admin/auth/me"));
    }

    @Test
    void 지원하지_않는_Content_Type은_415_unsupported_media_type이다() {
        given().contentType(ContentType.TEXT).body("hello")
            .when().post("/api/admin/auth/login")
            .then().statusCode(415).contentType(PROBLEM_JSON)
            .body("code", equalTo("unsupported_media_type"));
    }

    @Test
    void 요청_파라미터_검증_실패는_422와_필드_오류다() {
        given().auth().oauth2(token).queryParam("value", -1)
            .when().get("/api/admin/__test/errors/positive")
            .then().statusCode(422).contentType(PROBLEM_JSON)
            .body("code", equalTo("validation_failed"))
            .body("errors.field", hasItem("value"));
    }

    @Test
    void 처리하지_못한_예외는_500_internal_error이고_메시지를_응답과_로그에_남기지_않는다(CapturedOutput output) {
        String body = given().auth().oauth2(token)
            .when().get("/api/admin/__test/errors/unexpected")
            .then().statusCode(500).contentType(PROBLEM_JSON)
            .body("code", equalTo("internal_error"))
            .body("type", equalTo("https://leadmagnet.example/problems/internal_error"))
            .body("instance", equalTo("/api/admin/__test/errors/unexpected"))
            .extract().asString();

        assertThat(body).doesNotContain(ErrorProbeController.PII_IN_MESSAGE, "IllegalStateException");
        assertThat(output.getAll())
            .contains("IllegalStateException")
            .doesNotContain(ErrorProbeController.PII_IN_MESSAGE);
    }

    @Test
    void 도메인_예외와_Security_401도_instance를_같은_방식으로_채운다() {
        given().contentType(ContentType.JSON).body("{\"email\":\"nobody@example.com\",\"password\":\"whatever-123\"}")
            .when().post("/api/admin/auth/login")
            .then().statusCode(401).body("instance", equalTo("/api/admin/auth/login"));
        given().when().get("/api/admin/auth/me")
            .then().statusCode(401).body("instance", equalTo("/api/admin/auth/me"));
    }
}
