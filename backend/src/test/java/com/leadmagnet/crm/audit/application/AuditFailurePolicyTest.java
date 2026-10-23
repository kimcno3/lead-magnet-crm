package com.leadmagnet.crm.audit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.leadmagnet.crm.audit.domain.AuditLogRepository;
import com.leadmagnet.crm.auth.domain.Operator;
import com.leadmagnet.crm.auth.domain.OperatorRepository;
import com.leadmagnet.crm.common.web.PublicPort;
import com.leadmagnet.crm.support.AdminFixtures;
import com.leadmagnet.crm.support.AdminLogin;
import com.leadmagnet.crm.support.DatabaseCleaner;
import com.leadmagnet.crm.support.IntegrationTest;
import com.leadmagnet.crm.support.PublicOrigin;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
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

/**
 * 감사 로그 기록이 실패할 때 (ADR 0016): 원문 열람·다운로드는 요청도 실패하고(같은 트랜잭션),
 * 명단 조회와 로그인은 응답하되 실패를 로그에 남긴다.
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class AuditFailurePolicyTest {

    static final String HTML = """
        <form><input name="name"><input name="phone" type="tel"></form>
        """;

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

    @MockitoSpyBean
    AuditLogRepository auditLogs;

    String token;
    int campaign;
    long lead;

    @BeforeEach
    void setUp() {
        RestAssured.port = adminPort;
        DatabaseCleaner.truncateAll(jdbc);
        operators.save(new Operator("owner@example.com", passwordEncoder.encode("correct-horse-battery"), Instant.now()));
        token = AdminLogin.accessToken("owner@example.com", "correct-horse-battery");
        campaign = AdminFixtures.createCampaign(token, "봄 캠페인");
        AdminFixtures.createPublishedFormIn(token, campaign, "form-a", HTML);
        PublicOrigin.given(publicPort).contentType(ContentType.JSON)
            .body(Map.of("fields", Map.of("name", "홍길동", "phone", "010-1234-5678"), "consent", true))
            .post("/api/public/forms/form-a/submissions").then().statusCode(201);
        lead = jdbc.queryForObject("SELECT max(id) FROM leads", Long.class);
        doThrow(new DataAccessResourceFailureException("audit store down")).when(auditLogs).saveAndFlush(any());
    }

    long auditCount() {
        return jdbc.queryForObject("SELECT count(*) FROM audit_logs", Long.class);
    }

    @Test
    void 원문_열람은_기록에_실패하면_500이고_원문을_주지_않는다() {
        long before = auditCount();

        RestAssured.given().auth().oauth2(token).get("/api/admin/leads/{id}/contact", lead).then().statusCode(500)
            .body("code", equalTo("internal_error"));

        assertThat(auditCount()).isEqualTo(before);
    }

    @Test
    void 원문_다운로드도_기록에_실패하면_500이고_내용을_주지_않는다() {
        String body = RestAssured.given().auth().oauth2(token)
            .get("/api/admin/campaigns/{id}/leads.csv?reveal=true", campaign).then().statusCode(500)
            .body("code", equalTo("internal_error")).extract().asString();

        assertThat(body).doesNotContain("홍길동").doesNotContain("01012345678");
    }

    @Test
    void 가린_값_다운로드도_기록에_실패하면_500이다() {
        RestAssured.given().auth().oauth2(token).get("/api/admin/campaigns/{id}/leads.csv", campaign).then()
            .statusCode(500);
    }

    @Test
    void 명단_조회는_기록에_실패해도_응답하고_실패를_로그에_남긴다(CapturedOutput output) {
        RestAssured.given().auth().oauth2(token).get("/api/admin/campaigns/{id}/leads", campaign).then()
            .statusCode(200).body("totalElements", equalTo(1));

        assertThat(output.getAll()).contains("감사 로그 기록 실패").contains("lead.list")
            .doesNotContain("홍길동").doesNotContain("01012345678");
    }

    @Test
    void 로그인은_기록에_실패해도_성공한다(CapturedOutput output) {
        RestAssured.given().contentType(ContentType.JSON)
            .body(Map.of("email", "owner@example.com", "password", "correct-horse-battery"))
            .post("/api/admin/auth/login").then().statusCode(200);

        assertThat(output.getAll()).contains("감사 로그 기록 실패").contains("auth.login.success");
    }
}
