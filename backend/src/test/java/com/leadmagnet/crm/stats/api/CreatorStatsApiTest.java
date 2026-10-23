package com.leadmagnet.crm.stats.api;

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
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 크리에이터별 성과 (ADR 0005 정의 + ADR 0014). 숫자는 JdbcTemplate으로 시각까지 정해 넣은 visits·leads에서 손으로 계산했다.
 * 크리에이터는 방문·신청의 link_id → distribution_links.creator_id로 유도하고, 크리에이터 없는 링크와 direct는
 * "직접·자체 링크" 한 행(creator null)으로 묶는다. 전환 방문자는 같은 크리에이터 안의 교집합이다.
 *
 * 기본 시드: 캠페인 A (폼 a1, a2), 크리에이터 S(하늘), B(바다), D(유리, 링크만 있고 데이터 없음). 시각은 모두 KST.
 * 링크: a1 자체 instagram, a1 S-instagram, a1 S-youtube, a1 B-instagram, a2 S-x, a2 D-threads
 * <pre>
 *  #   종류  폼  링크            방문자  시각
 *  1   방문  a1  S-instagram     V1      03-10 10:00
 *  2   방문  a1  S-instagram     V1      03-11 10:00   (같은 방문자 재방문)
 *  3   방문  a1  S-youtube       V2      03-10 11:00
 *  4   방문  a2  S-x             V3      03-12 09:00
 *  5   방문  a1  B-instagram     V4      03-10 12:00
 *  6   방문  a1  B-instagram     V5      03-12 13:00
 *  7   방문  a1  자체-instagram  V6      03-10 14:00
 *  8   방문  a2  direct          V7      03-11 09:00
 *  L1  신청  a1  S-instagram     V1      03-10 10:30
 *  L2  신청  a1  S-instagram     V2      03-11 11:00   (S-youtube로 방문, S-instagram으로 신청: 같은 크리에이터)
 *  L3  신청  a1  B-instagram     V3      03-12 10:00   (S로 방문, B로 신청: 크리에이터가 다르다)
 *  L4  신청  a1  B-instagram     V4      03-10 12:30
 *  L5  신청  a2  direct          V6      03-10 15:00   (자체 링크로 방문, direct로 신청: 둘 다 "직접·자체 링크")
 *  L6  신청  a2  direct          NULL    03-11 10:00   (방문자 쿠키 없음: 신청에만 들어간다)
 *
 *  S:    visits {1,2,3,4}=4, visitors {V1,V2,V3}=3, leads {L1,L2}=2,
 *        converted: V1(S 방문·S 신청) O, V2(S 방문·S 신청, 채널은 다름) O, V3(S 방문·B 신청) X → 2, rate 2/3=0.6667
 *    채널 instagram: visits 2, visitors {V1}=1, leads {L1,L2}=2, converted {V1}=1 (V2는 instagram 방문 없음), 1.0000
 *         x: 1/1/0/0, youtube: 1/1/0/0 (V2는 youtube로 신청하지 않음), threads: 0
 *  B:    visits {5,6}=2, visitors {V4,V5}=2, leads {L3,L4}=2, converted {V4}=1 (V3는 B 방문 없음), rate 0.5000
 *    채널 instagram: 2/2/2/1/0.5000, 나머지 0
 *  D:    링크만 있음 → 모두 0, rate 0.0000
 *  직접·자체 링크: visits {7,8}=2, visitors {V6,V7}=2, leads {L5,L6}=2, converted {V6}=1, rate 0.5000
 *    채널 instagram: 1/1/0/0, direct: visits 1(V7), visitors 1, leads 2, converted 0 (V6의 방문은 instagram), x·youtube·threads 0
 *  행 순서: 크리에이터 등록순(S, B, D) 다음 직접·자체 링크.
 *
 *  캠페인 전체(기존 SQL, 크리에이터 무관): visits 8, visitors {V1..V7}=7, leads 6,
 *        converted {V1,V2,V3,V4,V6}=5 (캠페인 안 교집합이라 V3도 전환), rate 5/7=0.7143
 *  기간 3/11~: S visits {2,4}=2, visitors {V1,V3}=2, leads {L2}=1, converted 0 (V2의 방문은 3/10, V1의 신청은 3/10)
 * </pre>
 */
