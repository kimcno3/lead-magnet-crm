package com.leadmagnet.crm.stats.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import com.leadmagnet.crm.auth.domain.Operator;
import com.leadmagnet.crm.auth.domain.OperatorRepository;
import com.leadmagnet.crm.lead.application.ContactProtector;
import com.leadmagnet.crm.lead.domain.ProtectedContact;
import com.leadmagnet.crm.support.AdminFixtures;
import com.leadmagnet.crm.support.AdminLogin;
import com.leadmagnet.crm.support.DatabaseCleaner;
import com.leadmagnet.crm.support.IntegrationTest;
import com.leadmagnet.crm.support.MutableClock;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 유입 현황 대시보드 (ADR 0005). 숫자는 JdbcTemplate으로 시각까지 정해 넣은 visits·leads에서 손으로 계산한 값이다.
 *
 * 시드: 캠페인 A(폼 a1, a2), 그 뒤에 만든 캠페인 B(폼 b1). 크리에이터 하늘은 a1 instagram 링크. 시각은 모두 KST.
 * <pre>
 *  #   종류     폼  채널            방문자  시각
 *  1   방문     a1  instagram(하늘)  V1     03-10 10:00
 *  2   방문     a1  instagram(하늘)  V1     03-11 10:00
 *  3   방문     a1  x               V2     03-10 11:00
 *  4   방문     a2  direct          V3     03-12 09:00
 *  5   방문     b1  x               V2     03-12 12:00
 *  6   방문     a2  direct          V6     03-10 00:00:00   (기간 시작 경계, UTC로는 03-09)
 *  7   방문     a2  direct          V6     03-12 23:59:59   (기간 끝 경계)
 *  8   봇 방문  a2  direct          V4     03-11 14:00      (어디에도 세지 않음)
 *  9   방문     a1  x               V5     03-09 23:59:59   (기간 밖)
 *  10  방문     a1  x               V5     03-13 00:00:00   (기간 밖)
 *  L1  신청     a1  instagram(하늘)  V1     03-10 10:30  홍길동
 *  L2  신청     b1  x               V2     03-12 12:30  김철수
 *  L3  신청     a2  direct          V4     03-11 15:00  이영희 (봇 방문만 있음 → 전환 아님)
 *  L4  신청     a1  x               V5     03-13 00:00:00  (기간 밖)
 *
 *  기간 03-10~03-12
 *  전체: visits 7(1~7), visitors {V1,V2,V3,V6}=4, leads 3, converted {V1(A), V2(B)}=2, rate 0.5
 *  일별: 03-10 visits {1,3,6}=3 visitors 3 leads 1 converted 1(V1)
 *        03-11 visits {2}=1 visitors 1 leads 1 converted 0(V4는 봇 방문뿐)
 *        03-12 visits {4,5,7}=3 visitors 3 leads 1 converted 1(V2, B에서 같은 날 방문·신청)
 *  폼: b1 1/1/1/1 rate 1.0, a1 3/2/1/1 rate 0.5, a2 3/2/1/0 rate 0 (최신 캠페인 먼저, 캠페인 안에서는 폼 id순)
 *  채널: instagram 2/1/1/1, x {3,5}=2/{V2}=1/1/1, youtube·threads 0, direct {4,6,7}=3/{V3,V6}=2/1/0
 *  크리에이터: 하늘 2/1/1/1, 직접·자체 링크 5/{V2,V3,V6}=3/2/1
 *  최근 신청: L2, L3, L1
 * </pre>
 */
@IntegrationTest
class DashboardStatsApiTest {

    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    static final UUID V1 = uuid(1);
    static final UUID V2 = uuid(2);
    static final UUID V3 = uuid(3);
    static final UUID V4 = uuid(4);
    static final UUID V5 = uuid(5);
    static final UUID V6 = uuid(6);
    static final String PERIOD = "?from=2026-03-10&to=2026-03-12";

    @LocalServerPort
    int adminPort;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    OperatorRepository operators;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    ContactProtector protector;

    @Autowired
    MutableClock clock;

    String token;
    int campaignA;
    int campaignB;
    int a1;
    int a2;
    int b1;
    int sky;
    int phoneSeq;

