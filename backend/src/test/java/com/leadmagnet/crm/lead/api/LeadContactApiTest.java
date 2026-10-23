package com.leadmagnet.crm.lead.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
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
import io.restassured.specification.RequestSpecification;
import java.time.Instant;
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

/** 신청자 원문 연락처 조회 (ADR 0015). 명단은 가린 값만 주고 원문은 이 엔드포인트로 한 건씩만 본다. */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class LeadContactApiTest {

    static final String HTML = """
        <form><input name="name"><input name="phone" type="tel"><input name="email" type="email">
        <input name="alt_phone" type="tel"><input name="backup_email" type="email"><input name="memo"></form>
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

    String token;
    int campaign;

    @BeforeEach
    void setUp() {
        RestAssured.port = adminPort;
        DatabaseCleaner.truncateAll(jdbc);
        operators.save(new Operator("owner@example.com", passwordEncoder.encode("correct-horse-battery"), Instant.now()));
        token = AdminLogin.accessToken("owner@example.com", "correct-horse-battery");
        campaign = AdminFixtures.createCampaign(token, "봄 캠페인");
        AdminFixtures.createPublishedFormIn(token, campaign, "form-a", HTML);
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

    @Test
    void 원문_이름_전화번호_이메일을_복호화해_돌려주고_캐시하지_않게_한다() {
        long id = submit(Map.of("name", "홍길동", "phone", "010-1234-5678", "email", "Hong@Example.com"));

        authed().get("/api/admin/leads/{id}/contact", id).then().statusCode(200)
            .header("Cache-Control", containsString("no-store"))
            .body("name", equalTo("홍길동")).body("phone", equalTo("01012345678"))
            .body("email", equalTo("hong@example.com")).body("extraContacts", equalTo(Map.of()))
            .body("size()", equalTo(4));
    }

    @Test
    void 비어_있는_값은_null이다() {
        long id = submit(Map.of("phone", "010-1234-5678"));

        authed().get("/api/admin/leads/{id}/contact", id).then().statusCode(200)
            .body("name", equalTo(null)).body("email", equalTo(null)).body("phone", equalTo("01012345678"));
    }

    @Test
    void payload의_연락처_모양_항목은_extraContacts에_원문으로_주고_감사_로그는_그대로다() {
        long id = submit(Map.of("phone", "010-1234-5678", "alt_phone", "010-9999-8888",
            "backup_email", "Secret@Example.com", "memo", "오전"));

        authed().get("/api/admin/leads/{id}/contact", id).then().statusCode(200)
            .body("extraContacts.alt_phone", equalTo("01099998888"))
            .body("extraContacts.backup_email", equalTo("secret@example.com"))
            .body("extraContacts.size()", equalTo(2));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'lead.contact.view'",
            Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT detail::text FROM audit_logs WHERE action = 'lead.contact.view'",
            String.class)).doesNotContain("01099998888", "secret@example.com");
    }

    @Test
    void 없는_신청은_404다() {
        authed().get("/api/admin/leads/{id}/contact", 99999).then().statusCode(404).body("code", equalTo("not_found"));
    }

    @Test
    void 토큰이_없거나_틀리면_401이다() {
        long id = submit(Map.of("phone", "010-1234-5678"));

        RestAssured.get("/api/admin/leads/{id}/contact", id).then().statusCode(401)
            .contentType(containsString("application/problem+json")).body(not(containsString("01012345678")));
        RestAssured.given().auth().oauth2("not-a-jwt").get("/api/admin/leads/{id}/contact", id).then().statusCode(401);
    }

    @Test
    void public_포트에서는_열리지_않는다() {
        long id = submit(Map.of("phone", "010-1234-5678"));

        PublicOrigin.given(publicPort).auth().oauth2(token).get("/api/admin/leads/{id}/contact", id).then()
            .statusCode(404);
    }

    @Test
    void 변조된_암호문은_500이고_응답과_로그에_원문을_남기지_않는다(CapturedOutput output) {
        long id = submit(Map.of("name", "홍길동", "phone", "010-1234-5678"));
        jdbc.update("UPDATE leads SET phone_enc = set_byte(phone_enc, length(phone_enc) - 1, "
            + "get_byte(phone_enc, length(phone_enc) - 1) # 1) WHERE id = ?", id);

        authed().get("/api/admin/leads/{id}/contact", id).then().statusCode(500)
            .body("code", equalTo("internal_error")).body(not(containsString("01012345678")));
        assertThat(output.getAll()).doesNotContain("01012345678").doesNotContain("홍길동");
    }

    @Test
    void 신청_명단_원문_조회_모두_로그에_원문을_남기지_않는다(CapturedOutput output) {
        long id = submit(Map.of("name", "홍길동", "phone", "010-1234-5678", "email", "secret.person@example.com"));

        authed().get("/api/admin/campaigns/{id}/leads", campaign).then().statusCode(200);
        authed().get("/api/admin/leads/{id}/contact", id).then().statusCode(200);

        assertThat(output.getAll()).doesNotContain("홍길동").doesNotContain("01012345678")
            .doesNotContain("010-1234-5678").doesNotContain("secret.person@example.com");
    }
}
