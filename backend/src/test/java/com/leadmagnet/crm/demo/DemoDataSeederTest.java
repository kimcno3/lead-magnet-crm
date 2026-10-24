package com.leadmagnet.crm.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;

import com.leadmagnet.crm.auth.domain.Operator;
import com.leadmagnet.crm.auth.domain.OperatorRepository;
import com.leadmagnet.crm.demo.DemoDataSeeder.Outcome;
import com.leadmagnet.crm.demo.DemoDataSeeder.SeedResult;
import com.leadmagnet.crm.lead.application.ContactProtector;
import com.leadmagnet.crm.lead.application.LeadContact;
import com.leadmagnet.crm.lead.domain.Lead;
import com.leadmagnet.crm.lead.domain.LeadRepository;
import com.leadmagnet.crm.support.AdminLogin;
import com.leadmagnet.crm.support.DatabaseCleaner;
import com.leadmagnet.crm.support.IntegrationTest;
import com.leadmagnet.crm.support.MutableClock;
import io.restassured.RestAssured;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

/** 예시 데이터를 켠 설정(app.demo.enabled=true)으로 빈 DB에 시드한다 (ADR 0023). 기동 때와 같은 진입점(seedIfEnabled)을 부른다. */
@IntegrationTest
@TestPropertySource(properties = "app.demo.enabled=true")
@ExtendWith(OutputCaptureExtension.class)
class DemoDataSeederTest {

    static final String EMAIL = "owner@example.com";
    static final String PASSWORD = "correct-horse-battery";

    @LocalServerPort
    int adminPort;

    @Autowired
    DemoDataSeeder seeder;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    OperatorRepository operators;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    LeadRepository leads;

    @Autowired
    ContactProtector protector;

    @Autowired
    MutableClock clock;

    @BeforeEach
    void setUp() {
        RestAssured.port = adminPort;
        clock.reset();
        DatabaseCleaner.truncateAll(jdbc);
        operators.save(new Operator(EMAIL, passwordEncoder.encode(PASSWORD), Instant.now()));
    }

    @AfterEach
    void tearDown() {
        clock.reset();
    }

    long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    @Test
    void 빈_DB에_캠페인_폼_크리에이터_링크_방문_신청을_만든다() {
        SeedResult result = seeder.seedIfEnabled();

        assertThat(result.outcome()).isEqualTo(Outcome.SEEDED);
        assertThat(result.templates()).isEqualTo(4).isEqualTo(count("SELECT count(*) FROM html_templates"));
        assertThat(result.campaigns()).isEqualTo(4).isEqualTo(count("SELECT count(*) FROM campaigns"));
        assertThat(result.forms()).isEqualTo(5).isEqualTo(count("SELECT count(*) FROM forms"));
        assertThat(result.creators()).isEqualTo(5).isEqualTo(count("SELECT count(*) FROM creators"));
        assertThat(result.links()).isEqualTo(26).isEqualTo(count("SELECT count(*) FROM distribution_links"));
        assertThat(result.visits()).isEqualTo(count("SELECT count(*) FROM visits"));
        assertThat(result.botVisits()).isPositive().isEqualTo(count("SELECT count(*) FROM visits WHERE is_bot"));
        assertThat(result.leads()).isEqualTo(95).isEqualTo(count("SELECT count(*) FROM leads"));
        assertThat(count("SELECT count(*) FROM creators WHERE status = 'archived'")).isEqualTo(1);
        // 보존 기간은 기본 365일, 요고 캠페인 하나만 180일
        assertThat(jdbc.queryForList("SELECT retention_days FROM campaigns ORDER BY retention_days", Integer.class))
            .containsExactly(180, 365, 365, 365);
    }

    @Test
    void 폼은_설계한_상태이고_닫힌_폼과_초안도_있다() {
        seeder.seedIfEnabled();

        Map<String, String> states = Map.of("academy-first-1000", "published", "mcn-partner", "published",
            "yogo-brand", "published", "yogo-season-proposal", "draft", "seongsu-tasting", "closed");
        states.forEach((slug, status) -> assertThat(
            jdbc.queryForObject("SELECT status FROM forms WHERE slug = ?", String.class, slug)).as(slug).isEqualTo(status));
        // 게시한 적 있는 폼은 스냅샷이 있고 초안은 링크·신청이 없다
        assertThat(count("SELECT count(*) FROM forms WHERE status <> 'draft' AND published_html IS NULL")).isZero();
        assertThat(count("SELECT count(*) FROM distribution_links l JOIN forms f ON f.id = l.form_id "
            + "WHERE f.status = 'draft'")).isZero();
        // 게시·마감했던 폼은 기본 링크 4채널이 모두 있다
        assertThat(count("SELECT count(*) FROM distribution_links l JOIN forms f ON f.id = l.form_id "
            + "WHERE f.status <> 'draft' AND l.creator_id IS NULL")).isEqualTo(16);
        // 체크박스 묶음 라벨은 폼에서 고친 값이다
        assertThat(jdbc.queryForObject("SELECT f->>'label' FROM forms, jsonb_array_elements(field_config) f "
            + "WHERE slug = 'academy-first-1000' AND f->>'name' = 'interests'", String.class)).isEqualTo("가장 듣고 싶은 내용");
    }

