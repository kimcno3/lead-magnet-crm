package com.leadmagnet.crm.stats.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import com.leadmagnet.crm.auth.domain.Operator;
import com.leadmagnet.crm.auth.domain.OperatorRepository;
import com.leadmagnet.crm.support.AdminFixtures;
import com.leadmagnet.crm.support.AdminLogin;
import com.leadmagnet.crm.support.DatabaseCleaner;
import com.leadmagnet.crm.support.IntegrationTest;
import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 성과 지표 (ADR 0005). 숫자는 JdbcTemplate으로 시각까지 정해 넣은 visits·leads에서 손으로 계산한 값이다.
 *
 * 기본 시드: 캠페인 A (폼 a1, a2), 시각은 모두 Asia/Seoul(KST).
 * <pre>
 *  #  종류  폼  채널       방문자  시각
 *  1  방문  a1  instagram  V1      03-10 10:00
 *  2  방문  a1  instagram  V1      03-11 10:00   (같은 방문자 재방문)
 *  3  방문  a1  x          V2      03-10 11:00
 *  4  방문  a2  direct     V3      03-12 09:00
 *  5  방문  a2  x          V2      03-12 12:00
 *  6  신청  a1  instagram  V1      03-10 10:30
 *  7  신청  a2  x          V2      03-12 12:30
 *
 *  캠페인 전체: visits 5, visitors {V1,V2,V3}=3, leads 2, converted {V1,V2}=2, rate 2/3=0.6667
 *  instagram:  visits 2, visitors 1(V1), leads 1, converted 1, rate 1.0000
 *  x:          visits 2, visitors 1(V2), leads 1, converted 1, rate 1.0000
 *  direct:     visits 1, visitors 1(V3), leads 0, converted 0, rate 0.0000
 *  youtube, threads: 모두 0
 *  기간 3/12~(to 없음): visits {4,5}=2, visitors {V3,V2}=2, leads {7}=1, converted {V2}=1 (V1은 3/10에 신청했으므로 제외), rate 0.5
 * </pre>
 */
@IntegrationTest
class StatsApiTest {

    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    static final UUID V1 = UUID.fromString("00000000-0000-4000-8000-000000000001");
    static final UUID V2 = UUID.fromString("00000000-0000-4000-8000-000000000002");
    static final UUID V3 = UUID.fromString("00000000-0000-4000-8000-000000000003");

    @LocalServerPort
    int adminPort;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    OperatorRepository operators;

    @Autowired
    PasswordEncoder passwordEncoder;

    String token;
    int campaign;
    int a1;
    int a2;
    int phoneSeq;

    @BeforeEach
    void setUp() {
        RestAssured.port = adminPort;
        DatabaseCleaner.truncateAll(jdbc);
        phoneSeq = 0;
        operators.save(new Operator("owner@example.com", passwordEncoder.encode("correct-horse-battery"), Instant.now()));
        token = AdminLogin.accessToken("owner@example.com", "correct-horse-battery");
        campaign = AdminFixtures.createCampaign(token, "A 캠페인");
        a1 = AdminFixtures.createPublishedFormIn(token, campaign, "form-a1", AdminFixtures.FORM_HTML);
        a2 = AdminFixtures.createPublishedFormIn(token, campaign, "form-a2", AdminFixtures.FORM_HTML);
        for (int form : new int[] {a1, a2}) {
            AdminFixtures.createLink(token, form, "instagram");
            AdminFixtures.createLink(token, form, "x");
        }
    }

    RequestSpecification authed() {
        return RestAssured.given().auth().oauth2(token);
    }

