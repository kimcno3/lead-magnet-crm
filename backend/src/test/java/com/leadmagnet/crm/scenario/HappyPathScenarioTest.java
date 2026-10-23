package com.leadmagnet.crm.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import com.leadmagnet.crm.support.IntegrationTest;
import com.leadmagnet.crm.support.PublicVisitor;
import com.leadmagnet.crm.support.ScenarioBase;
import io.restassured.response.Response;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 운영자가 폼을 올려 링크를 뿌리고, 방문자가 들어와 신청하고, 운영자가 지표와 명단을 보는 전체 흐름.
 *
 * 손계산 (폼 하나, 방문은 /f/{slug} 200마다 한 행):
 * <pre>
 *  #  방문자  경로                          채널       비고
 *  1  A      /l/ig  → /f/spring?lm=ig      instagram
 *  2  A      /l/ig  → /f/spring?lm=ig      instagram  같은 방문자 재방문
 *  3  B      /l/x   → /f/spring?lm=x       x
 *  4  C      /l/yt  → /f/spring?lm=yt      youtube
 *  5  B      /l/th  → /f/spring?lm=th      threads    B가 채널을 바꿔 다시 방문
 *  신청 1: A, lm=ig → instagram
 *  신청 2: B, lm=th → threads (마지막으로 본 링크)
 *
 *  캠페인 전체: visits 5, visitors {A,B,C}=3, leads 2, converted {A,B}=2, rate 2/3=0.6667
 *  instagram:  visits 2, visitors 1(A), leads 1, converted 1, rate 1.0
 *  x:          visits 1, visitors 1(B), leads 0, converted 0, rate 0.0
 *  youtube:    visits 1, visitors 1(C), leads 0, converted 0, rate 0.0
 *  threads:    visits 1, visitors 1(B), leads 1, converted 1, rate 1.0
 *  direct:     모두 0
 * </pre>
 */
@IntegrationTest
class HappyPathScenarioTest extends ScenarioBase {

