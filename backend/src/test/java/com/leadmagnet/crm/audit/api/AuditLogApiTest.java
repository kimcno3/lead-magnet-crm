package com.leadmagnet.crm.audit.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;

import com.leadmagnet.crm.audit.application.AuditService;
import com.leadmagnet.crm.audit.domain.AuditEvent;
import com.leadmagnet.crm.auth.domain.Operator;
import com.leadmagnet.crm.common.web.AuditActor;
import com.leadmagnet.crm.auth.domain.OperatorRepository;
import com.leadmagnet.crm.common.web.PublicPort;
import com.leadmagnet.crm.support.AdminFixtures;
import com.leadmagnet.crm.support.AdminLogin;
import com.leadmagnet.crm.support.DatabaseCleaner;
import com.leadmagnet.crm.support.IntegrationTest;
import com.leadmagnet.crm.support.PublicOrigin;
import com.leadmagnet.crm.support.FormTokens;
import com.leadmagnet.crm.support.MutableClock;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
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
import org.springframework.transaction.support.TransactionTemplate;

/** 감사 로그 기록(명단 조회, 원문 열람, 로그인)과 조회 API (ADR 0016). CSV 다운로드는 LeadExportApiTest. */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class AuditLogApiTest {

    static final String HTML = """
        <form><input name="name"><input name="phone" type="tel"><input name="email" type="email"></form>
        """;

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

    @Autowired
    AuditService audit;

    @Autowired
    TransactionTemplate transactions;

    String token;
    long operatorId;
    int campaign;
    int formId;

    @BeforeEach
    void setUp() {
        RestAssured.port = adminPort;
        DatabaseCleaner.truncateAll(jdbc);
        operatorId = operators.save(new Operator("owner@example.com", passwordEncoder.encode("correct-horse-battery"),
            Instant.now())).getId();
        token = AdminLogin.accessToken("owner@example.com", "correct-horse-battery");
        campaign = AdminFixtures.createCampaign(token, "봄 캠페인");
        formId = AdminFixtures.createPublishedFormIn(token, campaign, "form-a", HTML);
    }

    RequestSpecification authed() {
        return RestAssured.given().auth().oauth2(token);
    }

    long submit(Map<String, Object> fields) {
        PublicOrigin.given(publicPort).contentType(ContentType.JSON)
            .body(Map.of("fields", fields, "consent", true, "ft", FormTokens.mature(publicPort, clock, "form-a")))
            .post("/api/public/forms/form-a/submissions").then().statusCode(201);
        return jdbc.queryForObject("SELECT max(id) FROM leads", Long.class);
    }

    Map<String, Object> lastLog(String action) {
        return jdbc.queryForMap("SELECT operator_id, target_type, target_id, campaign_id, detail::text AS detail, ip, "
            + "user_agent FROM audit_logs WHERE action = ? ORDER BY id DESC LIMIT 1", action);
    }

    long count(String action) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = ?", Long.class, action);
    }

    @Test
    void 명단_조회는_필터와_페이지를_lead_list로_남긴다() {
        authed().header("User-Agent", "Mozilla/5.0 " + "x".repeat(600))
            .get("/api/admin/campaigns/{id}/leads?page=0&size=10&formId={f}&channel=instagram", campaign, formId)
            .then().statusCode(200);

        Map<String, Object> log = lastLog("lead.list");
        assertThat(log.get("operator_id")).isEqualTo(operatorId);
        assertThat(log.get("target_type")).isEqualTo("campaign");
        assertThat(log.get("target_id")).isEqualTo((long) campaign);
        assertThat(log.get("campaign_id")).isEqualTo((long) campaign);
        assertThat((String) log.get("detail")).contains("\"page\": 0", "\"size\": 10", "\"formId\": " + formId,
            "\"channel\": \"instagram\"");
        assertThat((String) log.get("ip")).isIn("127.0.0.1", "0:0:0:0:0:0:0:1");
        assertThat((String) log.get("user_agent")).hasSize(512).startsWith("Mozilla/5.0 ");
    }

    @Test
    void 잘못된_명단_조회는_남기지_않는다() {
        authed().get("/api/admin/campaigns/{id}/leads?channel=tiktok", campaign).then().statusCode(422);
        authed().get("/api/admin/campaigns/{id}/leads", 99999).then().statusCode(404);

        assertThat(count("lead.list")).isZero();
    }

    @Test
    void 원문_열람은_신청_id와_캠페인으로_lead_contact_view를_남긴다() {
        long lead = submit(Map.of("name", "홍길동", "phone", "010-1234-5678", "email", "hong@example.com"));

        authed().get("/api/admin/leads/{id}/contact", lead).then().statusCode(200);

        Map<String, Object> log = lastLog("lead.contact.view");
        assertThat(log.get("operator_id")).isEqualTo(operatorId);
        assertThat(log.get("target_type")).isEqualTo("lead");
        assertThat(log.get("target_id")).isEqualTo(lead);
        assertThat(log.get("campaign_id")).isEqualTo((long) campaign);
        assertThat((String) log.get("detail")).doesNotContain("홍길동", "01012345678", "hong@example.com");
    }

    @Test
    void 없는_신청의_원문_열람은_남기지_않는다() {
        authed().get("/api/admin/leads/{id}/contact", 99999).then().statusCode(404);

        assertThat(count("lead.contact.view")).isZero();
    }

    @Test
    void 로그인_성공과_실패를_남기고_실패는_이메일을_가린다(CapturedOutput output) {
        RestAssured.given().contentType(ContentType.JSON)
            .body(Map.of("email", "secret.person@example.com", "password", "wrong-password-123"))
            .post("/api/admin/auth/login").then().statusCode(401);

        Map<String, Object> success = lastLog("auth.login.success");
        assertThat(success.get("operator_id")).isEqualTo(operatorId);
        assertThat(success.get("target_type")).isEqualTo("operator");
        assertThat(success.get("target_id")).isEqualTo(operatorId);

        Map<String, Object> failure = lastLog("auth.login.failure");
        assertThat(failure.get("operator_id")).isNull();
        assertThat(failure.get("target_id")).isNull();
        assertThat((String) failure.get("detail")).contains("s***@example.com").doesNotContain("secret.person");
        assertThat(output.getAll()).doesNotContain("secret.person@example.com");
    }

    @Test
    void 있는_계정의_비밀번호가_틀려도_운영자를_특정하지_않고_가린_이메일만_남긴다(CapturedOutput output) {
        RestAssured.given().contentType(ContentType.JSON)
            .body(Map.of("email", "owner@example.com", "password", "wrong-password-123"))
            .post("/api/admin/auth/login").then().statusCode(401).body("code", equalTo("invalid_credentials"));

        Map<String, Object> failure = lastLog("auth.login.failure");
        assertThat(failure.get("operator_id")).isNull();
        assertThat(failure.get("target_type")).isEqualTo("operator");
        assertThat(failure.get("target_id")).isNull();
        assertThat((String) failure.get("detail")).isEqualTo("{\"email\": \"o***@example.com\"}");
        assertThat(count("auth.login.failure")).isEqualTo(1);
        assertThat(output.getAll()).doesNotContain("owner@example.com\"");
    }

    @Test
    void 감사_로그는_최신순으로_페이지를_나눠_돌려준다() {
        long lead = submit(Map.of("phone", "010-1234-5678"));
        authed().get("/api/admin/campaigns/{id}/leads", campaign).then().statusCode(200);
        authed().get("/api/admin/leads/{id}/contact", lead).then().statusCode(200);

        // 로그인 성공 1 + 명단 1 + 원문 1
        authed().get("/api/admin/audit-logs?size=2").then().statusCode(200)
            .body("totalElements", equalTo(3)).body("totalPages", equalTo(2)).body("size", equalTo(2))
            .body("items.action", contains("lead.contact.view", "lead.list"))
            .body("items[0].operatorId", equalTo((int) operatorId))
            .body("items[0].operatorEmail", equalTo("owner@example.com"))
            .body("items[0].targetType", equalTo("lead")).body("items[0].targetId", equalTo((int) lead))
            .body("items[0].campaignId", equalTo(campaign))
            .body("items[0].createdAt", containsString("T"))
            .body("items[1].detail.page", equalTo(0));
        authed().get("/api/admin/audit-logs?size=2&page=1").then().statusCode(200)
            .body("items.action", contains("auth.login.success"))
            .body("items[0].campaignId", nullValue());
    }

    @Test
    void action_운영자_캠페인_기간으로_거른다() {
        authed().get("/api/admin/campaigns/{id}/leads", campaign).then().statusCode(200);
        int other = AdminFixtures.createCampaign(token, "다른 캠페인");
        authed().get("/api/admin/campaigns/{id}/leads", other).then().statusCode(200);
        jdbc.update("UPDATE audit_logs SET created_at = '2026-01-10T15:30:00Z' WHERE campaign_id = ?", other);

        authed().get("/api/admin/audit-logs?action=lead.list&campaignId={c}", campaign).then().statusCode(200)
            .body("totalElements", equalTo(1)).body("items[0].campaignId", equalTo(campaign));
        authed().get("/api/admin/audit-logs?operatorId={o}", operatorId).then().statusCode(200)
            .body("totalElements", equalTo(3));
        authed().get("/api/admin/audit-logs?operatorId=99999").then().statusCode(200)
            .body("totalElements", equalTo(0)).body("items", equalTo(List.of()));
        // 2026-01-10T15:30Z = 서울 2026-01-11 00:30
        authed().get("/api/admin/audit-logs?from=2026-01-11&to=2026-01-11").then().statusCode(200)
            .body("totalElements", equalTo(1)).body("items[0].campaignId", equalTo(other));
        authed().get("/api/admin/audit-logs?to=2026-01-10").then().statusCode(200).body("totalElements", equalTo(0));
    }

    @Test
    void 명단_조회_로그인_기록은_트랜잭션_안에서_부르면_거절한다() {
        AuditActor actor = new AuditActor((long) operatorId, "127.0.0.1", "test");

        assertThatThrownBy(() -> transactions.executeWithoutResult(
            status -> audit.recordBestEffort(actor, AuditEvent.loginSuccess(operatorId))))
            .isInstanceOf(IllegalStateException.class);
        audit.recordBestEffort(actor, AuditEvent.loginSuccess(operatorId));
        assertThat(count("auth.login.success")).isEqualTo(2);
    }

    @Test
    void 잘못된_필터는_422다() {
        authed().get("/api/admin/audit-logs?action=lead.delete").then().statusCode(422)
            .body("code", equalTo("validation_failed")).body("errors.field", hasItem("action"));
        authed().get("/api/admin/audit-logs?page=-1&size=101").then().statusCode(422)
            .body("errors.field", hasItem("page")).body("errors.field", hasItem("size"));
        authed().get("/api/admin/audit-logs?from=2026-02-01&to=2026-01-01").then().statusCode(422)
            .body("errors.field", hasItem("from"));
        authed().get("/api/admin/audit-logs?from=yesterday").then().statusCode(422)
            .body("errors.field", hasItem("from"));
    }

    @Test
    void 인증_없이는_401이고_public_포트에서는_404다() {
        RestAssured.get("/api/admin/audit-logs").then().statusCode(401)
            .contentType(containsString("application/problem+json"));
        PublicOrigin.given(publicPort).auth().oauth2(token).get("/api/admin/audit-logs").then().statusCode(404);
    }

    @Test
    void 감사_로그_조회_자체는_남기지_않고_수정_삭제_API가_없다() {
        authed().get("/api/admin/audit-logs").then().statusCode(200);
        long before = jdbc.queryForObject("SELECT count(*) FROM audit_logs", Long.class);
        authed().get("/api/admin/audit-logs").then().statusCode(200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs", Long.class)).isEqualTo(before);

        authed().delete("/api/admin/audit-logs/1").then().statusCode(org.hamcrest.Matchers.anyOf(equalTo(404),
            equalTo(405)));
        authed().contentType(ContentType.JSON).body("{}").patch("/api/admin/audit-logs/1").then()
            .statusCode(org.hamcrest.Matchers.anyOf(equalTo(404), equalTo(405)));
    }

    @Test
    void 원문_열람_감사_기록과_로그에_원문_개인정보가_없다(CapturedOutput output) {
        long lead = submit(Map.of("name", "홍길동", "phone", "010-1234-5678", "email", "secret.person@example.com"));

        authed().get("/api/admin/campaigns/{id}/leads", campaign).then().statusCode(200);
        authed().get("/api/admin/leads/{id}/contact", lead).then().statusCode(200);
        authed().get("/api/admin/audit-logs").then().statusCode(200);

        String all = String.join("\n", jdbc.queryForList("SELECT detail::text FROM audit_logs", String.class));
        assertThat(all).doesNotContain("홍길동", "01012345678", "010-1234-5678", "secret.person");
        assertThat(output.getAll()).doesNotContain("홍길동", "01012345678", "010-1234-5678",
            "secret.person@example.com");
    }
}
