package com.leadmagnet.crm.lead.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import com.leadmagnet.crm.auth.domain.Operator;
import com.leadmagnet.crm.auth.domain.OperatorRepository;
import com.leadmagnet.crm.common.web.PublicPort;
import com.leadmagnet.crm.support.AdminFixtures;
import com.leadmagnet.crm.support.AdminLogin;
import com.leadmagnet.crm.support.DatabaseCleaner;
import com.leadmagnet.crm.support.FormTokens;
import com.leadmagnet.crm.support.IntegrationTest;
import com.leadmagnet.crm.support.MutableClock;
import com.leadmagnet.crm.support.PublicOrigin;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 신청 제출 rate limit (ADR 0019). 다른 테스트는 한도를 크게 두므로 이 클래스만 낮은 한도(IP 4, 방문자 2)로 따로 띄운다.
 * 버킷은 컨텍스트 안에서 계속 살아 있으므로 각 테스트 전에 시계를 1분 넘게 옮겨 버킷을 다시 채운다(되돌리지 않는다:
 * 시계가 뒤로 가면 버킷이 채워지지 않는다. 이 컨텍스트의 Clock은 이 클래스만 쓴다).
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class SubmissionRateLimitTest {

    static final String PATH = "/api/public/forms/{slug}/submissions";
    static final String HTML = AdminFixtures.FORM_HTML;

    @DynamicPropertySource
    static void lowLimits(DynamicPropertyRegistry registry) {
        registry.add("app.public.rate-limit.ip-per-minute", () -> 4);
        registry.add("app.public.rate-limit.visitor-per-minute", () -> 2);
    }

    @LocalServerPort
    int adminPort;

    @Autowired
    PublicPort publicPort;

    @Autowired
    MutableClock clock;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    OperatorRepository operators;

    @Autowired
    PasswordEncoder passwordEncoder;

    String ft;
    int phoneSeq;

    @BeforeEach
    void setUp() {
        clock.advance(Duration.ofSeconds(61));
        RestAssured.port = adminPort;
        DatabaseCleaner.truncateAll(jdbc);
        operators.save(new Operator("owner@example.com", passwordEncoder.encode("correct-horse-battery"), Instant.now()));
        String token = AdminLogin.accessToken("owner@example.com", "correct-horse-battery");
        AdminFixtures.createPublishedForm(token, "spring-2026", HTML);
        ft = FormTokens.mature(publicPort, clock, "spring-2026");
    }

    RequestSpecification json(String visitor) {
        RequestSpecification spec = PublicOrigin.given(publicPort).contentType(ContentType.JSON);
        return visitor == null ? spec : spec.cookie("lm_vid", visitor);
    }

    Map<String, Object> body() {
        return Map.of("fields", Map.of("name", "홍", "phone", "010-1000-%04d".formatted(++phoneSeq)), "consent", true,
            "ft", ft);
    }

    Response submit(String visitor) {
        return json(visitor).body(body()).post(PATH, "spring-2026");
    }

    int leads() {
        return jdbc.queryForObject("SELECT count(*) FROM leads", Integer.class);
    }

    int auditLogs() {
        return jdbc.queryForObject("SELECT count(*) FROM audit_logs", Integer.class);
    }

    @Test
    void 방문자_쿠키당_한도를_넘으면_429_too_many_requests와_Retry_After이고_저장하지_않는다() {
        String visitor = UUID.randomUUID().toString();
        submit(visitor).then().statusCode(201);
        submit(visitor).then().statusCode(201);

        // 분당 2개 = 30초마다 1개. 시계는 실제 시간과 함께 흐르므로 그사이 조금 채워진 만큼 30 이하가 된다.
        String retryAfter = submit(visitor).then().statusCode(429).contentType(containsString("application/problem+json"))
            .body("code", equalTo("too_many_requests"))
            .body("type", equalTo("https://leadmagnet.example/problems/too_many_requests"))
            .body("status", equalTo(429))
            // 429도 공개 응답이라 보안 헤더가 붙는다(보안 헤더 필터가 제출 가드보다 앞이다)
            .header("Content-Security-Policy", containsString("default-src 'none'"))
            .header("X-Content-Type-Options", equalTo("nosniff"))
            .extract().header("Retry-After");
        assertThat(Long.parseLong(retryAfter)).isBetween(25L, 30L);
        assertThat(leads()).isEqualTo(2);

        // 다른 방문자는 자기 한도가 따로 있다(IP 한도 4 안)
        submit(UUID.randomUUID().toString()).then().statusCode(201);
        assertThat(leads()).isEqualTo(3);
    }

    @Test
    void IP당_한도를_넘으면_쿠키와_무관하게_429다() {
        for (int i = 0; i < 4; i++) {
            submit(i % 2 == 0 ? null : UUID.randomUUID().toString()).then().statusCode(201);
        }

        String retryAfter = submit(null).then().statusCode(429).body("code", equalTo("too_many_requests"))
            .extract().header("Retry-After");
        assertThat(Long.parseLong(retryAfter)).isBetween(10L, 15L);
        submit(UUID.randomUUID().toString()).then().statusCode(429);
        assertThat(leads()).isEqualTo(4);
    }

    @Test
    void Retry_After만큼_지나면_다시_받는다() {
        String visitor = UUID.randomUUID().toString();
        submit(visitor).then().statusCode(201);
        submit(visitor).then().statusCode(201);
        String retryAfter = submit(visitor).then().statusCode(429).extract().header("Retry-After");

        clock.advance(Duration.ofSeconds(Long.parseLong(retryAfter)));
        submit(visitor).then().statusCode(201);
    }

    @Test
    void Origin_null_403과_크기_413은_rate_limit보다_먼저이고_토큰을_쓰지_않는다() {
        String big = "{\"fields\":{\"memo\":\"" + "a".repeat(70 * 1024) + "\"},\"consent\":true}";
        for (int i = 0; i < 6; i++) {
            json(null).header("Origin", "null").body(body()).post(PATH, "spring-2026").then().statusCode(403)
                .body("code", equalTo("forbidden_origin"));
            json(null).body(big).post(PATH, "spring-2026").then().statusCode(413);
        }
        for (int i = 0; i < 4; i++) {
            submit(null).then().statusCode(201);
        }

        // 한도를 다 쓴 뒤에도 403·413이 429보다 먼저다
        json(null).header("Origin", "null").body(body()).post(PATH, "spring-2026").then().statusCode(403);
        json(null).body(big).post(PATH, "spring-2026").then().statusCode(413);
        submit(null).then().statusCode(429);
    }

    @Test
    void rate_limit은_토큰_검사보다_먼저다() {
        for (int i = 0; i < 4; i++) {
            submit(null).then().statusCode(201);
        }

        json(null).body(Map.of("fields", Map.of("name", "홍"), "consent", true)).post(PATH, "spring-2026")
            .then().statusCode(429);
    }

    /** 앞 테스트에서 모인 거절 건수는 이 테스트의 첫 제출 때 기록될 수 있어, 먼저 제출 하나로 털어 낸 뒤의 로그만 본다. */
    int flushPending(CapturedOutput output) {
        submit(null).then().statusCode(201);
        return output.getAll().length();
    }

    static long loggedRejections(String log) {
        Matcher m = Pattern.compile("지난 기록 이후 (\\d+)건").matcher(log);
        long sum = 0;
        while (m.find()) {
            sum += Long.parseLong(m.group(1));
        }
        return sum;
    }

    @Test
    void 거절은_감사_로그에_쓰지_않고_애플리케이션_로그에는_1분에_많아야_한_줄_건수만_남긴다(CapturedOutput output) {
        int from = flushPending(output);
        String visitor = UUID.randomUUID().toString();
        int auditBefore = auditLogs();
        submit(visitor).then().statusCode(201);
        submit(visitor).then().statusCode(201);
        for (int i = 0; i < 3; i++) {
            submit(visitor).then().statusCode(429);
        }

        assertThat(auditLogs()).isEqualTo(auditBefore);
        String log = output.getAll().substring(from);
        assertThat(log.split("rate limit 거절", -1).length - 1).isLessThanOrEqualTo(1);
        assertThat(log).doesNotContain(visitor).doesNotContain("010-1000-");
    }

    @Test
    void 기록되지_않고_모인_거절_건수는_1분이_지난_다음_제출_때_남긴다(CapturedOutput output) {
        int from = flushPending(output);
        String visitor = UUID.randomUUID().toString();
        submit(visitor).then().statusCode(201);
        submit(visitor).then().statusCode(201);
        for (int i = 0; i < 4; i++) {
            submit(visitor).then().statusCode(429);
        }

        clock.advance(Duration.ofSeconds(61));
        submit(UUID.randomUUID().toString()).then().statusCode(201);

        // 바로 기록된 것과 1분 뒤 다음 제출 때 기록된 것을 합하면 거절 4건이 모두 남는다
        assertThat(loggedRejections(output.getAll().substring(from))).isEqualTo(4);
    }
}