    @Test
    void 모든_신청은_원문_API로_복호화되고_같은_방문자의_방문이_있다() {
        seeder.seedIfEnabled();
        String token = AdminLogin.accessToken(EMAIL, PASSWORD);

        List<Long> ids = jdbc.queryForList("SELECT id FROM leads ORDER BY id", Long.class);
        assertThat(ids).hasSize(95);
        for (long id : ids) {
            RestAssured.given().auth().oauth2(token).get("/api/admin/leads/{id}/contact", id)
                .then().statusCode(200)
                .body("name", notNullValue())
                .body("phone", matchesPattern("^0105555\\d{4}$"));
        }
        RestAssured.given().auth().oauth2(token).get("/api/admin/leads/{id}/contact", ids.get(0))
            .then().body("email", endsWith("@example.com"));
        assertThat(count("""
            SELECT count(*) FROM leads l
             WHERE l.visitor_id IS NULL
                OR NOT EXISTS (SELECT 1 FROM visits v WHERE v.form_id = l.form_id AND v.visitor_id = l.visitor_id
                                                       AND v.occurred_at <= l.created_at AND NOT v.is_bot)
            """)).isZero();
        // 동의 증빙은 실제 제출처럼 폼 문구와 sha256이다
        assertThat(count("""
            SELECT count(*) FROM leads l JOIN forms f ON f.id = l.form_id
             WHERE l.consent_text IS DISTINCT FROM f.consent_text
                OR l.consent_sha256 IS DISTINCT FROM encode(sha256(convert_to(f.consent_text, 'UTF8')), 'hex')
            """)).isZero();
    }

    @Test
    void 캠페인_지표는_0이_아니고_봇_방문은_지표에서_빠진다() {
        seeder.seedIfEnabled();
        String token = AdminLogin.accessToken(EMAIL, PASSWORD);

        for (long campaignId : jdbc.queryForList("SELECT id FROM campaigns", Long.class)) {
            long humanVisits = count("SELECT count(*) FROM visits v JOIN forms f ON f.id = v.form_id "
                + "WHERE NOT v.is_bot AND f.campaign_id = " + campaignId);
            long allVisits = count("SELECT count(*) FROM visits v JOIN forms f ON f.id = v.form_id "
                + "WHERE f.campaign_id = " + campaignId);
            assertThat(allVisits).as("봇 방문 포함").isGreaterThan(humanVisits);
            Map<String, Object> stats = RestAssured.given().auth().oauth2(token)
                .get("/api/admin/campaigns/{id}/stats", campaignId).then().statusCode(200).extract().jsonPath().getMap("");
            assertThat(((Number) stats.get("visits")).longValue()).isEqualTo(humanVisits);
            assertThat(((Number) stats.get("leads")).longValue()).isPositive();
            assertThat(((Number) stats.get("convertedVisitors")).longValue()).isPositive();
        }
    }

    @Test
    void 캠페인_목록은_아카데미_MCN_요고_FnB_순이고_FnB만_보관이다() {
        seeder.seedIfEnabled();
        String token = AdminLogin.accessToken(EMAIL, PASSWORD);

        List<Map<String, Object>> list = RestAssured.given().auth().oauth2(token).get("/api/admin/campaigns")
            .then().statusCode(200).extract().jsonPath().getList("");

        assertThat(list).extracting(c -> (String) c.get("name")).containsExactly(
            "아카데미 · 유튜브 첫 1,000명 로드맵", "MCN · 크리에이터 파트너 모집", "요고 · 브랜드 입점·공동구매",
            "F&B · 성수 매장 오픈 시식회");
        assertThat(list).extracting(c -> (String) c.get("status"))
            .containsExactly("active", "active", "active", "archived");
    }

