package com.leadmagnet.crm.common.web;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import com.leadmagnet.crm.auth.domain.Operator;
import com.leadmagnet.crm.auth.domain.OperatorRepository;
import com.leadmagnet.crm.support.AdminFixtures;
import com.leadmagnet.crm.support.AdminLogin;
import com.leadmagnet.crm.support.DatabaseCleaner;
import com.leadmagnet.crm.support.IntegrationTest;
import com.leadmagnet.crm.support.PublicOrigin;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 출처 격리(ADR 0010): 포트마다 정해진 경로만 응답하고 나머지는 404다. 공개 응답에는 보안 헤더가 붙는다(ADR 0002). */
@IntegrationTest
class PublicOriginIsolationTest {

    static final String EMAIL = "owner@example.com";
    static final String PASSWORD = "correct-horse-battery";
    static final String PROBLEM_JSON = "application/problem+json";
    static final String CSP = "default-src 'none'; script-src 'self' 'unsafe-inline'; "
        + "style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:; "
        + "connect-src 'self'; frame-src 'self'; form-action 'none'; base-uri 'none'; frame-ancestors 'none'";

    @LocalServerPort
    int adminPort;

    @Autowired
    PublicPort publicPort;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    OperatorRepository operators;

    @Autowired
    PasswordEncoder passwordEncoder;

    String token;
    int formId;
    String code;

    @BeforeEach
    void setUp() {
        RestAssured.port = adminPort;
        DatabaseCleaner.truncateAll(jdbc);
        operators.save(new Operator(EMAIL, passwordEncoder.encode(PASSWORD)));
        token = AdminLogin.accessToken(EMAIL, PASSWORD);
        formId = AdminFixtures.createPublishedForm(token, "spring-2026");
        code = AdminFixtures.createLink(token, formId, "x");
    }

    @Test
    void public_커넥터는_admin과_다른_포트에서_뜬다() {
        assertThat(publicPort.get()).isPositive().isNotEqualTo(adminPort);
    }

    @Test
    void public_포트로_관리자_헬스_문서_경로를_요청하면_404다() {
        for (String path : List.of("/api/admin/auth/me", "/api/admin/campaigns", "/actuator/health",
                "/actuator/env", "/v3/api-docs", "/swagger-ui/index.html", "/swagger-ui.html", "/error", "/",
                "/favicon.ico")) {
            PublicOrigin.given(publicPort).get(path)
                .then().statusCode(404).contentType(PROBLEM_JSON)
                .body("code", equalTo("not_found"))
                .header("Content-Security-Policy", equalTo(CSP));
        }
    }

    @Test
    void public_포트로_유효한_토큰을_보내도_관리자_API는_404다() {
        PublicOrigin.given(publicPort).auth().oauth2(token).get("/api/admin/auth/me")
            .then().statusCode(404).body("code", equalTo("not_found"));
    }

    @Test
    void public_포트로_로그인하면_404이고_refresh_쿠키를_주지_않는다() {
        Integer tokensBefore = jdbc.queryForObject("SELECT count(*) FROM refresh_tokens", Integer.class);
        Response response = PublicOrigin.given(publicPort).contentType(ContentType.JSON)
            .body(Map.of("email", EMAIL, "password", PASSWORD))
            .post("/api/admin/auth/login");
        response.then().statusCode(404).body("accessToken", nullValue());
        assertNoRefreshCookie(response);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens", Integer.class)).isEqualTo(tokensBefore);
    }

    @Test
    void public_포트의_공개_응답에는_refresh_쿠키가_없다() {
        assertNoRefreshCookie(PublicOrigin.given(publicPort).get("/f/spring-2026"));
        assertNoRefreshCookie(PublicOrigin.given(publicPort).get("/l/{code}", code));
        assertNoRefreshCookie(PublicOrigin.given(publicPort).get("/api/public/forms/spring-2026"));
        assertNoRefreshCookie(PublicOrigin.given(publicPort).post("/api/admin/auth/refresh"));
        assertNoRefreshCookie(PublicOrigin.given(publicPort).post("/api/admin/auth/logout"));
    }