    @Test
    void 로그인부터_업로드_배포_방문_신청_지표_명단_로그아웃까지_이어진다() {
        // 1. 로그인: access token은 바디, refresh는 쿠키
        Response loggedIn = login(EMAIL, PASSWORD);
        loggedIn.then().statusCode(200).body("accessToken", notNullValue());
        String token = loggedIn.path("accessToken");
        String refreshCookie = loggedIn.getCookie(REFRESH_COOKIE);
        assertThat(refreshCookie).isNotBlank();

        // 2. 템플릿 업로드: 필드 이름이 HTML에서 추출된다
        Response uploaded = upload(token, "spring.html", AI_FORM_HTML.getBytes(java.nio.charset.StandardCharsets.UTF_8),
            "text/html");
        uploaded.then().statusCode(201).body("fields.name", hasItems("name", "phone", "email", "job", "interests", "memo"));
        int template = uploaded.path("id");

        // 3. 캠페인, 4. 폼(draft) — 템플릿 필드가 폼의 fieldConfig 스냅샷이 된다
        int campaign = authedJson(token).body(Map.of("name", "봄 특강")).post("/api/admin/campaigns")
            .then().statusCode(201).extract().path("id");
        int form = createForm(token, campaign, template, "spring-2026");
        authed(token).get("/api/admin/forms/{id}", form).then().statusCode(200).body("status", equalTo("draft"))
            .body("fieldConfig.name", hasItems("name", "phone", "email", "job", "interests", "memo"));

        // 5. 게시 전에는 공개 출처에서 열리지 않는다. 게시하면 열린다.
        newVisitor().request().get("/f/spring-2026").then().statusCode(404);
        authedJson(token).body(Map.of("status", "published")).patch("/api/admin/forms/{id}", form)
            .then().statusCode(200).body("status", equalTo("published"));
        newVisitor().request().get("/api/public/forms/spring-2026").then().statusCode(200)
            .body("title", equalTo("봄 특강 신청"));

        // 6. 4개 채널 링크: 응답 code가 곧 공개 단축 URL의 입력이다
        String ig = linkCode(token, form, "instagram");
        String x = linkCode(token, form, "x");
        String yt = linkCode(token, form, "youtube");
        String th = linkCode(token, form, "threads");
        authed(token).get("/api/admin/forms/{id}/links", form).then().statusCode(200)
            .body("channel", contains("instagram", "x", "youtube", "threads"));

        // 7. 방문: 방문자마다 쿠키가 다르다
        PublicVisitor a = newVisitor();
        PublicVisitor b = newVisitor();
        PublicVisitor c = newVisitor();
        a.visitViaShortLink(ig).then().statusCode(200);
        a.visitViaShortLink(ig).then().statusCode(200);
        b.visitViaShortLink(x).then().statusCode(200);
        c.visitViaShortLink(yt).then().statusCode(200);
        b.visitViaShortLink(th).then().statusCode(200);
        assertThat(List.of(a.visitorId(), b.visitorId(), c.visitorId())).doesNotHaveDuplicates().doesNotContainNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM visits", Integer.class)).isEqualTo(5);

        // 8. 신청 2건: 방문자 쿠키가 신청의 visitor_id가 된다
        a.submit("spring-2026", Map.of("name", "에이", "phone", "010-1111-0001", "email", "A@Example.com",
            "job", "student", "interests", List.of("ai", "marketing")), true, ig).then().statusCode(201)
            .body("status", equalTo("received"));
        b.submit("spring-2026", Map.of("name", "비", "phone", "010-1111-0002"), true, th).then().statusCode(201);

        // 9. 지표는 손계산과 정확히 같다
        authed(token).get("/api/admin/campaigns/{id}/stats", campaign).then().statusCode(200)
            .body("visits", equalTo(5)).body("visitors", equalTo(3)).body("leads", equalTo(2))
            .body("convertedVisitors", equalTo(2)).body("conversionRate", is(0.6667f));
        authed(token).get("/api/admin/campaigns/{id}/stats/channels", campaign).then().statusCode(200)
            .body("channels.channel", equalTo(List.of("instagram", "x", "youtube", "threads", "direct")))
            .body("channels.visits", equalTo(List.of(2, 1, 1, 1, 0)))
            .body("channels.visitors", equalTo(List.of(1, 1, 1, 1, 0)))
            .body("channels.leads", equalTo(List.of(1, 0, 0, 1, 0)))
            .body("channels.convertedVisitors", equalTo(List.of(1, 0, 0, 1, 0)))
            .body("channels.conversionRate", equalTo(List.of(1.0f, 0.0f, 0.0f, 1.0f, 0.0f)));
        authed(token).get("/api/admin/stats/campaigns").then().statusCode(200)
            .body("campaigns[0].campaignId", equalTo(campaign)).body("campaigns[0].visits", equalTo(5))
            .body("campaigns[0].leads", equalTo(2)).body("campaigns[0].convertedVisitors", equalTo(2));

        // 10. 명단: 최신순, 링크 code 채널이 귀속되고 연락처는 가린 값이다. 원문은 contact API로만 본다 (ADR 0015)
        authed(token).get("/api/admin/campaigns/{id}/leads", campaign).then().statusCode(200)
            .body("totalElements", equalTo(2))
            .body("items.name", contains("*", "에*"))
            .body("items.channel", contains("threads", "instagram"))
            .body("items.phone", contains("010-****-0002", "010-****-0001"))
            .body("items[1].email", equalTo("a***@example.com"))
            .body("items[1].formId", equalTo(form))
            .body("items[1].payload.job", equalTo("student"))
            .body("items[1].payload.interests", contains("ai", "marketing"));
        authed(token).queryParam("channel", "instagram").get("/api/admin/campaigns/{id}/leads", campaign)
            .then().statusCode(200).body("items.name", contains("에*"));
        int leadA = authed(token).get("/api/admin/campaigns/{id}/leads", campaign).then().extract().path("items[1].id");
        authed(token).get("/api/admin/leads/{id}/contact", leadA).then().statusCode(200)
            .body("name", equalTo("에이")).body("phone", equalTo("01011110001")).body("email", equalTo("a@example.com"));
        assertThat(jdbc.queryForList("SELECT visitor_id::text FROM leads ORDER BY id", String.class))
            .containsExactly(a.visitorId(), b.visitorId());

        // 11. refresh로 새 access token을 받고, 그 토큰으로 같은 데이터를 본다
        Response refreshed = refresh(refreshCookie);
        refreshed.then().statusCode(200).body("accessToken", notNullValue());
        String newToken = refreshed.path("accessToken");
        String rotated = refreshed.getCookie(REFRESH_COOKIE);
        assertThat(rotated).isNotBlank().isNotEqualTo(refreshCookie);
        authed(newToken).get("/api/admin/campaigns/{id}/stats", campaign).then().statusCode(200)
            .body("leads", equalTo(2));

        // 12. 로그아웃하면 그 refresh token은 더 쓸 수 없다
        io.restassured.RestAssured.given().cookie(REFRESH_COOKIE, rotated).post("/api/admin/auth/logout")
            .then().statusCode(204);
        refresh(rotated).then().statusCode(401);
    }

    private String linkCode(String token, int form, String channel) {
        return createLink(token, form, channel).then().statusCode(201).body("channel", equalTo(channel))
            .extract().path("code");
    }
}
