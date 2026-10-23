package com.leadmagnet.crm.lead.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import com.leadmagnet.crm.auth.domain.Operator;
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
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcProperties;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 명단 CSV 다운로드 (ADR 0016). 기본은 가린 값, reveal=true면 원문이고 둘 다 감사 로그를 남긴다. */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class LeadExportApiTest {

    static final String HTML = """
        <form><input name="name"><input name="phone" type="tel"><input name="email" type="email">
        <input name="memo"></form>
        """;
    static final String BOM = "﻿";
    static final String HEADER = "신청일,이름,전화번호,이메일,채널,크리에이터,폼,추가 항목,파기";

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
    WebMvcProperties webMvc;

    String token;
    int campaign;
    int formId;

    @BeforeEach
    void setUp() {
        RestAssured.port = adminPort;
        DatabaseCleaner.truncateAll(jdbc);
        operators.save(new Operator("owner@example.com", passwordEncoder.encode("correct-horse-battery"), Instant.now()));
        token = AdminLogin.accessToken("owner@example.com", "correct-horse-battery");
        campaign = AdminFixtures.createCampaign(token, "봄 캠페인");
        formId = AdminFixtures.createPublishedFormIn(token, campaign, "form-a", HTML);
    }

    RequestSpecification authed() {
        return RestAssured.given().auth().oauth2(token);
    }

    long submit(Map<String, Object> fields, String lm) {
        Map<String, Object> body = new HashMap<>(Map.of("fields", fields, "consent", true,
            "ft", FormTokens.mature(publicPort, clock, "form-a")));
        if (lm != null) {
            body.put("lm", lm);
        }
        PublicOrigin.given(publicPort).contentType(ContentType.JSON).body(body)
            .post("/api/public/forms/form-a/submissions").then().statusCode(201);
        return jdbc.queryForObject("SELECT max(id) FROM leads", Long.class);
    }

    static String text(Response response) {
        return new String(response.asByteArray(), StandardCharsets.UTF_8);
    }

    static List<String> lines(String csv) {
        return Arrays.asList(csv.substring(BOM.length()).split("\r\n"));
    }

    Map<String, Object> lastLog() {
        return jdbc.queryForMap("SELECT action, target_type, target_id, campaign_id, detail::text AS detail "
            + "FROM audit_logs WHERE action LIKE 'lead.export%' ORDER BY id DESC LIMIT 1");
    }

    @Test
    void 기본은_BOM과_헤더_뒤에_가린_값을_신청_순으로_내려주고_lead_export를_남긴다() {
        submit(Map.of("name", "홍길동", "phone", "010-1234-5678", "email", "hong@example.com", "memo", "오전, 가능"), null);
        submit(Map.of("name", "김철수", "phone", "010-2222-3333"), null);

        Response response = authed().get("/api/admin/campaigns/{id}/leads.csv", campaign);

        response.then().statusCode(200).contentType(containsString("text/csv"))
            .header("Cache-Control", containsString("no-store"))
            .header("Content-Disposition", containsString("attachment"))
            .header("Content-Disposition", containsString("leads-campaign-" + campaign + "-"))
            .header("Content-Disposition", containsString(".csv"));
        String csv = text(response);
        assertThat(csv).startsWith(BOM);
        List<String> lines = lines(csv);
        assertThat(lines).hasSize(3);
        assertThat(lines.get(0)).isEqualTo(HEADER);
        assertThat(lines.get(1)).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2},.*")
            .contains(",홍*동,010-****-5678,h***@example.com,direct,,봄 특강 신청,")
            .endsWith(",\"{\"\"memo\"\":\"\"오전, 가능\"\"}\",");
        assertThat(lines.get(2)).contains(",김*수,010-****-3333,,direct,,봄 특강 신청,");
        assertThat(csv).doesNotContain("홍길동", "01012345678", "hong@example.com");

        Map<String, Object> log = lastLog();
        assertThat(log.get("action")).isEqualTo("lead.export");
        assertThat(log.get("target_type")).isEqualTo("campaign");
        assertThat(log.get("target_id")).isEqualTo((long) campaign);
        assertThat(log.get("campaign_id")).isEqualTo((long) campaign);
        assertThat((String) log.get("detail")).contains("\"count\": 2", "\"reveal\": false");
    }

    @Test
    void reveal이면_원문을_내려주고_lead_export_reveal을_남긴다() {
        submit(Map.of("name", "홍길동", "phone", "010-1234-5678", "email", "Hong@Example.com"), null);

        String csv = text(authed().get("/api/admin/campaigns/{id}/leads.csv?reveal=true", campaign).then()
            .statusCode(200).header("Cache-Control", containsString("no-store")).extract().response());

        assertThat(lines(csv).get(1)).contains(",홍길동,010-1234-5678,hong@example.com,direct,");
        Map<String, Object> log = lastLog();
        assertThat(log.get("action")).isEqualTo("lead.export.reveal");
        assertThat((String) log.get("detail")).contains("\"count\": 1", "\"reveal\": true")
            .doesNotContain("홍길동", "01012345678", "hong@example.com");
    }

    @Test
    void 수식으로_시작하는_값에는_작은따옴표를_붙인다() {
        submit(Map.of("name", "=HYPERLINK(\"http://evil\")", "phone", "010-1234-5678", "memo", "@SUM(A1)"), null);
        submit(Map.of("name", "+cmd", "phone", "010-1111-2222"), null);
        submit(Map.of("name", "-1", "phone", "010-3333-4444"), null);

        List<String> lines = lines(text(authed().get("/api/admin/campaigns/{id}/leads.csv?reveal=true", campaign)
            .then().statusCode(200).extract().response()));

        assertThat(lines.get(1)).contains(",\"'=HYPERLINK(\"\"http://evil\"\")\",");
        assertThat(lines.get(2)).contains(",'+cmd,");
        assertThat(lines.get(3)).contains(",'-1,");
        // payload JSON 칸은 {로 시작해 수식이 아니다
        assertThat(lines.get(1)).endsWith(",\"{\"\"memo\"\":\"\"@SUM(A1)\"\"}\",");
    }

    @Test
    void 폼과_채널로_거르고_크리에이터_이름을_적는다() {
        int creator = authed().contentType(ContentType.JSON).body(Map.of("name", "하늘"))
            .post("/api/admin/creators").then().statusCode(201).extract().path("id");
        String code = authed().contentType(ContentType.JSON).body(Map.of("channel", "instagram", "creatorId", creator))
            .post("/api/admin/forms/{id}/links", formId).then().statusCode(201).extract().path("code");
        submit(Map.of("phone", "010-1234-5678"), code);
        submit(Map.of("phone", "010-2222-3333"), null);

        List<String> lines = lines(text(authed().get("/api/admin/campaigns/{id}/leads.csv?channel=instagram&formId={f}",
            campaign, formId).then().statusCode(200).extract().response()));

        assertThat(lines).hasSize(2);
        assertThat(lines.get(1)).contains(",010-****-5678,,instagram,하늘,봄 특강 신청,");
        assertThat((String) lastLog().get("detail")).contains("\"count\": 1", "\"channel\": \"instagram\"",
            "\"formId\": " + formId);
    }

    @Test
    void 신청이_없으면_헤더만_내려준다() {
        String csv = text(authed().get("/api/admin/campaigns/{id}/leads.csv", campaign).then().statusCode(200)
            .extract().response());

        assertThat(csv).isEqualTo(BOM + HEADER + "\r\n");
        assertThat((String) lastLog().get("detail")).contains("\"count\": 0");
    }

    @Test
    void 복호화하지_못한_행은_복호화_불가로_적고_로그에는_id만_남긴다(CapturedOutput output) {
        long id = submit(Map.of("name", "홍길동", "phone", "010-1234-5678"), null);
        jdbc.update("UPDATE leads SET phone_enc = set_byte(phone_enc, length(phone_enc) - 1, "
            + "get_byte(phone_enc, length(phone_enc) - 1) # 1) WHERE id = ?", id);

        List<String> lines = lines(text(authed().get("/api/admin/campaigns/{id}/leads.csv?reveal=true", campaign)
            .then().statusCode(200).extract().response()));

        assertThat(lines.get(1)).contains(",복호화 불가,복호화 불가,복호화 불가,direct,");
        assertThat(output.getAll()).contains("leadId=" + id).doesNotContain("홍길동").doesNotContain("01012345678");
    }

    @Test
    void 잘못된_요청은_감사_로그_없이_404_422다() {
        authed().get("/api/admin/campaigns/{id}/leads.csv", 99999).then().statusCode(404)
            .contentType(containsString("application/problem+json")).body("code", equalTo("not_found"));
        authed().get("/api/admin/campaigns/{id}/leads.csv?channel=tiktok", campaign).then().statusCode(422)
            .body("errors.field", hasItem("channel"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action LIKE 'lead.export%'", Long.class))
            .isZero();
    }

    @Test
    void 스트리밍_응답의_비동기_제한_시간은_10분이다() {
        assertThat(webMvc.getAsync().getRequestTimeout()).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void 인증_없이는_401이고_public_포트에서는_404다() {
        submit(Map.of("phone", "010-1234-5678"), null);

        RestAssured.get("/api/admin/campaigns/{id}/leads.csv?reveal=true", campaign).then().statusCode(401)
            .body(not(containsString("01012345678")));
        PublicOrigin.given(publicPort).auth().oauth2(token)
            .get("/api/admin/campaigns/{id}/leads.csv", campaign).then().statusCode(404);
    }

    @Test
    void 다운로드는_로그에_원문을_남기지_않는다(CapturedOutput output) {
        submit(Map.of("name", "홍길동", "phone", "010-1234-5678", "email", "secret.person@example.com"), null);

        authed().get("/api/admin/campaigns/{id}/leads.csv?reveal=true", campaign).then().statusCode(200);
        authed().get("/api/admin/campaigns/{id}/leads.csv", campaign).then().statusCode(200);

        assertThat(output.getAll()).doesNotContain("홍길동").doesNotContain("01012345678")
            .doesNotContain("010-1234-5678").doesNotContain("secret.person@example.com");
    }
}