    @Test
    void admin_포트로_공개_경로를_요청하면_404다() {
        for (String path : List.of("/f/spring-2026", "/f/spring-2026?lm=" + code, "/l/" + code,
                "/api/public/forms/spring-2026", "/f/_assets/form-wrapper.js")) {
            given().redirects().follow(false).get(path)
                .then().statusCode(404).contentType(PROBLEM_JSON).body("code", equalTo("not_found"));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM visits", Integer.class)).isZero();
    }

    @Test
    void admin_포트의_관리자_API와_헬스_문서는_그대로_동작한다() {
        given().auth().oauth2(token).get("/api/admin/auth/me").then().statusCode(200);
        given().get("/api/admin/auth/me").then().statusCode(401);
        given().get("/actuator/health").then().statusCode(200);
        given().get("/v3/api-docs").then().statusCode(200);
    }

    @Test
    void admin_포트_응답에는_공개_CSP를_붙이지_않는다() {
        given().get("/actuator/health").then().statusCode(200).header("Content-Security-Policy", nullValue());
    }

    @Test
    void public_출처에서_관리자_API로_보낸_CORS_프리플라이트는_거부한다() {
        for (String origin : List.of("http://localhost:8081", "http://localhost:" + publicPort.get())) {
            given()
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Content-Type")
                .options("/api/admin/auth/login")
                .then().statusCode(403)
                .header("Access-Control-Allow-Origin", nullValue())
                .header("Access-Control-Allow-Credentials", nullValue());
            PublicOrigin.given(publicPort)
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "POST")
                .options("/api/admin/auth/login")
                .then().statusCode(404)
                .header("Access-Control-Allow-Origin", nullValue());
        }
    }

    @Test
    void public_출처의_실제_요청에도_관리자_API는_CORS_허용_헤더를_주지_않는다() {
        given().header("Origin", "http://localhost:8081").auth().oauth2(token)
            .get("/api/admin/auth/me")
            .then().statusCode(403).header("Access-Control-Allow-Origin", nullValue());
    }

    @Test
    void 공개_경로는_Authorization_헤더를_무시한다() {
        PublicOrigin.given(publicPort).header("Authorization", "Bearer not-a-jwt")
            .get("/api/public/forms/spring-2026")
            .then().statusCode(200).header("WWW-Authenticate", nullValue());
        PublicOrigin.given(publicPort).header("Authorization", "Bearer not-a-jwt")
            .get("/f/spring-2026").then().statusCode(200);
    }

