package com.leadmagnet.crm.auth.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import com.leadmagnet.crm.auth.domain.Operator;
import com.leadmagnet.crm.auth.domain.OperatorRepository;
import com.leadmagnet.crm.support.DatabaseCleaner;
import com.leadmagnet.crm.support.IntegrationTest;
import com.leadmagnet.crm.support.MutableClock;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * 로그인 시도 제한 (ADR 0021). 다른 테스트는 한도를 크게 두므로 이 클래스만 낮은 한도(이메일 3, IP 5, 창 15분)로 따로 띄운다.
 * 신뢰 프록시는 테스트 클라이언트(루프백)가 아닌 대역으로 두어, 이 컨텍스트의 X-Forwarded-For는 모두 위조된 값이다.
 * 기록은 컨텍스트 안에서 계속 살아 있으므로 각 테스트 전에 시계를 창보다 길게 옮긴다(되돌리지 않는다: 이 컨텍스트의 Clock은
 * 이 클래스만 쓴다).
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class LoginThrottleApiTest {

    static final String EMAIL = "owner@example.com";
    static final String PASSWORD = "correct-horse-battery";
    static final String WRONG = "wrong-password-xyz";

    @DynamicPropertySource
    static void lowLimits(DynamicPropertyRegistry registry) {
        registry.add("app.auth.login-throttle.email-max-failures", () -> 3);
        registry.add("app.auth.login-throttle.ip-max-failures", () -> 5);
        registry.add("app.trusted-proxies", () -> "192.0.2.0/24");
    }

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    OperatorRepository operators;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    MutableClock clock;

    @BeforeEach
    void setUp() {
        clock.advance(Duration.ofMinutes(16));
        RestAssured.port = port;
        DatabaseCleaner.truncateAll(jdbc);
        operators.save(new Operator(EMAIL, passwordEncoder.encode(PASSWORD), Instant.now()));
    }

    Response login(String email, String password) {
        return given().contentType(ContentType.JSON).body(Map.of("email", email, "password", password))
            .when().post("/api/admin/auth/login");
    }

    void failTimes(String email, int times) {
        for (int i = 0; i < times; i++) {
            login(email, WRONG).then().statusCode(401).body("code", equalTo("invalid_credentials"));
        }
    }

    int audit(String action) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = ?", Integer.class, action);
    }

    @Test
    void 이메일별_실패가_한도에_닿으면_올바른_비밀번호도_429와_Retry_After이고_토큰을_주지_않는다() {
        failTimes(EMAIL, 3);

        Response locked = login(EMAIL, PASSWORD);
        locked.then().statusCode(429).contentType(containsString("application/problem+json"))
            .body("code", equalTo("too_many_login_attempts"))
            .body("type", equalTo("https://leadmagnet.example/problems/too_many_login_attempts"))
            .body("status", equalTo(429))
            .body("accessToken", nullValue());
        // 첫 실패가 창(15분)을 벗어날 때까지. 시계는 실제 시간과 함께 흐르므로 조금 줄어 있을 수 있다.
        assertThat(Long.parseLong(locked.getHeader("Retry-After"))).isBetween(880L, 900L);
        assertThat(locked.getHeader("Set-Cookie")).isNull();
        assertThat(locked.asString()).doesNotContain(EMAIL, PASSWORD);

        // 다른 이메일은 영향이 없다(IP 한도 5 안)
        login("other@example.com", WRONG).then().statusCode(401);
    }

    @Test
    void 없는_이메일도_같은_규칙으로_세고_응답_모양이_같다() {
        // IP 한도(5)에 닿지 않게 두 계정 사이에 창을 넘긴다
        failTimes(EMAIL, 3);
        Response existingResponse = login(EMAIL, PASSWORD);
        clock.advance(Duration.ofMinutes(16));
        failTimes("nobody@example.com", 3);
        Response unknownResponse = login("nobody@example.com", PASSWORD);

        existingResponse.then().statusCode(429);
        unknownResponse.then().statusCode(429);
        assertThat(unknownResponse.getHeader("Retry-After")).isNotNull();
        Map<String, Object> existing = existingResponse.as(Map.class);
        Map<String, Object> unknown = unknownResponse.as(Map.class);
        existing.remove("instance");
        unknown.remove("instance");
        assertThat(unknown).isEqualTo(existing);
    }

    @Test
    void 성공하면_그_이메일의_실패_기록이_초기화된다() {
        failTimes(EMAIL, 2);
        login(EMAIL, PASSWORD).then().statusCode(200);
        failTimes(EMAIL, 2);

        login(EMAIL, PASSWORD).then().statusCode(200);
    }

    @Test
    void Retry_After만큼_지나면_다시_로그인할_수_있다() {
        failTimes(EMAIL, 3);
        String retryAfter = login(EMAIL, PASSWORD).then().statusCode(429).extract().header("Retry-After");

        clock.advance(Duration.ofSeconds(Long.parseLong(retryAfter)));
        login(EMAIL, PASSWORD).then().statusCode(200);
    }

    @Test
    void IP별_한도는_이메일과_무관하고_신뢰하지_않는_피어의_위조_XFF로_우회할_수_없다() {
        for (int i = 0; i < 5; i++) {
            given().contentType(ContentType.JSON).header("X-Forwarded-For", "203.0.113." + i)
                .header("X-Real-IP", "203.0.113." + i)
                .body(Map.of("email", "user" + i + "@example.com", "password", WRONG))
                .post("/api/admin/auth/login").then().statusCode(401);
        }

        given().contentType(ContentType.JSON).header("X-Forwarded-For", "198.51.100.77")
            .body(Map.of("email", EMAIL, "password", PASSWORD))
            .post("/api/admin/auth/login").then().statusCode(429).body("code", equalTo("too_many_login_attempts"));
        // 감사 IP도 위조 값이 아니라 실제 피어 주소다
        List<String> ips = jdbc.queryForList("SELECT DISTINCT ip FROM audit_logs", String.class);
        assertThat(ips).hasSize(1).first().isIn("127.0.0.1", "0:0:0:0:0:0:0:1");
    }

    @Test
    void 잠금_시작은_auth_login_locked_한_행이고_거절된_시도는_감사_로그에_쓰지_않는다() {
        failTimes(EMAIL, 3);
        for (int i = 0; i < 4; i++) {
            login(EMAIL, WRONG).then().statusCode(429);
        }

        assertThat(audit("auth.login.failure")).isEqualTo(3);
        assertThat(audit("auth.login.locked")).isEqualTo(1);
        Map<String, Object> row = jdbc.queryForMap(
            "SELECT operator_id, target_type, detail::text AS detail, ip FROM audit_logs WHERE action = 'auth.login.locked'");
        assertThat(row.get("operator_id")).isNull();
        assertThat(row.get("target_type")).isEqualTo("operator");
        assertThat((String) row.get("detail"))
            .contains("\"email\": \"o***@example.com\"", "\"scopes\": [\"email\"]", "\"windowMinutes\": 15")
            .doesNotContain(EMAIL);
        assertThat((String) row.get("ip")).isIn("127.0.0.1", "0:0:0:0:0:0:0:1");
    }

    @Test
    void 거절은_1분_단위_건수_한_줄로만_남고_로그에_이메일_원문과_비밀번호가_없다(CapturedOutput output) {
        String email = "secret.person@example.com";
        failTimes(email, 3);
        for (int i = 0; i < 3; i++) {
            login(email, PASSWORD).then().statusCode(429);
        }
        clock.advance(Duration.ofSeconds(61));
        login(email, PASSWORD).then().statusCode(429);

        assertThat(output.getAll())
            .contains("로그인 시도 제한 시작: 범위=[email]")
            .contains("로그인 시도 제한 거절: 지난 기록 이후")
            .doesNotContain(email, PASSWORD, WRONG);
    }
}