@IntegrationTest
class CreatorStatsApiTest {

    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    static final UUID V1 = uuid(1);
    static final UUID V2 = uuid(2);
    static final UUID V3 = uuid(3);
    static final UUID V4 = uuid(4);
    static final UUID V5 = uuid(5);
    static final UUID V6 = uuid(6);
    static final UUID V7 = uuid(7);
    static final UUID V8 = uuid(8);

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
    int sky;
    int sea;
    int yuri;
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
        campaign = AdminFixtures.createCampaign(token, "A 캠페인");
        a1 = AdminFixtures.createPublishedFormIn(token, campaign, "form-a1", AdminFixtures.FORM_HTML);
        a2 = AdminFixtures.createPublishedFormIn(token, campaign, "form-a2", AdminFixtures.FORM_HTML);
        sky = createCreator("하늘");
        sea = createCreator("바다");
        yuri = createCreator("유리");
        createLink(a1, "instagram", null);
        createLink(a1, "instagram", sky);
        createLink(a1, "youtube", sky);
        createLink(a1, "instagram", sea);
        createLink(a2, "x", sky);
        createLink(a2, "threads", yuri);
    }

    RequestSpecification authed() {
        return RestAssured.given().auth().oauth2(token);
    }

    int createCreator(String name) {
        return authed().contentType(ContentType.JSON).body(Map.of("name", name)).post("/api/admin/creators").then()
            .statusCode(201).extract().path("id");
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

    /** creator null이면 크리에이터 없는 링크, channel direct면 링크 없음. */
    Long linkId(int form, String channel, Integer creator) {
        if ("direct".equals(channel)) {
            return null;
        }
        return jdbc.queryForObject("SELECT id FROM distribution_links WHERE form_id = ? AND channel = ? "
            + "AND creator_id IS NOT DISTINCT FROM ?", Long.class, form, channel, creator);
    }

    void visit(int form, String channel, Integer creator, UUID visitor, String kstTime) {
        jdbc.update("INSERT INTO visits (form_id, link_id, channel, visitor_id, occurred_at) VALUES (?, ?, ?, ?, ?)",
            form, linkId(form, channel, creator), channel, visitor, kst(kstTime));
    }

    void lead(int form, int campaignId, String channel, Integer creator, UUID visitor, String kstTime) {
        jdbc.update("INSERT INTO leads (form_id, campaign_id, link_id, channel, visitor_id, phone, consent_at, "
            + "created_at) VALUES (?, ?, ?, ?, ?, ?, now(), ?)", form, campaignId, linkId(form, channel, creator),
            channel, visitor, "0100000%04d".formatted(++phoneSeq), kst(kstTime));
    }

    void seedBase() {
        visit(a1, "instagram", sky, V1, "2026-03-10T10:00:00");
        visit(a1, "instagram", sky, V1, "2026-03-11T10:00:00");
        visit(a1, "youtube", sky, V2, "2026-03-10T11:00:00");
        visit(a2, "x", sky, V3, "2026-03-12T09:00:00");
        visit(a1, "instagram", sea, V4, "2026-03-10T12:00:00");
        visit(a1, "instagram", sea, V5, "2026-03-12T13:00:00");
        visit(a1, "instagram", null, V6, "2026-03-10T14:00:00");
        visit(a2, "direct", null, V7, "2026-03-11T09:00:00");
        lead(a1, campaign, "instagram", sky, V1, "2026-03-10T10:30:00");
        lead(a1, campaign, "instagram", sky, V2, "2026-03-11T11:00:00");
        lead(a1, campaign, "instagram", sea, V3, "2026-03-12T10:00:00");
        lead(a1, campaign, "instagram", sea, V4, "2026-03-10T12:30:00");
        lead(a2, campaign, "direct", null, V6, "2026-03-10T15:00:00");
        lead(a2, campaign, "direct", null, null, "2026-03-11T10:00:00");
    }

    @Test
    void 크리에이터별_지표는_손계산한_값과_같고_자체_링크와_direct는_한_행이다() {
        seedBase();

        authed().get("/api/admin/campaigns/{id}/stats/creators", campaign).then().statusCode(200)
            .body("campaignId", equalTo(campaign)).body("from", nullValue()).body("to", nullValue())
            .body("creators.size()", equalTo(4))
            .body("creators[0].creator.id", equalTo(sky)).body("creators[0].creator.name", equalTo("하늘"))
            .body("creators[1].creator.id", equalTo(sea))
            .body("creators[2].creator.id", equalTo(yuri))
            .body("creators[3].creator", nullValue())
            .body("creators.visits", equalTo(List.of(4, 2, 0, 2)))
            .body("creators.visitors", equalTo(List.of(3, 2, 0, 2)))
            .body("creators.leads", equalTo(List.of(2, 2, 0, 2)))
            .body("creators.convertedVisitors", equalTo(List.of(2, 1, 0, 1)))
            .body("creators.conversionRate", equalTo(List.of(0.6667f, 0.5f, 0.0f, 0.5f)));
    }

    @Test
    void 크리에이터_행의_채널별_지표는_같은_크리에이터_같은_채널_안의_교집합이다() {
        seedBase();

        authed().get("/api/admin/campaigns/{id}/stats/creators", campaign).then().statusCode(200)
            // 크리에이터 행은 링크 채널 네 개, 직접·자체 링크 행은 direct까지 다섯 개
            .body("creators[0].channels.channel", equalTo(List.of("instagram", "x", "youtube", "threads")))
            .body("creators[0].channels.visits", equalTo(List.of(2, 1, 1, 0)))
            .body("creators[0].channels.visitors", equalTo(List.of(1, 1, 1, 0)))
            .body("creators[0].channels.leads", equalTo(List.of(2, 0, 0, 0)))
            .body("creators[0].channels.convertedVisitors", equalTo(List.of(1, 0, 0, 0)))
            .body("creators[0].channels.conversionRate", equalTo(List.of(1.0f, 0.0f, 0.0f, 0.0f)))
            .body("creators[1].channels.convertedVisitors", equalTo(List.of(1, 0, 0, 0)))
            .body("creators[1].channels.conversionRate", equalTo(List.of(0.5f, 0.0f, 0.0f, 0.0f)))
            .body("creators[2].channels.visits", equalTo(List.of(0, 0, 0, 0)))
            .body("creators[3].channels.channel", equalTo(List.of("instagram", "x", "youtube", "threads", "direct")))
            .body("creators[3].channels.visits", equalTo(List.of(1, 0, 0, 0, 1)))
            .body("creators[3].channels.leads", equalTo(List.of(0, 0, 0, 0, 2)))
            .body("creators[3].channels.convertedVisitors", equalTo(List.of(0, 0, 0, 0, 0)));
    }

    @Test
    void 캠페인_전체_지표는_크리에이터와_무관하게_그대로다() {
        seedBase();

        authed().get("/api/admin/campaigns/{id}/stats", campaign).then().statusCode(200)
            .body("visits", equalTo(8)).body("visitors", equalTo(7)).body("leads", equalTo(6))
            .body("convertedVisitors", equalTo(5)).body("conversionRate", is(0.7143f));
    }

    @Test
    void 기간을_주면_기간_안의_방문과_신청만_같은_크리에이터_안에서_교집합으로_센다() {
        seedBase();

        authed().queryParam("from", "2026-03-11").get("/api/admin/campaigns/{id}/stats/creators", campaign).then()
            .statusCode(200).body("from", equalTo("2026-03-11"))
            .body("creators[0].visits", equalTo(2)).body("creators[0].visitors", equalTo(2))
            .body("creators[0].leads", equalTo(1)).body("creators[0].convertedVisitors", equalTo(0));
    }

    @Test
    void 기간_끝은_to_다음날_서울_자정을_포함하지_않는다() {
        // to=03-11 → [.., 03-12 00:00 KST). 모두 S-instagram.
        visit(a1, "instagram", sky, V1, "2026-03-11T23:59:59"); // 포함
        visit(a1, "instagram", sky, V2, "2026-03-12T00:00:00"); // 제외
        lead(a1, campaign, "instagram", sky, V1, "2026-03-12T00:00:00"); // 제외
        lead(a1, campaign, "instagram", sky, V2, "2026-03-11T23:00:00"); // 포함, 하지만 V2의 방문은 기간 밖

        // to=03-11: visits 1, visitors {V1}=1, leads {V2}=1, converted 0 (V1의 신청·V2의 방문이 기간 밖)
        authed().queryParam("to", "2026-03-11").get("/api/admin/campaigns/{id}/stats/creators", campaign).then()
            .statusCode(200).body("from", nullValue()).body("to", equalTo("2026-03-11"))
            .body("creators[0].visits", equalTo(1)).body("creators[0].visitors", equalTo(1))
            .body("creators[0].leads", equalTo(1)).body("creators[0].convertedVisitors", equalTo(0))
            .body("creators[0].channels[0].visits", equalTo(1)).body("creators[0].channels[0].convertedVisitors", equalTo(0));
        // 전체 기간: visits 2, visitors 2, leads 2, converted {V1,V2}=2
        authed().get("/api/admin/campaigns/{id}/stats/creators", campaign).then().statusCode(200)
            .body("creators[0].visits", equalTo(2)).body("creators[0].leads", equalTo(2))
            .body("creators[0].convertedVisitors", equalTo(2)).body("creators[0].channels[0].convertedVisitors", equalTo(2));
    }

    @Test
    void 같은_채널이어도_다른_크리에이터로_신청하면_채널_행에서도_전환이_아니다() {
        seedBase();
        UUID v9 = uuid(9);
        // V9: S-instagram으로 방문, B-instagram으로 신청(채널은 같고 크리에이터가 다르다)
        visit(a1, "instagram", sky, v9, "2026-03-10T16:00:00");
        lead(a1, campaign, "instagram", sea, v9, "2026-03-10T16:30:00");

        // S: visits 5, visitors {V1,V2,V3,V9}=4, leads 2, converted 2 그대로, instagram: visits 3, visitors {V1,V9}=2, converted 1
        // B: visits 2, visitors 2, leads 3, converted 1 그대로, instagram: leads 3, converted 1
        authed().get("/api/admin/campaigns/{id}/stats/creators", campaign).then().statusCode(200)
            .body("creators[0].visitors", equalTo(4)).body("creators[0].convertedVisitors", equalTo(2))
            .body("creators[0].channels[0].visits", equalTo(3)).body("creators[0].channels[0].visitors", equalTo(2))
            .body("creators[0].channels[0].convertedVisitors", equalTo(1))
            .body("creators[1].leads", equalTo(3)).body("creators[1].convertedVisitors", equalTo(1))
            .body("creators[1].channels[0].leads", equalTo(3)).body("creators[1].channels[0].convertedVisitors", equalTo(1));
    }

    @Test
    void 데이터가_없으면_링크_있는_크리에이터와_직접_자체_링크_행이_0으로_나온다() {
        authed().get("/api/admin/campaigns/{id}/stats/creators", campaign).then().statusCode(200)
            .body("creators.size()", equalTo(4)).body("creators[3].creator", nullValue())
            .body("creators.visits", equalTo(List.of(0, 0, 0, 0)))
            .body("creators.conversionRate", equalTo(List.of(0.0f, 0.0f, 0.0f, 0.0f)));
    }

    @Test
    void 다른_캠페인의_크리에이터_방문이나_신청은_교집합에_들어가지_않는다() {
        seedBase();
        int other = AdminFixtures.createCampaign(token, "B 캠페인");
        int b1 = AdminFixtures.createPublishedFormIn(token, other, "form-b1", AdminFixtures.FORM_HTML);
        createLink(b1, "instagram", sky);
        // V8: B 캠페인에서 S 링크로 방문하고, A 캠페인에서는 방문 없이 S 링크로 신청했다.
        visit(b1, "instagram", sky, V8, "2026-03-10T09:00:00");
        lead(a1, campaign, "instagram", sky, V8, "2026-03-10T09:30:00");

        // A의 S: leads 2 → 3, converted 2 그대로(V8의 방문은 B 캠페인). 바다·유리는 B 캠페인에 링크가 없다.
        authed().get("/api/admin/campaigns/{id}/stats/creators", campaign).then().statusCode(200)
            .body("creators[0].visitors", equalTo(3)).body("creators[0].leads", equalTo(3))
            .body("creators[0].convertedVisitors", equalTo(2));
        // B: 링크 있는 크리에이터는 S뿐 → S + 직접·자체 링크 두 행. S visits 1, visitors 1, leads 0, converted 0
        authed().get("/api/admin/campaigns/{id}/stats/creators", other).then().statusCode(200)
            .body("creators.size()", equalTo(2)).body("creators[0].creator.id", equalTo(sky))
            .body("creators.visits", equalTo(List.of(1, 0))).body("creators.leads", equalTo(List.of(0, 0)))
            .body("creators.convertedVisitors", equalTo(List.of(0, 0)));
    }

    @Test
    void 크리에이터의_캠페인별_지표는_링크가_있는_캠페인만_최신순으로_돌려준다() {
        seedBase();
        int other = AdminFixtures.createCampaign(token, "B 캠페인");
        int b1 = AdminFixtures.createPublishedFormIn(token, other, "form-b1", AdminFixtures.FORM_HTML);
        createLink(b1, "instagram", sky);
        AdminFixtures.createCampaign(token, "C 캠페인"); // S 링크 없음 → 나오지 않는다
        visit(b1, "instagram", sky, V8, "2026-03-10T09:00:00");
        lead(b1, other, "instagram", sky, V8, "2026-03-10T09:30:00");
        lead(b1, other, "direct", null, V1, "2026-03-10T09:40:00"); // S 귀속 아님

        // B: visits 1, visitors {V8}, leads 1, converted 1 → 1.0000 / A: 위 표의 S 행과 같다
        authed().get("/api/admin/creators/{id}/stats", sky).then().statusCode(200)
            .body("creatorId", equalTo(sky)).body("name", equalTo("하늘")).body("status", equalTo("active"))
            .body("from", nullValue()).body("to", nullValue())
            .body("campaigns.campaignId", equalTo(List.of(other, campaign)))
            .body("campaigns.name", equalTo(List.of("B 캠페인", "A 캠페인")))
            .body("campaigns.visits", equalTo(List.of(1, 4)))
            .body("campaigns.visitors", equalTo(List.of(1, 3)))
            .body("campaigns.leads", equalTo(List.of(1, 2)))
            .body("campaigns.convertedVisitors", equalTo(List.of(1, 2)))
            .body("campaigns.conversionRate", equalTo(List.of(1.0f, 0.6667f)));
        // D는 A 캠페인에 링크만 있다 → 0으로 한 행
        authed().get("/api/admin/creators/{id}/stats", yuri).then().statusCode(200)
            .body("campaigns.campaignId", equalTo(List.of(campaign))).body("campaigns[0].visits", equalTo(0));
    }

    @Test
    void 크리에이터의_캠페인별_지표도_기간_안의_방문과_신청만_교집합으로_센다() {
        seedBase();

        // 3/11 하루: S 방문 {#2 V1}=1, 방문자 1, 신청 {L2 V2}=1, 전환 0 (V1의 신청은 3/10, V2의 방문은 3/10)
        authed().queryParams("from", "2026-03-11", "to", "2026-03-11").get("/api/admin/creators/{id}/stats", sky).then()
            .statusCode(200).body("from", equalTo("2026-03-11")).body("to", equalTo("2026-03-11"))
            .body("campaigns.campaignId", equalTo(List.of(campaign)))
            .body("campaigns[0].visits", equalTo(1)).body("campaigns[0].visitors", equalTo(1))
            .body("campaigns[0].leads", equalTo(1)).body("campaigns[0].convertedVisitors", equalTo(0))
            .body("campaigns[0].conversionRate", is(0.0f));
    }

    @Test
    void 링크가_없는_크리에이터의_캠페인별_지표는_빈_목록이다() {
        int none = createCreator("없음");

        authed().get("/api/admin/creators/{id}/stats", none).then().statusCode(200)
            .body("campaigns.size()", equalTo(0));
    }

    @Test
    void 보관된_크리에이터도_지표는_그대로_조회된다() {
        seedBase();
        authed().contentType(ContentType.JSON).body(Map.of("status", "archived"))
            .patch("/api/admin/creators/{id}", sea).then().statusCode(200);

        authed().get("/api/admin/campaigns/{id}/stats/creators", campaign).then().statusCode(200)
            .body("creators[1].creator.status", equalTo("archived")).body("creators[1].leads", equalTo(2));
        authed().get("/api/admin/creators/{id}/stats", sea).then().statusCode(200)
            .body("status", equalTo("archived")).body("campaigns[0].convertedVisitors", equalTo(1));
    }

    @Test
    void 없는_캠페인이나_크리에이터는_404다() {
        authed().get("/api/admin/campaigns/{id}/stats/creators", 99999).then().statusCode(404)
            .body("code", equalTo("not_found"));
        authed().get("/api/admin/creators/{id}/stats", 99999).then().statusCode(404)
            .body("code", equalTo("not_found"));
    }

    @Test
    void from이_to보다_늦으면_422다() {
        for (String path : List.of("/api/admin/campaigns/" + campaign + "/stats/creators",
                "/api/admin/creators/" + sky + "/stats")) {
            authed().queryParams("from", "2026-03-12", "to", "2026-03-11").get(path).then().statusCode(422)
                .body("code", equalTo("validation_failed")).body("errors.field", hasItem("from"));
        }
    }

    @Test
    void 토큰이_없으면_401이다() {
        for (String path : List.of("/api/admin/campaigns/" + campaign + "/stats/creators",
                "/api/admin/creators/" + sky + "/stats")) {
            RestAssured.get(path).then().statusCode(401).contentType(containsString("application/problem+json"));
        }
    }
}
