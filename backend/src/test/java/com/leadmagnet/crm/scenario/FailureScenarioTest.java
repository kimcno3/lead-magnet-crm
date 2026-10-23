package com.leadmagnet.crm.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

import com.leadmagnet.crm.support.IntegrationTest;
import com.leadmagnet.crm.support.MutableClock;
import com.leadmagnet.crm.support.PublicVisitor;
import com.leadmagnet.crm.support.ScenarioBase;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 흐름 한가운데서 일어나는 실패. 기능별 테스트와 겹치는 것은 "이미 만든 데이터가 실패 뒤에도 멀쩡한가"를 본다.
 * 각 테스트는 독립이며 캠페인·폼(published, slug spring-2026)이 준비된 상태에서 시작한다.
 */
@IntegrationTest
class FailureScenarioTest extends ScenarioBase {

    static final String SLUG = "spring-2026";
    static final String PROBLEM = "application/problem+json";

    @Autowired
    MutableClock clock;

    String token;
    int campaign;
    int form;

    @BeforeEach
    void setUp() {
        token = login(EMAIL, PASSWORD).then().statusCode(200).extract().path("accessToken");
        campaign = authedJson(token).body(Map.of("name", "봄 특강")).post("/api/admin/campaigns")
            .then().statusCode(201).extract().path("id");
        form = createForm(token, campaign, uploadTemplate(token, AI_FORM_HTML), SLUG);
        publish();
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    void publish() {
        authedJson(token).body(Map.of("status", "published")).patch("/api/admin/forms/{id}", form).then().statusCode(200);
    }

    void close() {
        authedJson(token).body(Map.of("status", "closed")).patch("/api/admin/forms/{id}", form).then().statusCode(200);
    }

    int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    Response submitOk(PublicVisitor visitor, String phone, String lm) {
        return visitor.submit(SLUG, Map.of("name", "홍길동", "phone", phone), true, lm);
    }

    @Test
    void 토큰_없이는_흐름의_어느_관리자_API도_401이고_데이터는_그대로다() {
        String code = createLink(token, form, "instagram").then().statusCode(201).extract().path("code");

        for (String path : List.of("/api/admin/campaigns/" + campaign + "/stats",
                "/api/admin/campaigns/" + campaign + "/leads", "/api/admin/forms/" + form + "/links",
                "/api/admin/templates", "/api/admin/campaigns")) {
            RestAssured.get(path).then().statusCode(401).contentType(containsString(PROBLEM));
        }
        RestAssured.given().contentType("application/json").body(Map.of("channel", "x"))
            .post("/api/admin/forms/{id}/links", form).then().statusCode(401);
        assertThat(count("distribution_links")).isEqualTo(1);
        newVisitorOpens(code);
    }

    private void newVisitorOpens(String code) {
        newVisitor().visitViaShortLink(code).then().statusCode(200);
    }

    @Test
    void 잘못된_비밀번호는_401이고_refresh_쿠키도_토큰도_주지_않는다() {
        Response response = login(EMAIL, "wrong-password-123");

        response.then().statusCode(401).contentType(containsString(PROBLEM));
        assertThat(response.getCookie(REFRESH_COOKIE)).isNull();
        assertThat(response.asString()).doesNotContain("accessToken");
        login("nobody@example.com", PASSWORD).then().statusCode(401);
    }

    @Test
    void 만료된_access_token은_401이고_refresh로_새_토큰을_받으면_이어서_쓸_수_있다() {
        Response loggedIn = login(EMAIL, PASSWORD);
        String expiring = loggedIn.path("accessToken");
        String refreshCookie = loggedIn.getCookie(REFRESH_COOKIE);
        authed(expiring).get("/api/admin/campaigns/{id}/stats", campaign).then().statusCode(200);

        clock.advance(Duration.ofMinutes(15).plusSeconds(61));

        authed(expiring).get("/api/admin/campaigns/{id}/stats", campaign).then().statusCode(401)
            .contentType(containsString(PROBLEM));
        String renewed = refresh(refreshCookie).then().statusCode(200).extract().path("accessToken");
        authed(renewed).get("/api/admin/campaigns/{id}/stats", campaign).then().statusCode(200)
            .body("visits", equalTo(0));
    }

    @Test
    void 교체된_refresh_token을_다시_쓰면_패밀리가_폐기되어_새_토큰도_못_쓰지만_다른_로그인은_산다() {
        String first = login(EMAIL, PASSWORD).getCookie(REFRESH_COOKIE);
        String otherSession = login(EMAIL, PASSWORD).getCookie(REFRESH_COOKIE);
        String second = refresh(first).then().statusCode(200).extract().cookie(REFRESH_COOKIE);

        refresh(first).then().statusCode(401).body("code", equalTo("refresh_token_reused"));
        refresh(second).then().statusCode(401);
        refresh(otherSession).then().statusCode(200);
    }

    @Test
    void 잘못된_업로드는_거부하고_템플릿이_늘지_않으며_그_뒤_정상_업로드와_폼_생성이_된다() {
        int before = count("html_templates");

        upload(token, "notes.txt", AI_FORM_HTML.getBytes(StandardCharsets.UTF_8), "text/plain")
            .then().statusCode(422).contentType(containsString(PROBLEM)).body("code", equalTo("template_invalid_extension"));
        upload(token, "empty.html", new byte[0], "text/html")
            .then().statusCode(422).body("code", equalTo("template_empty"));
        byte[] big = (AI_FORM_HTML + "<!--" + "a".repeat(600 * 1024) + "-->").getBytes(StandardCharsets.UTF_8);
        upload(token, "big.html", big, "text/html").then().statusCode(413).body("code", equalTo("template_too_large"));

        assertThat(count("html_templates")).isEqualTo(before);
        int template = uploadTemplate(token, AI_FORM_HTML);
        createForm(token, campaign, template, "another-slug");
    }

    @Test
    void 필수_누락과_형식_오류_제출은_422이고_명단에_남지_않으며_고친_뒤_다시_제출하면_받는다() {
        PublicVisitor visitor = newVisitor();
        visitor.visitForm(SLUG).then().statusCode(200);

        visitor.submit(SLUG, Map.of("phone", "010-2222-0001"), true, null).then().statusCode(422)
            .contentType(containsString(PROBLEM)).body("errors.field", hasItem("name"));
        visitor.submit(SLUG, Map.of("name", "홍", "phone", "02-123-4567", "email", "nope"), true, null)
            .then().statusCode(422).body("errors.field", hasItem("phone")).body("errors.field", hasItem("email"));
        visitor.submit(SLUG, Map.of("name", "홍", "phone", "010-2222-0001"), false, null).then().statusCode(422)
            .body("errors.code", hasItem("consent_required"));
        assertThat(count("leads")).isZero();

        submitOk(visitor, "010-2222-0001", null).then().statusCode(201);
        authed(token).get("/api/admin/campaigns/{id}/leads", campaign).then().statusCode(200)
            .body("totalElements", equalTo(1));
        authed(token).get("/api/admin/campaigns/{id}/stats", campaign).then().statusCode(200)
            .body("visits", equalTo(1)).body("leads", equalTo(1)).body("convertedVisitors", equalTo(1));
    }

    @Test
    void 게시_전과_마감_뒤에는_페이지_링크_제출이_막히고_마감_전_신청은_명단에_남는다() {
        // draft 폼: 새 폼은 게시하지 않는다
        createForm(token, campaign, uploadTemplate(token, AI_FORM_HTML), "draft-form");
        PublicVisitor visitor = newVisitor();
        visitor.visitForm("draft-form").then().statusCode(404);
        visitor.request().get("/api/public/forms/draft-form").then().statusCode(404);
        visitor.submit("draft-form", Map.of("name", "홍", "phone", "010-3333-0001"), true, null)
            .then().statusCode(404);

        // published 폼에서 링크와 신청을 하나 만든 뒤 마감
        String code = createLink(token, form, "x").then().statusCode(201).extract().path("code");
        PublicVisitor early = newVisitor();
        early.visitViaShortLink(code).then().statusCode(200);
        submitOk(early, "010-3333-0002", code).then().statusCode(201);
        close();

        PublicVisitor late = newVisitor();
        late.request().get("/l/{code}", code).then().statusCode(410);
        late.visitForm(SLUG).then().statusCode(410);
        late.request().get("/api/public/forms/{slug}", SLUG).then().statusCode(410).body("code", equalTo("gone"));
        submitOk(late, "010-3333-0003", code).then().statusCode(410);

        assertThat(count("leads")).isEqualTo(1);
        assertThat(count("visits")).isEqualTo(1);
        authed(token).get("/api/admin/campaigns/{id}/leads", campaign).then().statusCode(200)
            .body("totalElements", equalTo(1)).body("items[0].channel", equalTo("x"));
    }

    @Test
    void 없는_code와_없는_slug는_404이고_다른_폼의_code는_direct로_귀속된다() {
        PublicVisitor visitor = newVisitor();
        visitor.request().get("/l/{code}", "ZZZZZZZZ").then().statusCode(404);
        visitor.request().get("/f/{slug}", "no-such-form").then().statusCode(404);
        visitor.submit("no-such-form", Map.of("name", "홍", "phone", "010-4444-0001"), true, null)
            .then().statusCode(404);
        assertThat(count("visits")).isZero();

        // 다른 폼의 링크 code는 이 폼에서는 모르는 code라 direct
        int other = createForm(token, campaign, uploadTemplate(token, AI_FORM_HTML), "other-form");
        authedJson(token).body(Map.of("status", "published")).patch("/api/admin/forms/{id}", other)
            .then().statusCode(200);
        String otherCode = createLink(token, other, "youtube").then().statusCode(201).extract().path("code");
        visitor.request().get("/f/{slug}?lm={code}", SLUG, otherCode).then().statusCode(200);
        submitOk(visitor, "010-4444-0002", otherCode).then().statusCode(201);

        assertThat(jdbc.queryForList("SELECT channel FROM visits", String.class)).containsExactly("direct");
        assertThat(jdbc.queryForList("SELECT channel FROM leads", String.class)).containsExactly("direct");
    }

    @Test
    void 같은_연락처로_다시_신청하면_409이고_명단과_지표는_한_건으로_남는다() {
        String code = createLink(token, form, "instagram").then().statusCode(201).extract().path("code");
        PublicVisitor visitor = newVisitor();
        visitor.visitViaShortLink(code).then().statusCode(200);
        submitOk(visitor, "010-5555-0001", code).then().statusCode(201);

        // 표기가 달라도 같은 번호
        submitOk(visitor, "01055550001", code).then().statusCode(409).contentType(containsString(PROBLEM))
            .body("code", equalTo("duplicate_submission"));

        authed(token).get("/api/admin/campaigns/{id}/leads", campaign).then().statusCode(200)
            .body("totalElements", equalTo(1));
        authed(token).get("/api/admin/campaigns/{id}/stats", campaign).then().statusCode(200)
            .body("leads", equalTo(1)).body("convertedVisitors", equalTo(1));
    }

    @Test
    void 허용되지_않은_채널로는_링크를_만들_수_없고_이미_있는_링크는_중복으로_거부된다() {
        createLink(token, form, "instagram").then().statusCode(201);

        createLink(token, form, "direct").then().statusCode(422).contentType(containsString(PROBLEM));
        createLink(token, form, "facebook").then().statusCode(422);
        createLink(token, form, "instagram").then().statusCode(409).body("code", equalTo("link_exists"));

        assertThat(count("distribution_links")).isEqualTo(1);
        authed(token).get("/api/admin/forms/{id}/links", form).then().statusCode(200)
            .body("channel", equalTo(List.of("instagram")));
    }
}
