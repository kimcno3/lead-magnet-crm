package com.leadmagnet.crm.audit.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.leadmagnet.crm.audit.domain.AuditLogRepository;
import com.leadmagnet.crm.auth.domain.Operator;
import com.leadmagnet.crm.auth.domain.OperatorRepository;
import com.leadmagnet.crm.support.AdminFixtures;
import com.leadmagnet.crm.support.AdminLogin;
import com.leadmagnet.crm.support.DatabaseCleaner;
import com.leadmagnet.crm.support.IntegrationTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** 감사 공백 메우기(ADR 0016 변경 기록): refresh 재사용, 로그아웃, 폼 게시·마감, 템플릿 새 버전, 크리에이터 보관. */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class AuditGapsApiTest {

    static final String EMAIL = "owner@example.com";
    static final String PASSWORD = "correct-horse-battery";
    static final String COOKIE = "lm_refresh";
    static final String V1 = "<form><input name=\"name\"><input name=\"phone\" type=\"tel\"></form>";
    static final String V2 = "<form><input name=\"name\"><input name=\"phone\" type=\"tel\"><input name=\"memo\"></form>";

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    OperatorRepository operators;

    @Autowired
    PasswordEncoder passwordEncoder;

    @MockitoSpyBean
    AuditLogRepository auditLogs;

    long operatorId;
    String token;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        DatabaseCleaner.truncateAll(jdbc);
        operatorId = operators.save(new Operator(EMAIL, passwordEncoder.encode(PASSWORD), Instant.now())).getId();
        token = AdminLogin.accessToken(EMAIL, PASSWORD);
    }

    RequestSpecification authed() {
        return RestAssured.given().auth().oauth2(token).contentType(ContentType.JSON);
    }

    Response login() {
        return RestAssured.given().contentType(ContentType.JSON).body(Map.of("email", EMAIL, "password", PASSWORD))
            .post("/api/admin/auth/login");
    }

    Response refresh(String cookie) {
        return RestAssured.given().cookie(COOKIE, cookie).post("/api/admin/auth/refresh");
    }

    Map<String, Object> lastLog(String action) {
        return jdbc.queryForMap("SELECT operator_id, target_type, target_id, campaign_id, detail::text AS detail "
            + "FROM audit_logs WHERE action = ? ORDER BY id DESC LIMIT 1", action);
    }

    long count(String action) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = ?", Long.class, action);
    }

    long activeRefreshTokens() {
        return jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE revoked_at IS NULL", Long.class);
    }

    void failAuditWrites() {
        doThrow(new DataAccessResourceFailureException("audit store down")).when(auditLogs).saveAndFlush(any());
    }

    // --- auth.refresh.reused

    @Test
    void refresh_토큰_재사용을_감지하면_운영자_id로_auth_refresh_reused를_남기고_응답은_401이다() {
        String a = login().getCookie(COOKIE);
        refresh(a).then().statusCode(200);

        refresh(a).then().statusCode(401).body("code", equalTo("refresh_token_reused"));

        Map<String, Object> log = lastLog("auth.refresh.reused");
        assertThat(log.get("operator_id")).isEqualTo(operatorId);
        assertThat(log.get("target_type")).isEqualTo("operator");
        assertThat(log.get("target_id")).isEqualTo(operatorId);
        assertThat((String) log.get("detail")).isEqualTo("{}");
        assertThat(count("auth.refresh.reused")).isEqualTo(1);
    }

    @Test
    void 이미_폐기된_패밀리의_토큰을_다시_보내면_401이지만_재사용_기록은_한_번만_남는다() {
        String a = login().getCookie(COOKIE);
        refresh(a).then().statusCode(200);
        refresh(a).then().statusCode(401).body("code", equalTo("refresh_token_reused"));

        refresh(a).then().statusCode(401).body("code", equalTo("refresh_token_reused"));
        refresh(a).then().statusCode(401).body("code", equalTo("refresh_token_reused"));

        assertThat(count("auth.refresh.reused")).isEqualTo(1);
    }

    @Test
    void 모르는_refresh_토큰은_재사용_기록을_남기지_않는다() {
        refresh("unknown-token").then().statusCode(401).body("code", equalTo("invalid_refresh_token"));

        assertThat(count("auth.refresh.reused")).isZero();
    }

    @Test
    void 재사용_기록이_실패해도_401_refresh_token_reused이고_패밀리는_폐기된다(CapturedOutput output) {
        String a = login().getCookie(COOKIE);
        refresh(a).then().statusCode(200);
        failAuditWrites();

        refresh(a).then().statusCode(401).body("code", equalTo("refresh_token_reused"));

        assertThat(output.getAll()).contains("감사 로그 기록 실패").contains("auth.refresh.reused");
        assertThat(activeRefreshTokens()).isEqualTo(1); // setUp 로그인의 패밀리만 남는다
    }

    // --- auth.logout

    @Test
    void 로그아웃은_쿠키가_운영자로_확인되면_auth_logout을_남긴다() {
        String cookie = login().getCookie(COOKIE);

        RestAssured.given().cookie(COOKIE, cookie).post("/api/admin/auth/logout").then().statusCode(204);

        Map<String, Object> log = lastLog("auth.logout");
        assertThat(log.get("operator_id")).isEqualTo(operatorId);
        assertThat(log.get("target_type")).isEqualTo("operator");
        assertThat(log.get("target_id")).isEqualTo(operatorId);
        assertThat((String) log.get("detail")).isEqualTo("{}");
    }

    @Test
    void 이미_로그아웃한_쿠키로_다시_로그아웃하면_204이고_두_번째는_기록하지_않는다() {
        String cookie = login().getCookie(COOKIE);
        RestAssured.given().cookie(COOKIE, cookie).post("/api/admin/auth/logout").then().statusCode(204);

        RestAssured.given().cookie(COOKIE, cookie).post("/api/admin/auth/logout").then().statusCode(204);

        assertThat(count("auth.logout")).isEqualTo(1);
    }

    @Test
    void 쿠키가_없거나_모르는_토큰의_로그아웃은_204이고_기록하지_않는다() {
        RestAssured.post("/api/admin/auth/logout").then().statusCode(204);
        RestAssured.given().cookie(COOKIE, "unknown-token").post("/api/admin/auth/logout").then().statusCode(204);

        assertThat(count("auth.logout")).isZero();
    }

    @Test
    void 로그아웃_기록이_실패해도_204이고_토큰은_폐기된다(CapturedOutput output) {
        String cookie = login().getCookie(COOKIE);
        failAuditWrites();

        RestAssured.given().cookie(COOKIE, cookie).post("/api/admin/auth/logout").then().statusCode(204);

        assertThat(output.getAll()).contains("감사 로그 기록 실패").contains("auth.logout");
        assertThat(activeRefreshTokens()).isEqualTo(1); // setUp 로그인의 패밀리만 남는다
    }

    // --- form.publish / form.close

    @Test
    void 폼_게시와_마감은_폼_id와_slug로_각각_남긴다() {
        int campaign = AdminFixtures.createCampaign(token, "봄");
        int form = AdminFixtures.createDraftFormIn(token, campaign, "spring-a", AdminFixtures.FORM_HTML);

        AdminFixtures.changeStatus(token, form, "published");
        Map<String, Object> publish = lastLog("form.publish");
        AdminFixtures.changeStatus(token, form, "closed");
        Map<String, Object> close = lastLog("form.close");

        for (Map<String, Object> log : java.util.List.of(publish, close)) {
            assertThat(log.get("operator_id")).isEqualTo(operatorId);
            assertThat(log.get("target_type")).isEqualTo("form");
            assertThat(log.get("target_id")).isEqualTo((long) form);
            assertThat(log.get("campaign_id")).isEqualTo((long) campaign);
            assertThat((String) log.get("detail")).contains("\"formId\": " + form, "\"slug\": \"spring-a\"");
        }
        assertThat(count("form.publish")).isEqualTo(1);
        assertThat(count("form.close")).isEqualTo(1);
    }

    @Test
    void 허용되지_않은_폼_전이와_내용_수정은_남기지_않는다() {
        int form = AdminFixtures.createDraftForm(token, "spring-b");

        authed().body(Map.of("status", "closed")).patch("/api/admin/forms/{id}", form).then().statusCode(409);
        authed().body(Map.of("title", "새 제목")).patch("/api/admin/forms/{id}", form).then().statusCode(200);

        assertThat(count("form.publish")).isZero();
        assertThat(count("form.close")).isZero();
    }

    @Test
    void 폼_게시_기록이_실패하면_게시도_롤백되어_500이다() {
        int form = AdminFixtures.createDraftForm(token, "spring-c");
        failAuditWrites();

        authed().body(Map.of("status", "published")).patch("/api/admin/forms/{id}", form).then().statusCode(500)
            .body("code", equalTo("internal_error"));

        assertThat(jdbc.queryForObject("SELECT status FROM forms WHERE id = ?", String.class, form)).isEqualTo("draft");
        assertThat(jdbc.queryForObject("SELECT published_at FROM forms WHERE id = ?", Object.class, form)).isNull();
    }

    @Test
    void 폼_마감_기록이_실패하면_마감도_롤백되어_500이다() {
        int form = AdminFixtures.createPublishedForm(token, "spring-d");
        failAuditWrites();

        authed().body(Map.of("status", "closed")).patch("/api/admin/forms/{id}", form).then().statusCode(500)
            .body("code", equalTo("internal_error"));

        assertThat(jdbc.queryForObject("SELECT status FROM forms WHERE id = ?", String.class, form))
            .isEqualTo("published");
    }

    // --- template.version.upload

    int createTemplate() {
        return RestAssured.given().auth().oauth2(token)
            .multiPart("file", "t.html", V1.getBytes(StandardCharsets.UTF_8), "text/html")
            .post("/api/admin/templates").then().statusCode(201).extract().path("id");
    }

    Response uploadVersion(int template, String html) {
        return RestAssured.given().auth().oauth2(token)
            .multiPart("file", "t2.html", html.getBytes(StandardCharsets.UTF_8), "text/html")
            .put("/api/admin/templates/{id}", template);
    }

    @Test
    void 템플릿_새_버전은_버전과_영향받은_draft_수를_남긴다() {
        int template = createTemplate();
        int campaign = AdminFixtures.createCampaign(token, "봄");
        for (String slug : new String[] {"tv-a", "tv-b"}) {
            authed().body(Map.of("templateId", template, "title", "t", "consentText", "c", "slug", slug))
                .post("/api/admin/campaigns/{id}/forms", campaign).then().statusCode(201);
        }

        uploadVersion(template, V2).then().statusCode(200).body("affectedDraftForms", equalTo(2));

        Map<String, Object> log = lastLog("template.version.upload");
        assertThat(log.get("operator_id")).isEqualTo(operatorId);
        assertThat(log.get("target_type")).isEqualTo("template");
        assertThat(log.get("target_id")).isEqualTo((long) template);
        assertThat(log.get("campaign_id")).isNull();
        assertThat((String) log.get("detail")).contains("\"templateId\": " + template, "\"version\": 2",
            "\"affectedDraftForms\": 2");
    }

    @Test
    void 계약을_어긴_새_버전과_없는_템플릿은_남기지_않는다() {
        int template = createTemplate();

        uploadVersion(template, "<p>폼 없음</p>").then().statusCode(422);
        uploadVersion(99999, V2).then().statusCode(404);

        assertThat(count("template.version.upload")).isZero();
    }

    @Test
    void 템플릿_새_버전_기록이_실패하면_업로드도_롤백되어_500이다() {
        int template = createTemplate();
        failAuditWrites();

        uploadVersion(template, V2).then().statusCode(500).body("code", equalTo("internal_error"));

        assertThat(jdbc.queryForObject("SELECT version FROM html_templates WHERE id = ?", Integer.class, template))
            .isEqualTo(1);
    }

    // --- creator.archive

    int createCreator() {
        return authed().body(Map.of("name", "김하늘", "handle", "@sky_kim")).post("/api/admin/creators")
            .then().statusCode(201).extract().path("id");
    }

    @Test
    void 크리에이터_보관은_creator_id를_남기고_이름이나_핸들은_남기지_않는다() {
        int creator = createCreator();

        authed().body(Map.of("status", "archived")).patch("/api/admin/creators/{id}", creator).then().statusCode(200);

        Map<String, Object> log = lastLog("creator.archive");
        assertThat(log.get("operator_id")).isEqualTo(operatorId);
        assertThat(log.get("target_type")).isEqualTo("creator");
        assertThat(log.get("target_id")).isEqualTo((long) creator);
        assertThat((String) log.get("detail")).isEqualTo("{\"creatorId\": " + creator + "}");
    }

    @Test
    void 크리에이터_이름_수정과_이미_보관된_크리에이터의_보관은_남기지_않는다() {
        int creator = createCreator();
        authed().body(Map.of("name", "박바다")).patch("/api/admin/creators/{id}", creator).then().statusCode(200);
        authed().body(Map.of("status", "archived")).patch("/api/admin/creators/{id}", creator).then().statusCode(200);
        authed().body(Map.of("status", "archived")).patch("/api/admin/creators/{id}", creator).then().statusCode(409);

        assertThat(count("creator.archive")).isEqualTo(1);
    }

    @Test
    void 크리에이터_보관_기록이_실패하면_보관도_롤백되어_500이다() {
        int creator = createCreator();
        failAuditWrites();

        authed().body(Map.of("status", "archived")).patch("/api/admin/creators/{id}", creator).then().statusCode(500)
            .body("code", equalTo("internal_error"));

        assertThat(jdbc.queryForObject("SELECT status FROM creators WHERE id = ?", String.class, creator))
            .isEqualTo("active");
    }

    // --- 개인정보 없음

    @Test
    void 새_기록의_detail과_로그에는_이름_핸들_이메일_토큰_원문이_없다(CapturedOutput output) {
        String cookie = login().getCookie(COOKIE);
        refresh(cookie);
        refresh(cookie);
        RestAssured.given().cookie(COOKIE, cookie).post("/api/admin/auth/logout");
        int creator = createCreator();
        authed().body(Map.of("status", "archived")).patch("/api/admin/creators/{id}", creator);
        AdminFixtures.createPublishedForm(token, "pii-a");

        String details = String.join("|", jdbc.queryForList(
            "SELECT detail::text FROM audit_logs WHERE action IN ('auth.refresh.reused','auth.logout','creator.archive',"
                + "'form.publish','form.close','template.version.upload')", String.class));
        assertThat(details).doesNotContain("김하늘").doesNotContain("sky_kim").doesNotContain(EMAIL)
            .doesNotContain(cookie);
        assertThat(output.getAll()).doesNotContain(cookie).doesNotContain("김하늘").doesNotContain("sky_kim");
    }
}
