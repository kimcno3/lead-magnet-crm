package com.leadmagnet.crm.lead.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import com.leadmagnet.crm.auth.domain.Operator;
import com.leadmagnet.crm.auth.domain.OperatorRepository;
import com.leadmagnet.crm.common.web.AuditActor;
import com.leadmagnet.crm.common.web.PublicPort;
import com.leadmagnet.crm.lead.application.LeadRetentionService;
import com.leadmagnet.crm.support.AdminFixtures;
import com.leadmagnet.crm.support.AdminLogin;
import com.leadmagnet.crm.support.DatabaseCleaner;
import com.leadmagnet.crm.support.FormTokens;
import com.leadmagnet.crm.support.IntegrationTest;
import com.leadmagnet.crm.support.MutableClock;
import com.leadmagnet.crm.support.PublicOrigin;
import com.leadmagnet.crm.support.PublicVisitor;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 보존 기간 파기와 동의 문구 증빙 (ADR 0020). 신청은 실제 공개 API로 넣고 created_at만 SQL로 과거로 옮긴 뒤,
 * 시계를 고정해 경계를 정확히 맞춘다. 배치 크기는 테스트에서 2건이다(@IntegrationTest).
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class LeadRetentionApiTest {

    static final String HTML = """
        <form><input name="name"><input name="phone" type="tel"><input name="email" type="email">
        <input name="company"><input type="checkbox" name="topics" value="a"><input type="checkbox" name="topics" value="b">
        </form>
        """;
    static final String CONSENT = "개인정보 수집에 동의합니다";

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

    @Autowired
    PlatformTransactionManager transactionManager;

    String token;
    int campaign;
    int otherCampaign;
    long operatorId;

    @BeforeEach
    void setUp() {
        RestAssured.port = adminPort;
        DatabaseCleaner.truncateAll(jdbc);
        operatorId = operators.save(new Operator("owner@example.com", passwordEncoder.encode("correct-horse-battery"),
            Instant.now())).getId();
        token = AdminLogin.accessToken("owner@example.com", "correct-horse-battery");
        campaign = createCampaign("봄 캠페인", 30);
        otherCampaign = createCampaign("가을 캠페인", 3650);
        AdminFixtures.createPublishedFormIn(token, campaign, "form-a", HTML);
        AdminFixtures.createPublishedFormIn(token, otherCampaign, "form-b", HTML);
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    RequestSpecification authed() {
        return RestAssured.given().auth().oauth2(token);
    }

    int createCampaign(String name, int retentionDays) {
        return authed().contentType(ContentType.JSON).body(Map.of("name", name, "retentionDays", retentionDays))
            .post("/api/admin/campaigns").then().statusCode(201).body("retentionDays", equalTo(retentionDays))
            .extract().path("id");
    }

    long submit(String slug, String phone) {
        // 이메일도 전화번호마다 달라야 중복(409)이 아니다.
        return submit(slug, Map.of("name", "홍길동", "phone", phone, "email", "secret.person" + phone.replace("-", "") + "@example.com",
            "company", "하늘상사", "topics", List.of("a", "b")));
    }

    long submit(String slug, Map<String, Object> fields) {
        PublicOrigin.given(publicPort).contentType(ContentType.JSON)
            .body(Map.of("fields", fields, "consent", true, "ft", FormTokens.mature(publicPort, clock, slug)))
            .post("/api/public/forms/{slug}/submissions", slug).then().statusCode(201);
        return jdbc.queryForObject("SELECT max(id) FROM leads", Long.class);
    }

    void createdAt(long leadId, Instant at) {
        jdbc.update("UPDATE leads SET created_at = ?, consent_at = ? WHERE id = ?", Timestamp.from(at),
            Timestamp.from(at), leadId);
    }

    boolean anonymized(long leadId) {
        return jdbc.queryForObject("SELECT anonymized_at IS NOT NULL FROM leads WHERE id = ?", Boolean.class, leadId);
    }

    Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    int runManually() {
        return authed().post("/api/admin/retention/run").then().statusCode(200)
            .body("dryRun", equalTo(false)).body("interrupted", equalTo(false)).extract().path("anonymized");
    }

    @Test
    void 정확히_보존_기간이_된_신청은_남고_그보다_오래된_신청만_익명화한다() {
        Instant now = now();
        long boundary = submit("form-a", "010-1111-0001");
        long expired = submit("form-a", "010-1111-0002");
        long fresh = submit("form-a", "010-1111-0003");
        createdAt(boundary, now.minus(Duration.ofDays(30)));
        createdAt(expired, now.minus(Duration.ofDays(30)).minus(1, ChronoUnit.MICROS));
        createdAt(fresh, now.minus(Duration.ofDays(29)));
        clock.freezeAt(now);

        assertThat(runManually()).isEqualTo(1);

        assertThat(anonymized(boundary)).isFalse();
        assertThat(anonymized(expired)).isTrue();
        assertThat(anonymized(fresh)).isFalse();
        assertThat(jdbc.queryForObject("SELECT anonymized_at FROM leads WHERE id = ?", Timestamp.class, expired)
            .toInstant()).isEqualTo(now);
    }

    @Test
    void 익명화는_연락처_암호문과_해시를_지우고_payload는_키만_남기며_지표용_값과_동의_증빙은_둔다() {
        long id = submit("form-a", "010-1111-0001");
        Map<String, Object> before = jdbc.queryForMap(
            "SELECT form_id, campaign_id, link_id, channel, visitor_id, created_at, consent_at, consent_text, consent_sha256 "
                + "FROM leads WHERE id = ?", id);
        createdAt(id, now().minus(Duration.ofDays(31)));

        runManually();

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM leads WHERE id = ?", id);
        assertThat(row.get("name_enc")).isNull();
        assertThat(row.get("phone_enc")).isNull();
        assertThat(row.get("email_enc")).isNull();
        assertThat(row.get("phone_hash")).isNull();
        assertThat(row.get("email_hash")).isNull();
        assertThat(jdbc.queryForObject("SELECT payload::text FROM leads WHERE id = ?", String.class, id))
            .isEqualTo("{\"topics\": [], \"company\": null}");
        Map<String, Object> after = jdbc.queryForMap(
            "SELECT form_id, campaign_id, link_id, channel, visitor_id, consent_text, consent_sha256 FROM leads WHERE id = ?",
            id);
        assertThat(after.get("form_id")).isEqualTo(before.get("form_id"));
        assertThat(after.get("campaign_id")).isEqualTo(before.get("campaign_id"));
        assertThat(after.get("channel")).isEqualTo(before.get("channel"));
        assertThat(after.get("consent_text")).isEqualTo(CONSENT);
        assertThat(after.get("consent_sha256")).isEqualTo(before.get("consent_sha256"));
    }

    @Test
    void 다른_캠페인의_신청은_자기_보존_기간을_따른다() {
        long a = submit("form-a", "010-1111-0001");
        long b = submit("form-b", "010-1111-0001");
        createdAt(a, now().minus(Duration.ofDays(400)));
        createdAt(b, now().minus(Duration.ofDays(400)));

        assertThat(runManually()).isEqualTo(1);

        assertThat(anonymized(a)).isTrue();
        assertThat(anonymized(b)).isFalse();
        assertThat(jdbc.queryForObject("SELECT phone_enc IS NOT NULL FROM leads WHERE id = ?", Boolean.class, b)).isTrue();
    }

    @Test
    void 다시_실행해도_추가로_익명화하지_않고_처음_시각을_유지한다() {
        long id = submit("form-a", "010-1111-0001");
        createdAt(id, now().minus(Duration.ofDays(31)));
        assertThat(runManually()).isEqualTo(1);
        Timestamp first = jdbc.queryForObject("SELECT anonymized_at FROM leads WHERE id = ?", Timestamp.class, id);

        assertThat(runManually()).isZero();

        assertThat(jdbc.queryForObject("SELECT anonymized_at FROM leads WHERE id = ?", Timestamp.class, id))
            .isEqualTo(first);
    }

    @Test
    void 배치_크기보다_많은_대상도_keyset으로_모두_처리한다() {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            long id = submit("form-a", "010-1111-000" + i);
            createdAt(id, now().minus(Duration.ofDays(31)));
            ids.add(id);
        }

        assertThat(runManually()).isEqualTo(5);

        assertThat(ids).allMatch(this::anonymized);
    }

    @Test
    void 익명화해도_캠페인_채널_크리에이터_지표는_그대로다() {
        String code = AdminFixtures.createLink(token, jdbc.queryForObject("SELECT id FROM forms WHERE slug = 'form-a'",
            Integer.class), "instagram");
        PublicVisitor visitor = new PublicVisitor(publicPort, clock);
        visitor.visitViaShortLink(code);
        visitor.submit("form-a", Map.of("phone", "010-2222-0001"), true, code).then().statusCode(201);
        long old = jdbc.queryForObject("SELECT max(id) FROM leads", Long.class);
        long direct = submit("form-a", "010-2222-0002");
        createdAt(old, now().minus(Duration.ofDays(31)));
        createdAt(direct, now().minus(Duration.ofDays(31)));
        List<String> before = stats();

        assertThat(runManually()).isEqualTo(2);

        assertThat(stats()).isEqualTo(before);
        assertThat(before.get(0)).contains("\"leads\":2").contains("\"convertedVisitors\":1");
    }

    List<String> stats() {
        return List.of(
            authed().get("/api/admin/campaigns/{id}/stats", campaign).then().statusCode(200).extract().asString(),
            authed().get("/api/admin/campaigns/{id}/stats/channels", campaign).then().statusCode(200).extract().asString(),
            authed().get("/api/admin/campaigns/{id}/stats/creators", campaign).then().statusCode(200).extract().asString(),
            authed().get("/api/admin/stats/campaigns").then().statusCode(200).extract().asString());
    }

    @Test
    void 실행마다_감사_로그_한_줄에_건수를_남긴다() {
        for (int i = 0; i < 3; i++) {
            createdAt(submit("form-a", "010-1111-000" + i), now().minus(Duration.ofDays(31)));
        }

        runManually();
        runManually();

        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT operator_id, target_type, target_id, campaign_id, detail->>'trigger' AS trigger, "
                + "(detail->>'count')::int AS count FROM audit_logs WHERE action = 'retention.anonymize' ORDER BY id");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("operator_id", operatorId).containsEntry("target_type", "lead")
            .containsEntry("trigger", "manual").containsEntry("count", 3);
        assertThat(rows.get(0).get("target_id")).isNull();
        assertThat(rows.get(1)).containsEntry("count", 0);
        assertThat(jdbc.queryForList("SELECT detail::text FROM audit_logs WHERE action = 'retention.anonymize'",
            String.class)).allSatisfy(d -> assertThat(d).contains("\"completed\": true").contains("\"interrupted\": false")
                .doesNotContain("error"));
        authed().get("/api/admin/audit-logs?action=retention.anonymize").then().statusCode(200)
            .body("totalElements", equalTo(2));
    }

    @Test
    void 스케줄_실행은_운영자_없이_scheduled로_기록한다() {
        createdAt(submit("form-a", "010-1111-0001"), now().minus(Duration.ofDays(31)));

        assertThat(retention.run(LeadRetentionService.TRIGGER_SCHEDULED, new AuditActor(null, null, null)).anonymized())
            .isEqualTo(1);

        Map<String, Object> row = jdbc.queryForMap(
            "SELECT operator_id, detail->>'trigger' AS trigger FROM audit_logs WHERE action = 'retention.anonymize'");
        assertThat(row.get("operator_id")).isNull();
        assertThat(row.get("trigger")).isEqualTo("scheduled");
    }

    @Test
    void 드라이런은_대상_건수만_주고_아무것도_바꾸거나_기록하지_않는다() {
        long id = submit("form-a", "010-1111-0001");
        createdAt(id, now().minus(Duration.ofDays(31)));
        submit("form-a", "010-1111-0002");

        authed().post("/api/admin/retention/run?dryRun=true").then().statusCode(200)
            .body("anonymized", equalTo(1)).body("dryRun", equalTo(true));

        assertThat(anonymized(id)).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'retention.anonymize'",
            Integer.class)).isZero();
    }

    @Test
    void 토큰이_없으면_401이고_public_포트에서는_404다() {
        RestAssured.post("/api/admin/retention/run").then().statusCode(401).body("code", equalTo("unauthorized"));
        PublicOrigin.given(publicPort).auth().oauth2(token).post("/api/admin/retention/run").then().statusCode(404);
    }

    @Test
    void 다른_실행이_advisory_lock을_쥐고_있으면_이번_실행은_아무것도_처리하지_않는다() throws Exception {
        long id = submit("form-a", "010-1111-0001");
        createdAt(id, now().minus(Duration.ofDays(31)));
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            // 다른 인스턴스의 배치 트랜잭션처럼 같은 키의 xact lock을 쥐고 기다린다.
            Future<?> holder = pool.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(s -> {
                jdbc.queryForObject("SELECT pg_advisory_xact_lock(?)", Object.class, lockKey());
                locked.countDown();
                await(release);
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            // 이번 실행은 아무것도 처리하지 않고 멈췄다고 응답과 감사 기록에 남긴다.
            authed().post("/api/admin/retention/run").then().statusCode(200)
                .body("anonymized", equalTo(0)).body("interrupted", equalTo(true));
            assertThat(anonymized(id)).isFalse();
            assertThat(jdbc.queryForMap("SELECT (detail->>'count')::int AS count, (detail->>'interrupted')::boolean AS i, "
                + "(detail->>'completed')::boolean AS c FROM audit_logs WHERE action = 'retention.anonymize'"))
                .containsEntry("count", 0).containsEntry("i", true).containsEntry("c", true);

            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
        assertThat(runManually()).isEqualTo(1);
        assertThat(anonymized(id)).isTrue();
    }

    @Test
    void 두_실행이_동시에_돌아도_같은_신청을_두_번_처리하지_않는다() throws Exception {
        int total = 9;
        for (int i = 0; i < total; i++) {
            createdAt(submit("form-a", "010-3333-000" + i), now().minus(Duration.ofDays(31)));
        }
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Long>> runs = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                runs.add(pool.submit(() -> {
                    await(start);
                    return retention.run(LeadRetentionService.TRIGGER_SCHEDULED, new AuditActor(null, null, null))
                        .anonymized();
                }));
            }
            start.countDown();
            long sum = 0;
            for (Future<Long> run : runs) {
                sum += run.get(30, TimeUnit.SECONDS);
            }

            // 두 실행이 처리한 건수의 합이 대상 수와 같다 = 겹쳐 처리한 행이 없다.
            assertThat(sum).isEqualTo(total);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM leads WHERE anonymized_at IS NOT NULL", Integer.class))
            .isEqualTo(total);
        assertThat(jdbc.queryForObject("SELECT sum((detail->>'count')::int) FROM audit_logs "
            + "WHERE action = 'retention.anonymize'", Integer.class)).isEqualTo(total);
    }

    @Test
    void 파기된_신청은_명단에서_연락처가_비고_파기_시각이_있다() {
        long id = submit("form-a", "010-1111-0001");
        long kept = submit("form-a", "010-1111-0002");
        createdAt(id, now().minus(Duration.ofDays(31)));
        runManually();

        authed().get("/api/admin/campaigns/{id}/leads", campaign).then().statusCode(200)
            .body("items.find { it.id == " + id + " }.anonymizedAt", notNullValue())
            .body("items.find { it.id == " + id + " }.name", nullValue())
            .body("items.find { it.id == " + id + " }.phone", nullValue())
            .body("items.find { it.id == " + id + " }.email", nullValue())
            .body("items.find { it.id == " + id + " }.contactUnavailable", equalTo(false))
            .body("items.find { it.id == " + id + " }.payload.company", nullValue())
            .body("items.find { it.id == " + id + " }.payload.topics.size()", equalTo(0))
            .body("items.find { it.id == " + kept + " }.anonymizedAt", nullValue())
            .body("items.find { it.id == " + kept + " }.phone", equalTo("010-****-0002"));
    }

    @Test
    void 파기된_신청의_원문_연락처는_410_lead_anonymized이고_기록하지_않는다() {
        long id = submit("form-a", "010-1111-0001");
        createdAt(id, now().minus(Duration.ofDays(31)));
        runManually();

        authed().get("/api/admin/leads/{id}/contact", id).then().statusCode(410)
            .contentType(containsString("application/problem+json"))
            .body("code", equalTo("lead_anonymized"))
            .body("type", equalTo("https://leadmagnet.example/problems/lead_anonymized"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'lead.contact.view'",
            Integer.class)).isZero();
    }

    @Test
    void 파기된_신청은_CSV에서_연락처와_추가_항목이_비고_파기됨으로_표시된다() {
        long id = submit("form-a", "010-1111-0001");
        submit("form-a", "010-1111-0002");
        createdAt(id, Instant.parse("2025-01-01T00:00:00Z"));
        runManually();

        String csv = authed().get("/api/admin/campaigns/{id}/leads.csv?reveal=true", campaign).then().statusCode(200)
            .extract().asString();

        List<String> lines = csv.lines().toList();
        assertThat(lines.get(0)).endsWith(",추가 항목,파기");
        assertThat(lines.get(1)).isEqualTo("2025-01-01 09:00:00,,,,direct,,봄 특강 신청,,파기됨");
        assertThat(lines.get(2)).contains("010-1111-0002").endsWith(",");
    }

    @Test
    void 파기된_연락처로_같은_폼에_다시_신청할_수_있다() {
        long id = submit("form-a", "010-1111-0001");
        createdAt(id, now().minus(Duration.ofDays(31)));
        runManually();

        long again = submit("form-a", "010-1111-0001");

        assertThat(again).isGreaterThan(id);
        assertThat(anonymized(again)).isFalse();
    }

    @Test
    void 신청하면_폼의_동의_문구와_sha256을_저장하고_증빙_API로_준다() {
        long id = submit("form-a", "010-1111-0001");

        Map<String, Object> row = jdbc.queryForMap("SELECT consent_text, consent_sha256, "
            + "consent_sha256 = encode(sha256(convert_to(consent_text, 'UTF8')), 'hex') AS matches FROM leads WHERE id = ?", id);
        assertThat(row.get("consent_text")).isEqualTo(CONSENT);
        assertThat(row.get("matches")).isEqualTo(true);
        authed().get("/api/admin/leads/{id}/consent", id).then().statusCode(200)
            .body("consentText", equalTo(CONSENT))
            .body("consentSha256", equalTo(row.get("consent_sha256")))
            .body("consentAt", notNullValue())
            .body("anonymizedAt", nullValue());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action NOT LIKE 'auth.%'", Integer.class))
            .isZero();
    }

    @Test
    void 파기된_신청도_동의_증빙은_남아_있다() {
        long id = submit("form-a", "010-1111-0001");
        createdAt(id, now().minus(Duration.ofDays(31)));
        runManually();

        authed().get("/api/admin/leads/{id}/consent", id).then().statusCode(200)
            .body("consentText", equalTo(CONSENT)).body("anonymizedAt", notNullValue());
    }

    @Test
    void 없는_신청의_동의_증빙은_404이고_토큰이_없으면_401이다() {
        authed().get("/api/admin/leads/{id}/consent", 99999).then().statusCode(404).body("code", equalTo("not_found"));
        RestAssured.get("/api/admin/leads/{id}/consent", 1).then().statusCode(401);
    }

    @Test
    void 파기_실행과_조회의_로그에_원문을_남기지_않는다(CapturedOutput output) {
        long id = submit("form-a", "010-1111-0001");
        createdAt(id, now().minus(Duration.ofDays(31)));

        runManually();
        authed().get("/api/admin/campaigns/{id}/leads", campaign).then().statusCode(200);
        authed().get("/api/admin/campaigns/{id}/leads.csv", campaign).then().statusCode(200);
        authed().get("/api/admin/leads/{id}/consent", id).then().statusCode(200);

        assertThat(output.getAll()).contains("보존 기간 파기 완료").doesNotContain("홍길동").doesNotContain("01011110001")
            .doesNotContain("010-1111-0001").doesNotContain("secret.person01011110001@example.com").doesNotContain("하늘상사");
    }

    long lockKey() {
        return 0x4C4D_5245_5441_494EL;
    }

    static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