    @Test
    void 방문과_신청은_최근_21일에_흩어지고_보관된_크리에이터_링크는_지난_방문만_있다() {
        clock.freezeAt(Instant.parse("2026-10-24T05:00:00Z"));
        seeder.seedIfEnabled();
        Instant now = clock.instant();

        assertThat(jdbc.queryForObject("SELECT min(occurred_at) FROM visits", Instant.class))
            .isAfter(now.minus(Duration.ofDays(23)));
        assertThat(jdbc.queryForObject("SELECT max(occurred_at) FROM visits", Instant.class)).isBefore(now);
        assertThat(jdbc.queryForObject("SELECT max(created_at) FROM leads", Instant.class)).isBefore(now);
        assertThat(count("SELECT count(DISTINCT (created_at AT TIME ZONE 'Asia/Seoul')::date) FROM leads"))
            .isGreaterThanOrEqualTo(15);
        // 게시는 첫 방문보다 앞이다
        assertThat(count("SELECT count(*) FROM visits v JOIN forms f ON f.id = v.form_id "
            + "WHERE v.occurred_at < f.published_at")).isZero();
        Instant archivedLast = jdbc.queryForObject("""
            SELECT max(v.occurred_at) FROM visits v JOIN distribution_links l ON l.id = v.link_id
              JOIN creators c ON c.id = l.creator_id WHERE c.status = 'archived'
            """, Instant.class);
        assertThat(archivedLast).isNotNull().isBefore(now.minus(Duration.ofDays(10)));
    }

    @Test
    void 두_번째_실행은_아무것도_넣지_않는다() {
        seeder.seedIfEnabled();
        long visits = count("SELECT count(*) FROM visits");
        long audits = count("SELECT count(*) FROM audit_logs");

        SeedResult second = seeder.seedIfEnabled();

        assertThat(second.outcome()).isEqualTo(Outcome.NOT_EMPTY);
        assertThat(count("SELECT count(*) FROM campaigns")).isEqualTo(4);
        assertThat(count("SELECT count(*) FROM html_templates")).isEqualTo(4);
        assertThat(count("SELECT count(*) FROM visits")).isEqualTo(visits);
        assertThat(count("SELECT count(*) FROM leads")).isEqualTo(95);
        assertThat(count("SELECT count(*) FROM audit_logs")).isEqualTo(audits);
    }

    @Test
    void 캠페인이_이미_있으면_아무것도_넣지_않는다() {
        jdbc.update("INSERT INTO campaigns (name, created_by) SELECT '기존 캠페인', id FROM operators");

        assertThat(seeder.seedIfEnabled().outcome()).isEqualTo(Outcome.NOT_EMPTY);

        assertThat(count("SELECT count(*) FROM campaigns")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM html_templates")).isZero();
        assertThat(count("SELECT count(*) FROM creators")).isZero();
    }

    @Test
    void 운영자가_없으면_아무것도_넣지_않는다() {
        DatabaseCleaner.truncateAll(jdbc);

        assertThat(seeder.seedIfEnabled().outcome()).isEqualTo(Outcome.NO_OPERATOR);

        assertThat(count("SELECT count(*) FROM html_templates")).isZero();
        assertThat(count("SELECT count(*) FROM campaigns")).isZero();
    }

    @Test
    void 감사_기록은_시드_주체의_게시_마감_보관뿐이고_운영자를_사칭하지_않는다() {
        seeder.seedIfEnabled();

        assertThat(jdbc.queryForList("SELECT action FROM audit_logs ORDER BY action", String.class))
            .containsExactlyInAnyOrder("form.publish", "form.publish", "form.publish", "form.publish", "form.close",
                "creator.archive");
        assertThat(count("SELECT count(*) FROM audit_logs WHERE operator_id IS NOT NULL OR ip IS NOT NULL")).isZero();
        assertThat(jdbc.queryForList("SELECT DISTINCT user_agent FROM audit_logs", String.class))
            .containsExactly(DemoDataSeeder.ACTOR.userAgent());
    }

    @Test
    void 로그에는_건수만_남고_신청자_원문_연락처는_없다(CapturedOutput output) {
        seeder.seedIfEnabled();

        assertThat(output).contains("예시 데이터 생성").contains("신청 95");
        assertThat(output.getAll()).doesNotContainPattern("010-?5555-?\\d{4}");
        for (Lead lead : leads.findAll()) {
            LeadContact contact = protector.reveal(lead);
            assertThat(output.getAll()).doesNotContain(contact.phone());
            if (contact.email() != null) {
                assertThat(output.getAll()).doesNotContain(contact.email());
            }
            assertThat(output.getAll()).doesNotContain(contact.name());
        }
    }
}
