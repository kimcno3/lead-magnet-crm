package com.leadmagnet.crm.auth.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import com.leadmagnet.crm.auth.domain.Operator;
import com.leadmagnet.crm.auth.domain.OperatorRepository;
import com.leadmagnet.crm.support.DatabaseCleaner;
import com.leadmagnet.crm.support.IntegrationTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * /refresh의 IP별 rate limit (ADR 0021 변경 기록). 다른 테스트는 한도를 크게 두므로 이 클래스만 분당 3으로 띄운다.
 * 루프백이 신뢰 프록시(@IntegrationTest)라 X-Forwarded-For로 클라이언트 IP를 바꿔 IP마다 따로 세는지 본다.
 * 토큰 버킷은 IP 키로 컨텍스트 안에 남으므로 테스트마다 다른 IP 대역을 쓴다.
 */
@IntegrationTest
class RefreshRateLimitApiTest {

    static final String EMAIL = "owner@example.com";
    static final String PASSWORD = "correct-horse-battery";

    @DynamicPropertySource
    static void lowLimit(DynamicPropertyRegistry registry) {
        registry.add("app.auth.refresh-rate-limit", () -> 3);
    }

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    OperatorRepository operators;

    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        DatabaseCleaner.truncateAll(jdbc);
        operators.save(new Operator(EMAIL, passwordEncoder.encode(PASSWORD), Instant.now()));
    }

    String loginCookie(String ip) {
        return given().contentType(ContentType.JSON).header("X-Forwarded-For", ip)
            .body(Map.of("email", EMAIL, "password", PASSWORD))
            .post("/api/admin/auth/login").then().statusCode(200).extract().cookie("lm_refresh");
    }

    Response refresh(String ip, String cookie) {
        var request = given().header("X-Forwarded-For", ip);
        if (cookie != null) {
            request.cookie("lm_refresh", cookie);
        }
        return request.post("/api/admin/auth/refresh");
    }

    @Test
    void 한도_안의_회전은_성공한다() {
        String cookie = loginCookie("198.51.100.1");
        for (int i = 0; i < 3; i++) {
            Response response = refresh("198.51.100.1", cookie);
            response.then().statusCode(200);
            cookie = response.getCookie("lm_refresh");
        }
    }

    @Test
    void 한도를_넘으면_쿠키를_보기_전에_429_too_many_requests와_Retry_After다() {
        String cookie = loginCookie("198.51.100.2");
        for (int i = 0; i < 3; i++) {
            refresh("198.51.100.2", null).then().statusCode(401);
        }

        Response limited = refresh("198.51.100.2", cookie);
        limited.then().statusCode(429).contentType(containsString("application/problem+json"))
            .body("code", equalTo("too_many_requests"))
            .body("type", equalTo("https://leadmagnet.example/problems/too_many_requests"));
        // 분당 3이면 토큰 하나가 20초마다 찬다
        assertThat(Long.parseLong(limited.getHeader("Retry-After"))).isBetween(1L, 20L);
        assertThat(limited.getHeader("Set-Cookie")).isNull();
        // 거절된 요청은 토큰을 쓰지 않았다: 같은 쿠키가 다른 IP에서는 그대로 회전된다
        refresh("198.51.100.99", cookie).then().statusCode(200);
    }

    @Test
    void 다른_IP는_따로_센다() {
        for (int i = 0; i < 3; i++) {
            refresh("203.0.113.1", null).then().statusCode(401);
        }
        refresh("203.0.113.1", null).then().statusCode(429);

        refresh("203.0.113.2", null).then().statusCode(401).body("code", equalTo("invalid_refresh_token"));
    }
}