    static UUID uuid(int n) {
        return UUID.fromString("00000000-0000-4000-8000-%012d".formatted(n));
    }

    @BeforeEach
    void setUp() {
        RestAssured.port = adminPort;
        DatabaseCleaner.truncateAll(jdbc);
        phoneSeq = 0;
        operators.save(new Operator("owner@example.com", passwordEncoder.encode("correct-horse-battery"), Instant.now()));
        token = AdminLogin.accessToken("owner@example.com", "correct-horse-battery");
        campaignA = AdminFixtures.createCampaign(token, "A 캠페인");
        a1 = AdminFixtures.createPublishedFormIn(token, campaignA, "form-a1", AdminFixtures.FORM_HTML);
        a2 = AdminFixtures.createPublishedFormIn(token, campaignA, "form-a2", AdminFixtures.FORM_HTML);
        campaignB = AdminFixtures.createCampaign(token, "B 캠페인");
        b1 = AdminFixtures.createPublishedFormIn(token, campaignB, "form-b1", AdminFixtures.FORM_HTML);
        sky = authed().contentType(ContentType.JSON).body(Map.of("name", "하늘")).post("/api/admin/creators").then()
            .statusCode(201).extract().path("id");
        createLink(a1, "instagram", sky);
        createLink(a1, "x", null);
        createLink(b1, "x", null);
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    RequestSpecification authed() {
        return RestAssured.given().auth().oauth2(token);
    }

    void createLink(int form, String channel, Integer creator) {
        Map<String, Object> body = new HashMap<>();
        body.put("channel", channel);
        body.put("creatorId", creator);
        authed().contentType(ContentType.JSON).body(body).post("/api/admin/forms/{id}/links", form).then()
            .statusCode(201);
    }

    static Timestamp kst(String dateTime) {
        return Timestamp.from(ZonedDateTime.of(LocalDateTime.parse(dateTime), KST).toInstant());
    }

    Long linkId(int form, String channel) {
        if ("direct".equals(channel)) {
            return null;
        }
        return jdbc.queryForObject("SELECT id FROM distribution_links WHERE form_id = ? AND channel = ?", Long.class,
            form, channel);
    }

    void visit(int form, String channel, UUID visitor, String kstTime) {
        jdbc.update("INSERT INTO visits (form_id, link_id, channel, visitor_id, occurred_at) VALUES (?, ?, ?, ?, ?)",
            form, linkId(form, channel), channel, visitor, kst(kstTime));
    }

    void botVisit(int form, String channel, UUID visitor, String kstTime) {
        jdbc.update("INSERT INTO visits (form_id, link_id, channel, visitor_id, is_bot, occurred_at) "
            + "VALUES (?, ?, ?, ?, true, ?)", form, linkId(form, channel), channel, visitor, kst(kstTime));
    }

    /** 이름·전화번호·이메일을 실제 암호화해 넣는다(최근 신청의 가린 이름을 확인하려고). */
    void lead(int form, int campaign, String channel, UUID visitor, String kstTime, String name) {
        int seq = ++phoneSeq;
        ProtectedContact c = protector.protect(name, "0109999%04d".formatted(seq), "secret%d@example.com".formatted(seq));
        jdbc.update("INSERT INTO leads (form_id, campaign_id, link_id, channel, visitor_id, name_enc, phone_enc, email_enc, "
            + "phone_hash, email_hash, consent_at, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), ?)",
            form, campaign, linkId(form, channel), channel, visitor, c.nameEnc(), c.phoneEnc(), c.emailEnc(),
            c.phoneHash(), c.emailHash(), kst(kstTime));
    }

    void seed() {
        visit(a1, "instagram", V1, "2026-03-10T10:00:00");
        visit(a1, "instagram", V1, "2026-03-11T10:00:00");
        visit(a1, "x", V2, "2026-03-10T11:00:00");
        visit(a2, "direct", V3, "2026-03-12T09:00:00");
        visit(b1, "x", V2, "2026-03-12T12:00:00");
        visit(a2, "direct", V6, "2026-03-10T00:00:00");
        visit(a2, "direct", V6, "2026-03-12T23:59:59");
        botVisit(a2, "direct", V4, "2026-03-11T14:00:00");
        visit(a1, "x", V5, "2026-03-09T23:59:59");
        visit(a1, "x", V5, "2026-03-13T00:00:00");
        lead(a1, campaignA, "instagram", V1, "2026-03-10T10:30:00", "홍길동");
        lead(b1, campaignB, "x", V2, "2026-03-12T12:30:00", "김철수");
        lead(a2, campaignA, "direct", V4, "2026-03-11T15:00:00", "이영희");
        lead(a1, campaignA, "x", V5, "2026-03-13T00:00:00", "박기간");
    }

    @Test
    void 전체_지표는_손계산한_값과_같고_봇_방문은_빠진다() {
        seed();

        authed().get("/api/admin/stats/dashboard" + PERIOD).then().statusCode(200)
            .body("from", equalTo("2026-03-10")).body("to", equalTo("2026-03-12"))
            .body("totals.visits", equalTo(7)).body("totals.visitors", equalTo(4))
            .body("totals.leads", equalTo(3)).body("totals.convertedVisitors", equalTo(2))
            .body("totals.conversionRate", is(0.5f));
    }

    @Test
    void 일별_행은_서울_날짜로_묶고_같은_날_방문과_신청을_전환으로_센다() {
        seed();

        authed().get("/api/admin/stats/dashboard" + PERIOD).then().statusCode(200)
            .body("daily.date", equalTo(List.of("2026-03-10", "2026-03-11", "2026-03-12")))
            .body("daily.visits", equalTo(List.of(3, 1, 3)))
            .body("daily.visitors", equalTo(List.of(3, 1, 3)))
            .body("daily.leads", equalTo(List.of(1, 1, 1)))
            .body("daily.convertedVisitors", equalTo(List.of(1, 0, 1)));
    }

    @Test
    void 일별_행은_데이터가_없는_날도_0으로_채운다() {
        seed();

        // 03-13 00:00 KST의 방문·신청(V5)은 03-13 행이다. 03-14, 03-15는 비어 있다.
        authed().get("/api/admin/stats/dashboard?from=2026-03-13&to=2026-03-15").then().statusCode(200)
            .body("daily.date", equalTo(List.of("2026-03-13", "2026-03-14", "2026-03-15")))
            .body("daily.visits", equalTo(List.of(1, 0, 0)))
            .body("daily.visitors", equalTo(List.of(1, 0, 0)))
            .body("daily.leads", equalTo(List.of(1, 0, 0)))
            .body("daily.convertedVisitors", equalTo(List.of(1, 0, 0)))
            .body("forms.formId", equalTo(List.of(a1)));
    }

    @Test
    void 기간_경계는_from_00시_포함_to_다음날_00시_제외다() {
        seed();

        // 03-09 23:59:59(9번)만 03-09 하루에 들어간다
        authed().get("/api/admin/stats/dashboard?from=2026-03-09&to=2026-03-09").then().statusCode(200)
            .body("totals.visits", equalTo(1)).body("totals.leads", equalTo(0))
            .body("daily.date", equalTo(List.of("2026-03-09")));
        // 03-12 하루: 4, 5, 7(23:59:59)번과 L2. 03-13 00:00의 10번·L4는 빠진다
        authed().get("/api/admin/stats/dashboard?from=2026-03-12&to=2026-03-12").then().statusCode(200)
            .body("totals.visits", equalTo(3)).body("totals.leads", equalTo(1));
    }

    @Test
    void 기간을_주지_않으면_서울_오늘까지_30일이다() {
        // 2026-03-12 15:30 UTC = 03-13 00:30 KST → 서울 오늘은 03-13, 시작은 02-12
        clock.freezeAt(Instant.parse("2026-03-12T15:30:00Z"));
        token = AdminLogin.accessToken("owner@example.com", "correct-horse-battery");
        seed();

        authed().get("/api/admin/stats/dashboard").then().statusCode(200)
            .body("from", equalTo("2026-02-12")).body("to", equalTo("2026-03-13"))
            .body("daily.size()", equalTo(30))
            .body("daily[0].date", equalTo("2026-02-12")).body("daily[29].date", equalTo("2026-03-13"))
            .body("totals.visits", equalTo(9)).body("totals.leads", equalTo(4));
    }

    @Test
    void 폼_시트는_방문이나_신청이_있는_폼만_캠페인과_함께_돌려준다() {
        seed();
        jdbc.update("UPDATE forms SET status = 'closed' WHERE id = ?", a2);

        authed().get("/api/admin/stats/dashboard" + PERIOD).then().statusCode(200)
            .body("forms.formId", equalTo(List.of(b1, a1, a2)))
            .body("forms.campaignId", equalTo(List.of(campaignB, campaignA, campaignA)))
            .body("forms.campaignName", equalTo(List.of("B 캠페인", "A 캠페인", "A 캠페인")))
            .body("forms.status", equalTo(List.of("published", "published", "closed")))
            .body("forms.visits", equalTo(List.of(1, 3, 3)))
            .body("forms.visitors", equalTo(List.of(1, 2, 2)))
            .body("forms.leads", equalTo(List.of(1, 1, 1)))
            .body("forms.convertedVisitors", equalTo(List.of(1, 1, 0)))
            .body("forms.conversionRate", equalTo(List.of(1.0f, 0.5f, 0.0f)))
            .body("forms[0].title", equalTo(jdbc.queryForObject("SELECT title FROM forms WHERE id = ?", String.class, b1)));
    }

    @Test
    void 채널과_크리에이터는_전체_캠페인에_걸쳐_같은_정의로_센다() {
        seed();

        authed().get("/api/admin/stats/dashboard" + PERIOD).then().statusCode(200)
            .body("channels.channel", equalTo(List.of("instagram", "x", "youtube", "threads", "direct")))
            .body("channels.visits", equalTo(List.of(2, 2, 0, 0, 3)))
            .body("channels.visitors", equalTo(List.of(1, 1, 0, 0, 2)))
            .body("channels.leads", equalTo(List.of(1, 1, 0, 0, 1)))
            .body("channels.convertedVisitors", equalTo(List.of(1, 1, 0, 0, 0)))
            .body("creators.size()", equalTo(2))
            .body("creators[0].creator.id", equalTo(sky)).body("creators[0].creator.name", equalTo("하늘"))
            .body("creators[1].creator", nullValue())
            .body("creators.visits", equalTo(List.of(2, 5)))
            .body("creators.visitors", equalTo(List.of(1, 3)))
            .body("creators.leads", equalTo(List.of(1, 2)))
            .body("creators.convertedVisitors", equalTo(List.of(1, 1)));
    }

    @Test
    void 최근_신청은_가린_이름만_있고_전화번호와_이메일은_없다() {
        seed();

        Response response = authed().get("/api/admin/stats/dashboard" + PERIOD);
        response.then().statusCode(200)
            .body("recentLeads.name", equalTo(List.of("김*수", "이*희", "홍*동")))
            .body("recentLeads.campaignName", equalTo(List.of("B 캠페인", "A 캠페인", "A 캠페인")))
            .body("recentLeads.campaignId", equalTo(List.of(campaignB, campaignA, campaignA)))
            .body("recentLeads.channel", equalTo(List.of("x", "direct", "instagram")))
            .body("recentLeads.creatorName", equalTo(java.util.Arrays.asList(null, null, "하늘")))
            .body("recentLeads.createdAt", hasItem("2026-03-12T03:30:00Z"));
        String json = response.asString();
        assertThat(json).doesNotContain("\"phone").doesNotContain("\"email").doesNotContain("01099990")
            .doesNotContain("@example.com").doesNotContain("홍길동");
    }

    @Test
    void 최근_신청은_최신_10건까지만이다() {
        for (int i = 0; i < 12; i++) {
            lead(a1, campaignA, "x", null, "2026-03-11T10:%02d:00".formatted(i), "신청자");
        }

        authed().get("/api/admin/stats/dashboard" + PERIOD).then().statusCode(200)
            .body("recentLeads.size()", equalTo(10))
            .body("recentLeads[0].createdAt", equalTo("2026-03-11T01:11:00Z"))
            .body("recentLeads.name", everyItem(equalTo("신*자")));
    }

    @Test
    void 폼_시트_CSV는_BOM_헤더_행을_쓰고_수식_시작_값을_무력화하며_감사에_남긴다() {
        seed();
        jdbc.update("UPDATE campaigns SET name = ? WHERE id = ?", "=HYPERLINK(\"x\")", campaignB);
        jdbc.update("UPDATE forms SET title = ? WHERE id = ?", "@SUM(1)", b1);

        Response response = authed().get("/api/admin/stats/dashboard.csv" + PERIOD);
        response.then().statusCode(200).contentType(containsCsv())
            .header("Content-Disposition", org.hamcrest.Matchers.containsString("dashboard-forms-2026-03-10-2026-03-12.csv"));
        byte[] bytes = response.asByteArray();
        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        String csv = new String(bytes, StandardCharsets.UTF_8).substring(1);
        String[] lines = csv.split("\r\n");
        assertThat(lines[0]).isEqualTo("캠페인,폼,상태,방문,방문자,신청,전환 방문자,전환율");
        assertThat(lines[1]).isEqualTo("\"'=HYPERLINK(\"\"x\"\")\",'@SUM(1),published,1,1,1,1,1.0000");
        assertThat(lines).hasSize(4);
        assertThat(csv).doesNotContain("@example.com").doesNotContain("01099990");

        Map<String, Object> log = jdbc.queryForMap("SELECT target_type, detail->>'from' AS from_date, "
            + "detail->>'to' AS to_date, (detail->>'count')::int AS count FROM audit_logs WHERE action = 'stats.export'");
        assertThat(log).containsEntry("target_type", "dashboard").containsEntry("from_date", "2026-03-10")
            .containsEntry("to_date", "2026-03-12").containsEntry("count", 3);
    }

    static String containsCsv() {
        return "text/csv;charset=UTF-8";
    }

    @Test
    void 기간이_잘못되면_422다() {
        authed().get("/api/admin/stats/dashboard?from=2026-03-12&to=2026-03-10").then().statusCode(422)
            .body("code", equalTo("validation_failed")).body("errors[0].field", equalTo("from"));
        authed().get("/api/admin/stats/dashboard?from=2026/03/10").then().statusCode(422)
            .body("code", equalTo("validation_failed"));
        authed().get("/api/admin/stats/dashboard.csv?from=2026-03-12&to=2026-03-10").then().statusCode(422)
            .body("code", equalTo("validation_failed"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'stats.export'", Long.class))
            .isZero();
    }

    @Test
    void 기간은_366일까지이고_367일이면_422다() {
        // 2024는 윤년: 2024-01-01 ~ 2024-12-31 = 366일
        authed().get("/api/admin/stats/dashboard?from=2024-01-01&to=2024-12-31").then().statusCode(200)
            .body("daily.size()", equalTo(366));
        authed().get("/api/admin/stats/dashboard.csv?from=2024-01-01&to=2024-12-31").then().statusCode(200);

        authed().get("/api/admin/stats/dashboard?from=2024-01-01&to=2025-01-01").then().statusCode(422)
            .body("code", equalTo("validation_failed"))
            .body("errors[0].field", equalTo("from")).body("errors[0].code", equalTo("range_too_long"));
        authed().get("/api/admin/stats/dashboard.csv?from=2024-01-01&to=2025-01-01").then().statusCode(422)
            .body("code", equalTo("validation_failed")).body("errors[0].code", equalTo("range_too_long"));
        // from만 주면 to는 오늘이므로, 오래전 from도 367일을 넘으면 거절한다
        clock.freezeAt(Instant.parse("2026-03-12T15:30:00Z"));
        token = AdminLogin.accessToken("owner@example.com", "correct-horse-battery");
        authed().get("/api/admin/stats/dashboard?from=2025-03-12").then().statusCode(422)
            .body("errors[0].code", equalTo("range_too_long"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'stats.export'", Long.class))
            .isEqualTo(1);
    }

    @Test
    void 토큰이_없으면_401이다() {
        RestAssured.given().get("/api/admin/stats/dashboard").then().statusCode(401)
            .body("code", equalTo("unauthorized"));
        RestAssured.given().get("/api/admin/stats/dashboard.csv").then().statusCode(401)
            .body("code", equalTo("unauthorized"));
    }
}
