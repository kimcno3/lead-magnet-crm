package com.leadmagnet.crm.lead.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

import com.leadmagnet.crm.audit.domain.AuditActor;
import com.leadmagnet.crm.auth.domain.Operator;
import com.leadmagnet.crm.auth.domain.OperatorRepository;
import com.leadmagnet.crm.common.web.PublicPort;
import com.leadmagnet.crm.lead.config.RetentionScheduler;
import com.leadmagnet.crm.lead.domain.LeadRepository;
import com.leadmagnet.crm.support.AdminFixtures;
import com.leadmagnet.crm.support.AdminLogin;
import com.leadmagnet.crm.support.DatabaseCleaner;
import com.leadmagnet.crm.support.FormTokens;
import com.leadmagnet.crm.support.IntegrationTest;
import com.leadmagnet.crm.support.MutableClock;
import com.leadmagnet.crm.support.PublicOrigin;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
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
 * 파기 실행 중간에 배치가 실패해도 이미 커밋한 익명화에 대한 감사 기록(부분 건수, completed=false)이 남는다 (ADR 0020).
 * 배치는 2건(@IntegrationTest)이고, 두 번째 배치의 대상 조회를 실패시킨다.
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class LeadRetentionFailureTest {

    static final String HTML = """
        <form><input name="name"><input name="phone" type="tel"></form>
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
    LeadRetentionService retention;

    @MockitoSpyBean
    LeadRepository leads;

    String token;

    @BeforeEach
    void setUp() {
        RestAssured.port = adminPort;
        DatabaseCleaner.truncateAll(jdbc);
        operators.save(new Operator("owner@example.com", passwordEncoder.encode("correct-horse-battery"), Instant.now()));
        token = AdminLogin.accessToken("owner@example.com", "correct-horse-battery");
        int campaign = AdminFixtures.createCampaign(token, "봄 캠페인");
        AdminFixtures.createPublishedFormIn(token, campaign, "form-a", HTML);
        for (int i = 0; i < 3; i++) {
            PublicOrigin.given(publicPort).contentType(ContentType.JSON)
                .body(Map.of("fields", Map.of("name", "홍길동", "phone", "010-4444-000" + i), "consent", true,
                    "ft", FormTokens.mature(publicPort, clock, "form-a")))
                .post("/api/public/forms/form-a/submissions").then().statusCode(201);
        }
        jdbc.update("UPDATE leads SET created_at = ?", Timestamp.from(Instant.now().minus(Duration.ofDays(400))));
        // 첫 배치는 가장 작은 id 2건(배치 크기 2라 다음 배치로 넘어간다), 두 번째 배치의 대상 조회에서 DB 오류.
        // JPA 리포지토리 프록시는 callRealMethod를 쓸 수 없어 첫 응답을 직접 만든다.
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            if (calls.getAndIncrement() == 0) {
                return jdbc.queryForList("SELECT id FROM leads ORDER BY id LIMIT 2", Long.class);
            }
            throw new DataAccessResourceFailureException("db down");
        }).when(leads).findRetentionDueIds(any(), anyLong(), anyInt());
    }

    List<Map<String, Object>> auditRows() {
        return jdbc.queryForList("SELECT detail->>'trigger' AS trigger, (detail->>'count')::int AS count, "
            + "(detail->>'completed')::boolean AS completed, detail->>'error' AS error "
            + "FROM audit_logs WHERE action = 'retention.anonymize'");
    }

    long anonymizedCount() {
        return jdbc.queryForObject("SELECT count(*) FROM leads WHERE anonymized_at IS NOT NULL", Long.class);
    }

    @Test
    void 두_번째_배치가_실패하면_첫_배치는_파기된_채_부분_건수와_completed_false를_한_줄_기록하고_예외를_올린다() {
        assertThatThrownBy(() -> retention.run(LeadRetentionService.TRIGGER_MANUAL, new AuditActor(1L, null, null)))
            .isInstanceOf(DataAccessResourceFailureException.class);

        assertThat(anonymizedCount()).isEqualTo(2);
        List<Map<String, Object>> rows = auditRows();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("trigger", "manual").containsEntry("count", 2)
            .containsEntry("completed", false)
            .containsEntry("error", DataAccessResourceFailureException.class.getName());
    }

    @Test
    void 수동_실행_API는_500이고_감사_기록은_남는다() {
        RestAssured.given().auth().oauth2(token).post("/api/admin/retention/run").then().statusCode(500)
            .body("code", equalTo("internal_error"));

        assertThat(anonymizedCount()).isEqualTo(2);
        assertThat(auditRows()).singleElement().satisfies(r -> {
            assertThat(r).containsEntry("count", 2).containsEntry("completed", false);
        });
    }

    @Test
    void 스케줄러는_예외를_삼키고_ERROR_한_줄에_예외_종류만_남기며_감사_기록은_남는다(CapturedOutput output) {
        new RetentionScheduler(retention).runDaily();

        assertThat(anonymizedCount()).isEqualTo(2);
        assertThat(auditRows()).singleElement().satisfies(r -> {
            assertThat(r).containsEntry("trigger", "scheduled").containsEntry("count", 2)
                .containsEntry("completed", false);
        });
        assertThat(output.getAll()).contains("보존 기간 파기 실패: 원인=" + DataAccessResourceFailureException.class.getName())
            .doesNotContain("db down").doesNotContain("01044440000");
    }
}
