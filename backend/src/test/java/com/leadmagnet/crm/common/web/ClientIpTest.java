package com.leadmagnet.crm.common.web;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.leadmagnet.crm.support.DatabaseCleaner;
import com.leadmagnet.crm.support.IntegrationTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * admin 포트의 신뢰 프록시 뒤 클라이언트 IP (ADR 0021). 공유 컨텍스트는 루프백을 신뢰 프록시로 둔다(@IntegrationTest).
 * 신뢰하지 않는 피어의 XFF는 LoginThrottleApiTest(루프백을 신뢰하지 않는 컨텍스트)가, public 포트는 SubmissionRateLimitTest가 본다.
 */
@IntegrationTest
class ClientIpTest {

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        DatabaseCleaner.truncateAll(jdbc);
    }

    String auditIpAfterFailedLogin(String... forwardedFor) {
        var spec = given().contentType(ContentType.JSON);
        for (String value : forwardedFor) {
            spec = spec.header("X-Forwarded-For", value);
        }
        spec.body(Map.of("email", "nobody@example.com", "password", "wrong-password"))
            .post("/api/admin/auth/login").then().statusCode(401);
        return jdbc.queryForObject("SELECT ip FROM audit_logs WHERE action = 'auth.login.failure'", String.class);
    }

    @Test
    void 신뢰_프록시를_거친_admin_요청은_XFF의_클라이언트_주소가_감사_IP가_된다() {
        assertThat(auditIpAfterFailedLogin("203.0.113.7")).isEqualTo("203.0.113.7");
    }

    @Test
    void XFF_앞쪽에_위조한_값이_있어도_신뢰_프록시가_붙인_마지막_값을_쓴다() {
        assertThat(auditIpAfterFailedLogin("198.51.100.1, 203.0.113.7")).isEqualTo("203.0.113.7");
    }

    @Test
    void XFF가_없으면_피어_주소다() {
        assertThat(auditIpAfterFailedLogin()).isIn("127.0.0.1", "0:0:0:0:0:0:0:1");
    }

    @Test
    void IP_형식이_아닌_XFF는_믿지_않는다() {
        assertThat(auditIpAfterFailedLogin("evil.example")).isIn("127.0.0.1", "0:0:0:0:0:0:0:1");
    }
}
