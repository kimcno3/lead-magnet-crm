package com.leadmagnet.crm.scenario;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import com.leadmagnet.crm.support.IntegrationTest;
import com.leadmagnet.crm.support.PublicOrigin;
import com.leadmagnet.crm.support.ScenarioBase;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 실제로 폼이 운영되는 중에도 두 출처가 서로의 경로를 내주지 않는다. */
@IntegrationTest
class IsolationScenarioTest extends ScenarioBase {

    String token;
    int campaign;
    String code;

    @BeforeEach
    void setUp() {
        token = login(EMAIL, PASSWORD).then().statusCode(200).extract().path("accessToken");
        campaign = authedJson(token).body(Map.of("name", "봄 특강")).post("/api/admin/campaigns")
            .then().statusCode(201).extract().path("id");
        int form = createForm(token, campaign, uploadTemplate(token, AI_FORM_HTML), "spring-2026");
        authedJson(token).body(Map.of("status", "published")).patch("/api/admin/forms/{id}", form).then().statusCode(200);
        code = createLink(token, form, "instagram").then().statusCode(201).extract().path("code");
    }

    @Test
    void public_포트로는_유효한_토큰이_있어도_관리자_API가_404다() {
        PublicOrigin.given(publicPort).auth().oauth2(token).get("/api/admin/campaigns/{id}/stats", campaign)
            .then().statusCode(404);
        PublicOrigin.given(publicPort).auth().oauth2(token).get("/api/admin/campaigns/{id}/leads", campaign)
            .then().statusCode(404);
        Response login = PublicOrigin.given(publicPort).contentType(ContentType.JSON)
            .body(Map.of("email", EMAIL, "password", PASSWORD)).post("/api/admin/auth/login");
        login.then().statusCode(404);
        assertThat(login.getCookie(REFRESH_COOKIE)).isNull();
    }

    @Test
    void admin_포트로는_게시된_폼의_공개_경로가_404이고_방문도_신청도_남지_않는다() {
        given().get("/f/spring-2026").then().statusCode(404);
        given().get("/l/{code}", code).then().statusCode(404);
        given().get("/api/public/forms/spring-2026").then().statusCode(404);
        given().contentType(ContentType.JSON)
            .body(Map.of("fields", Map.of("name", "홍", "phone", "010-6666-0001"), "consent", true))
            .post("/api/public/forms/spring-2026/submissions").then().statusCode(404);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM visits", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM leads", Integer.class)).isZero();
    }

    @Test
    void 공개_출처에서_온_CORS_요청은_관리자_API가_허용하지_않는다() {
        given().header("Origin", publicOrigin()).header("Access-Control-Request-Method", "GET")
            .header("Access-Control-Request-Headers", "Authorization").options("/api/admin/campaigns")
            .then().statusCode(403).header("Access-Control-Allow-Origin", nullValue());
        given().header("Origin", publicOrigin()).auth().oauth2(token).get("/api/admin/campaigns/{id}/leads", campaign)
            .then().statusCode(403).header("Access-Control-Allow-Origin", nullValue());
    }

    @Test
    void 공개_응답에는_CSP와_보안_헤더가_붙고_래퍼는_allow_same_origin_없는_sandbox만_쓴다() {
        Response page = PublicOrigin.given(publicPort).get("/f/spring-2026");
        page.then().statusCode(200).header("X-Frame-Options", containsString("DENY"))
            .header("X-Content-Type-Options", containsString("nosniff"))
            .body(containsString("sandbox=\"allow-scripts allow-forms\""))
            .body(not(containsString("allow-same-origin")));
        assertThat(page.getHeader("Content-Security-Policy")).contains("default-src 'none'").contains("connect-src 'self'");

        // 업로드한 HTML(인라인 script 포함)은 셸이 아니라 JSON 응답으로만 나간다
        PublicOrigin.given(publicPort).get("/api/public/forms/spring-2026").then().statusCode(200)
            .header("Content-Security-Policy", containsString("default-src"));
        assertThat(page.asString()).doesNotContain("addEventListener");

        // 관리자 응답에는 공개 CSP가 없다
        authed(token).get("/api/admin/campaigns").then().statusCode(200).header("Content-Security-Policy", nullValue());
    }

    @Test
    void 공개_포트의_신청은_Origin이_null이면_403이고_저장하지_않는다() {
        newVisitor().request().contentType(ContentType.JSON).header("Origin", "null")
            .body(Map.of("fields", Map.of("name", "홍", "phone", "010-6666-0002"), "consent", true))
            .post("/api/public/forms/spring-2026/submissions").then().statusCode(403);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM leads", Integer.class)).isZero();
    }
}