    static OffsetDateTime kst(String dateTime) {
        return ZonedDateTime.of(LocalDateTime.parse(dateTime), KST).toOffsetDateTime();
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
            form, linkId(form, channel), channel, visitor, Timestamp.from(kst(kstTime).toInstant()));
    }

    void lead(int form, int campaignId, String channel, UUID visitor, String kstTime) {
        jdbc.update("INSERT INTO leads (form_id, campaign_id, link_id, channel, visitor_id, phone, consent_at, "
            + "created_at) VALUES (?, ?, ?, ?, ?, ?, now(), ?)", form, campaignId, linkId(form, channel), channel,
            visitor, "0100000%04d".formatted(++phoneSeq), Timestamp.from(kst(kstTime).toInstant()));
    }

    void seedBase() {
        visit(a1, "instagram", V1, "2026-03-10T10:00:00");
        visit(a1, "instagram", V1, "2026-03-11T10:00:00");
        visit(a1, "x", V2, "2026-03-10T11:00:00");
        visit(a2, "direct", V3, "2026-03-12T09:00:00");
        visit(a2, "x", V2, "2026-03-12T12:00:00");
        lead(a1, campaign, "instagram", V1, "2026-03-10T10:30:00");
        lead(a2, campaign, "x", V2, "2026-03-12T12:30:00");
    }

    @Test
    void 캠페인_지표는_손계산한_값과_같다() {
        seedBase();

        authed().get("/api/admin/campaigns/{id}/stats", campaign).then().statusCode(200)
            .body("campaignId", equalTo(campaign)).body("from", nullValue()).body("to", nullValue())
            .body("visits", equalTo(5)).body("visitors", equalTo(3)).body("leads", equalTo(2))
            .body("convertedVisitors", equalTo(2)).body("conversionRate", is(0.6667f));
    }

    @Test
    void 전환율은_JSON_숫자로_소수_넷째_자리까지_나간다() {
        seedBase();

        String json = authed().get("/api/admin/campaigns/{id}/stats", campaign).then().statusCode(200)
            .extract().asString();

        assertThat(json).contains("\"conversionRate\":0.6667");
    }

    @Test
    void 채널별_지표는_다섯_채널을_정해진_순서로_0_포함해_돌려준다() {
        seedBase();

        authed().get("/api/admin/campaigns/{id}/stats/channels", campaign).then().statusCode(200)
            .body("campaignId", equalTo(campaign))
            .body("channels.channel", equalTo(List.of("instagram", "x", "youtube", "threads", "direct")))
            .body("channels.visits", equalTo(List.of(2, 2, 0, 0, 1)))
            .body("channels.visitors", equalTo(List.of(1, 1, 0, 0, 1)))
            .body("channels.leads", equalTo(List.of(1, 1, 0, 0, 0)))
            .body("channels.convertedVisitors", equalTo(List.of(1, 1, 0, 0, 0)))
            .body("channels.conversionRate", equalTo(List.of(1.0f, 1.0f, 0.0f, 0.0f, 0.0f)));
    }

    @Test
    void 채널별_전환은_같은_채널_안의_교집합이다() {
        seedBase();
        // V3는 direct로 방문했고 instagram 링크로 신청했다(방문 기록이 다른 채널).
        lead(a1, campaign, "instagram", V3, "2026-03-12T15:00:00");

        // 캠페인 전체: V3도 방문+신청 → leads 3, converted {V1,V2,V3}=3, rate 1.0000
        authed().get("/api/admin/campaigns/{id}/stats", campaign).then().statusCode(200)
            .body("leads", equalTo(3)).body("convertedVisitors", equalTo(3)).body("conversionRate", is(1.0f));
        // instagram: leads 2(V1,V3) 중 방문한 instagram 방문자는 V1뿐 → converted 1. direct: 신청 없음 → 0
        authed().get("/api/admin/campaigns/{id}/stats/channels", campaign).then().statusCode(200)
            .body("channels[0].leads", equalTo(2)).body("channels[0].convertedVisitors", equalTo(1))
            .body("channels[4].leads", equalTo(0)).body("channels[4].convertedVisitors", equalTo(0));
    }

    @Test
    void visitor_id가_없는_신청은_신청_수에는_들어가고_전환에는_들어가지_않는다() {
        seedBase();
        lead(a2, campaign, "direct", null, "2026-03-12T13:00:00");

        authed().get("/api/admin/campaigns/{id}/stats", campaign).then().statusCode(200)
            .body("leads", equalTo(3)).body("convertedVisitors", equalTo(2)).body("conversionRate", is(0.6667f));
        authed().get("/api/admin/campaigns/{id}/stats/channels", campaign).then().statusCode(200)
            .body("channels[4].leads", equalTo(1)).body("channels[4].convertedVisitors", equalTo(0));
    }

    @Test
    void 기간을_주면_기간_안의_방문과_신청만_교집합으로_센다() {
        seedBase();

        // 3/12부터: V1의 신청은 3/10이라 기간 밖 → V1은 전환 방문자가 아니다(방문도 3/12 이후엔 없다)
        authed().queryParam("from", "2026-03-12").get("/api/admin/campaigns/{id}/stats", campaign).then()
            .statusCode(200).body("from", equalTo("2026-03-12")).body("to", nullValue())
            .body("visits", equalTo(2)).body("visitors", equalTo(2)).body("leads", equalTo(1))
            .body("convertedVisitors", equalTo(1)).body("conversionRate", is(0.5f));
        // 3/11까지(to만): 방문 {1,2,3} = 3, 방문자 {V1,V2}, 신청 {6}, 전환 {V1}
        authed().queryParam("to", "2026-03-11").get("/api/admin/campaigns/{id}/stats", campaign).then()
            .statusCode(200).body("from", nullValue()).body("to", equalTo("2026-03-11"))
            .body("visits", equalTo(3)).body("visitors", equalTo(2)).body("leads", equalTo(1))
            .body("convertedVisitors", equalTo(1)).body("conversionRate", is(0.5f));
    }

    @Test
    void 기간_경계는_서울_자정이다() {
        visit(a1, "instagram", V1, "2026-03-10T23:59:59"); // 3/11 이전
        visit(a1, "instagram", V2, "2026-03-11T00:00:00"); // 3/11 시작, 포함
        visit(a1, "instagram", V3, "2026-03-11T23:59:59"); // 3/11 끝, 포함
        visit(a1, "instagram", UUID.randomUUID(), "2026-03-12T00:00:00"); // 3/12 시작, 제외
        lead(a1, campaign, "instagram", V1, "2026-03-11T00:00:00"); // 신청은 포함, 하지만 V1의 방문은 기간 밖
        lead(a1, campaign, "instagram", V3, "2026-03-12T00:00:00"); // 신청이 기간 밖

        // 방문 {V2 00:00:00, V3 23:59:59} = 2, 신청 {V1 00:00:00} = 1, 둘 다 겹치는 방문자 없음 → 전환 0
        authed().queryParams("from", "2026-03-11", "to", "2026-03-11")
            .get("/api/admin/campaigns/{id}/stats", campaign).then().statusCode(200)
            .body("visits", equalTo(2)).body("visitors", equalTo(2)).body("leads", equalTo(1))
            .body("convertedVisitors", equalTo(0)).body("conversionRate", is(0.0f));
    }

    @Test
    void 방문이_없으면_전환율은_0이다() {
        String json = authed().get("/api/admin/campaigns/{id}/stats", campaign).then().statusCode(200)
            .body("visits", equalTo(0)).body("visitors", equalTo(0)).body("leads", equalTo(0))
            .body("convertedVisitors", equalTo(0)).body("conversionRate", is(0.0f))
            .extract().asString();

        assertThat(json).contains("\"conversionRate\":0.0000");
        authed().get("/api/admin/campaigns/{id}/stats/channels", campaign).then().statusCode(200)
            .body("channels.size()", equalTo(5)).body("channels.visits", equalTo(List.of(0, 0, 0, 0, 0)));
    }

    @Test
    void 다른_캠페인의_데이터는_섞이지_않는다() {
        seedBase();
        int other = AdminFixtures.createCampaign(token, "B 캠페인");
        int b1 = AdminFixtures.createPublishedFormIn(token, other, "form-b1", AdminFixtures.FORM_HTML);
        AdminFixtures.createLink(token, b1, "youtube");
        visit(b1, "youtube", V1, "2026-03-10T10:00:00");
        visit(b1, "direct", V3, "2026-03-10T10:00:00");
        lead(b1, other, "youtube", V1, "2026-03-10T11:00:00");

        authed().get("/api/admin/campaigns/{id}/stats", campaign).then().statusCode(200)
            .body("visits", equalTo(5)).body("visitors", equalTo(3)).body("leads", equalTo(2))
            .body("convertedVisitors", equalTo(2));
        // B: V1이 A에서 방문했어도 B의 교집합은 B 안에서만 본다. visits 2, visitors {V1,V3}=2, leads 1, converted {V1}=1
        authed().get("/api/admin/campaigns/{id}/stats", other).then().statusCode(200)
            .body("visits", equalTo(2)).body("visitors", equalTo(2)).body("leads", equalTo(1))
            .body("convertedVisitors", equalTo(1)).body("conversionRate", is(0.5f));
        authed().get("/api/admin/campaigns/{id}/stats/channels", other).then().statusCode(200)
            .body("channels.visits", equalTo(List.of(0, 0, 1, 0, 1)));
    }

    @Test
    void 다른_캠페인에만_있는_방문이나_신청은_교집합에_들어가지_않는다() {
        seedBase();
        int other = AdminFixtures.createCampaign(token, "B 캠페인");
        int b1 = AdminFixtures.createPublishedFormIn(token, other, "form-b1", AdminFixtures.FORM_HTML);
        AdminFixtures.createLink(token, b1, "youtube");
        UUID v4 = UUID.fromString("00000000-0000-4000-8000-000000000004");
        // V4: B에서만 방문, 신청은 A에만 있다. V3: A에서만 방문(direct), 신청은 B에만 있다.
        visit(b1, "youtube", v4, "2026-03-10T10:00:00");
        lead(a1, campaign, "instagram", v4, "2026-03-10T12:00:00");
        lead(b1, other, "youtube", V3, "2026-03-10T12:00:00");

        // A: visits 5, visitors {V1,V2,V3}=3, leads {V1,V2,V4}=3, converted {V1,V2}=2 (V3의 신청은 B, V4의 방문은 B)
        authed().get("/api/admin/campaigns/{id}/stats", campaign).then().statusCode(200)
            .body("visits", equalTo(5)).body("visitors", equalTo(3)).body("leads", equalTo(3))
            .body("convertedVisitors", equalTo(2)).body("conversionRate", is(0.6667f));
        // B: visits 1, visitors {V4}=1, leads {V3}=1, converted 0
        authed().get("/api/admin/campaigns/{id}/stats", other).then().statusCode(200)
            .body("visits", equalTo(1)).body("visitors", equalTo(1)).body("leads", equalTo(1))
            .body("convertedVisitors", equalTo(0)).body("conversionRate", is(0.0f));
        // 목록 엔드포인트도 같은 값: 최신순이라 B, A
        authed().get("/api/admin/stats/campaigns").then().statusCode(200)
            .body("campaigns.campaignId", equalTo(List.of(other, campaign)))
            .body("campaigns.leads", equalTo(List.of(1, 3)))
            .body("campaigns.convertedVisitors", equalTo(List.of(0, 2)));
    }

    @Test
    void 기간_내_방문이_없는_신청_방문자는_전환_방문자가_아니다() {
        UUID v4 = UUID.fromString("00000000-0000-4000-8000-000000000004");
        visit(a1, "instagram", V1, "2026-03-10T10:00:00");
        lead(a1, campaign, "instagram", v4, "2026-03-10T11:00:00"); // V4는 방문 기록이 전혀 없다

        authed().get("/api/admin/campaigns/{id}/stats", campaign).then().statusCode(200)
            .body("visitors", equalTo(1)).body("leads", equalTo(1)).body("convertedVisitors", equalTo(0))
            .body("conversionRate", is(0.0f));
        authed().get("/api/admin/campaigns/{id}/stats/channels", campaign).then().statusCode(200)
            .body("channels[0].leads", equalTo(1)).body("channels[0].convertedVisitors", equalTo(0));
        authed().get("/api/admin/stats/campaigns").then().statusCode(200)
            .body("campaigns[0].convertedVisitors", equalTo(0));
    }

    @Test
    void 캠페인_목록_지표는_최신순이고_데이터가_없는_캠페인도_포함한다() {
        seedBase();
        int second = AdminFixtures.createCampaign(token, "B 캠페인");
        int third = AdminFixtures.createCampaign(token, "C 캠페인");

        authed().get("/api/admin/stats/campaigns").then().statusCode(200)
            .body("from", nullValue()).body("to", nullValue())
            .body("campaigns.campaignId", equalTo(List.of(third, second, campaign)))
            .body("campaigns.name", equalTo(List.of("C 캠페인", "B 캠페인", "A 캠페인")))
            .body("campaigns.status", equalTo(List.of("active", "active", "active")))
            .body("campaigns.visits", equalTo(List.of(0, 0, 5)))
            .body("campaigns.visitors", equalTo(List.of(0, 0, 3)))
            .body("campaigns.leads", equalTo(List.of(0, 0, 2)))
            .body("campaigns.convertedVisitors", equalTo(List.of(0, 0, 2)))
            .body("campaigns.conversionRate", equalTo(List.of(0.0f, 0.0f, 0.6667f)));
        authed().queryParam("from", "2026-03-12").get("/api/admin/stats/campaigns").then().statusCode(200)
            .body("from", equalTo("2026-03-12")).body("campaigns[2].visits", equalTo(2))
            .body("campaigns[2].conversionRate", is(0.5f));
    }

    @Test
    void 없는_캠페인은_404다() {
        authed().get("/api/admin/campaigns/{id}/stats", 99999).then().statusCode(404).body("code", equalTo("not_found"));
        authed().get("/api/admin/campaigns/{id}/stats/channels", 99999).then().statusCode(404)
            .body("code", equalTo("not_found"));
    }

    @Test
    void 토큰이_없으면_세_엔드포인트_모두_401이다() {
        for (String path : List.of("/api/admin/campaigns/" + campaign + "/stats",
                "/api/admin/campaigns/" + campaign + "/stats/channels", "/api/admin/stats/campaigns")) {
            RestAssured.get(path).then().statusCode(401).contentType(containsString("application/problem+json"));
        }
    }

    @Test
    void from이_to보다_늦으면_세_엔드포인트_모두_422다() {
        for (String path : List.of("/api/admin/campaigns/" + campaign + "/stats",
                "/api/admin/campaigns/" + campaign + "/stats/channels", "/api/admin/stats/campaigns")) {
            authed().queryParams("from", "2026-03-12", "to", "2026-03-11").get(path).then().statusCode(422)
                .body("code", equalTo("validation_failed")).body("errors.field", hasItem("from"));
        }
    }

    @Test
    void 날짜_형식이_틀리면_400이_아니라_422다() {
        authed().queryParams("from", "2026/03/12", "to", "내일").get("/api/admin/campaigns/{id}/stats", campaign).then()
            .statusCode(422).body("errors.field", hasItem("from")).body("errors.field", hasItem("to"));
        authed().queryParam("to", "2026-02-30").get("/api/admin/stats/campaigns").then().statusCode(422)
            .body("errors.field", hasItem("to"));
        authed().queryParam("from", "").get("/api/admin/campaigns/{id}/stats/channels", campaign).then()
            .statusCode(422).body("errors.field", hasItem("from"));
    }
}