    @Test
    void 공개_경로를_빌린_경로_조작도_관리자_API에_닿지_않는다() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        for (String raw : List.of("/f/%2e%2e/api/admin/auth/me", "/f/..;/api/admin/auth/me",
                "/f/../api/admin/auth/me", "/api/public/%2e%2e/%2e%2e/actuator/health", "/l/..%2f..%2factuator/health",
                "/F/spring-2026", "/api/publicx/forms")) {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + publicPort.get() + raw))
                .header("Authorization", "Bearer " + token).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).as(raw).isIn(400, 404);
            assertThat(response.body()).as(raw).doesNotContain("operatorId").doesNotContain("\"UP\"");
        }
    }

    @Test
    void 정규화하면_다른_출처의_경로가_되는_원문_URI는_양쪽_포트에서_핸들러에_닿지_않는다() throws Exception {
        String slug = "spring-2026";
        // 원문 경로 → 실제 상태. OriginGuard는 Tomcat이 ;파라미터·..·%2e·//를 정규화한 경로로 판단한다(404).
        // 가드를 지나도 원문이 정규화되지 않았거나 ;가 있으면 Spring Security 방화벽이 400으로 거부한다.
        Map<String, Integer> admin = new LinkedHashMap<>();
        admin.put("/api/admin/x/..;/../f/" + slug, 400); // /api/f/{slug}로 정규화 → 가드 통과 → 방화벽 400
        admin.put("/api/admin/%2e%2e/%2e%2e/f/" + slug, 404);
        admin.put("/f;a=b/" + slug, 404);
        Map<String, Integer> pub = new LinkedHashMap<>();
        pub.put("/api/admin/auth/me/..;/../../../f/" + slug, 400);
        pub.put("//api/admin/auth/me", 404);
        pub.put("/f;a=b/" + slug, 400);

        HttpClient client = HttpClient.newHttpClient();
        Map<String, Integer> actual = new LinkedHashMap<>();
        Map<String, Integer> expected = new LinkedHashMap<>();
        for (Map.Entry<Integer, Map<String, Integer>> port : Map.of(adminPort, admin, publicPort.get(), pub).entrySet()) {
            String name = port.getKey() == adminPort ? "admin " : "public ";
            for (Map.Entry<String, Integer> path : port.getValue().entrySet()) {
                HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port.getKey() + path.getKey()))
                    .header("Authorization", "Bearer " + token).GET().build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                actual.put(name + path.getKey(), response.statusCode());
                expected.put(name + path.getKey(), path.getValue());
                assertThat(response.body()).as(name + path.getKey())
                    .doesNotContain("operatorId").doesNotContain("lm-frame");
            }
        }
        assertThat(actual).isEqualTo(expected);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM visits", Integer.class)).isZero();
    }

    @Test
    void public_포트의_공개_응답에는_보안_헤더가_모두_붙는다() {
        int closed = AdminFixtures.createPublishedForm(token, "closed-form");
        AdminFixtures.changeStatus(token, closed, "closed");
        List<Response> responses = List.of(
            PublicOrigin.given(publicPort).get("/f/spring-2026"),
            PublicOrigin.given(publicPort).get("/f/no-such-form"),
            PublicOrigin.given(publicPort).get("/f/_assets/form-wrapper.js"),
            PublicOrigin.given(publicPort).get("/api/public/forms/spring-2026"),
            PublicOrigin.given(publicPort).get("/l/{code}", code),
            PublicOrigin.given(publicPort).get("/api/admin/auth/me"),
            PublicOrigin.given(publicPort).get("/f/closed-form"));
        responses.get(responses.size() - 1).then().statusCode(410);
        for (Response response : responses) {
            response.then()
                .header("Content-Security-Policy", equalTo(CSP))
                .header("X-Content-Type-Options", equalTo("nosniff"))
                .header("X-Frame-Options", equalTo("DENY"))
                .header("Referrer-Policy", equalTo("strict-origin-when-cross-origin"));
            assertThat(response.headers().getValues("Content-Security-Policy")).hasSize(1);
            assertThat(response.headers().getValues("X-Frame-Options")).hasSize(1);
        }
    }

    @Test
    void 래퍼_셸은_allow_same_origin_없이_샌드박스_iframe만_쓴다() {
        String html = PublicOrigin.given(publicPort).get("/f/spring-2026")
            .then().statusCode(200).contentType(containsString("text/html"))
            .body(containsString("sandbox=\"allow-scripts allow-forms\""))
            .body(not(containsString("allow-same-origin")))
            .extract().asString();
        assertThat(html).contains("<script src=\"/f/_assets/form-wrapper.js\" defer></script>");
        // 셸에는 업로드 HTML이나 폼 값이 서버에서 섞이지 않는다.
        assertThat(html).doesNotContain("봄 특강 신청").doesNotContain("<form").doesNotContain("spring-2026");
    }

    private static void assertNoRefreshCookie(Response response) {
        assertThat(response.headers().getValues("Set-Cookie")).noneMatch(c -> c.startsWith("lm_refresh"));
        assertThat(response.getCookie("lm_refresh")).isNull();
    }
}
