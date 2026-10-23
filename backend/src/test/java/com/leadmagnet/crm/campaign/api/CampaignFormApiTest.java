package com.leadmagnet.crm.campaign.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import com.leadmagnet.crm.auth.domain.Operator;
import com.leadmagnet.crm.auth.domain.OperatorRepository;
import com.leadmagnet.crm.support.AdminLogin;
import com.leadmagnet.crm.support.DatabaseCleaner;
import com.leadmagnet.crm.support.IntegrationTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@IntegrationTest
class CampaignFormApiTest {

    static final String EMAIL = "owner@example.com";
    static final String PASSWORD = "correct-horse-battery";
    static final String FORM_HTML = """
        <!doctype html><html><body><form>
        <label for="n">이름</label><input id="n" name="name" required>
        <input type="tel" name="phone">
        <select name="plan"><option value="a">A</option><option value="b">B</option></select>
        <button>신청</button></form></body></html>
        """;

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    OperatorRepository operators;

    @Autowired
    PasswordEncoder passwordEncoder;

    long operatorId;
    String token;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        DatabaseCleaner.truncateAll(jdbc);
        operatorId = operators.save(new Operator(EMAIL, passwordEncoder.encode(PASSWORD), Instant.now())).getId();
        token = AdminLogin.accessToken(EMAIL, PASSWORD);
    }

    // ---- 캠페인 -------------------------------------------------------------

    @Test
    void 캠페인을_만들면_201과_active_상태를_주고_생성자가_기록된다() {
        int id = authed().body(Map.of("name", "봄 캠페인", "description", "설명",
                "startsAt", "2026-11-01T00:00:00Z", "endsAt", "2026-12-01T00:00:00Z"))
            .post("/api/admin/campaigns")
            .then().statusCode(201)
            .header("Location", matchesPattern(".*/api/admin/campaigns/\\d+"))
            .body("name", equalTo("봄 캠페인"))
            .body("description", equalTo("설명"))
            .body("status", equalTo("active"))
            .body("startsAt", equalTo("2026-11-01T00:00:00Z"))
            .body("createdAt", notNullValue())
            .body("forms", hasSize(0))
            .extract().path("id");

        assertThat(jdbc.queryForObject("SELECT created_by FROM campaigns WHERE id = ?", Long.class, id))
            .isEqualTo(operatorId);
    }

    @Test
    void 캠페인_입력이_잘못되면_422이다() {
        authed().body(Map.of("name", "")).post("/api/admin/campaigns")
            .then().statusCode(422).body("code", equalTo("validation_failed"))
            .body("errors[0].field", equalTo("name"))
            .body("errors[0].code", equalTo("invalid_value"));
        authed().body(Map.of("name", "a".repeat(101))).post("/api/admin/campaigns").then().statusCode(422);
        authed().body(Map.of("name", "c", "description", "a".repeat(2001))).post("/api/admin/campaigns")
            .then().statusCode(422);
        authed().body(Map.of("name", "c", "startsAt", "2026-12-01T00:00:00Z", "endsAt", "2026-11-01T00:00:00Z"))
            .post("/api/admin/campaigns").then().statusCode(422).body("code", equalTo("validation_failed"))
            .body("errors[0].field", equalTo("startsAt"));
        authed().body("{}").post("/api/admin/campaigns").then().statusCode(422);
    }

    @Test
    void 캠페인_목록은_최신순이고_폼_수를_상태와_무관하게_센다() {
        int first = createCampaign("첫째");
        int second = createCampaign("둘째");
        int template = uploadTemplate();
        createForm(first, template, null);
        int published = createForm(first, template, null);
        authed().body(Map.of("status", "published")).patch("/api/admin/forms/{id}", published).then().statusCode(200);

        authed().get("/api/admin/campaigns").then().statusCode(200)
            .body("name", equalTo(List.of("둘째", "첫째")))
            .body("[0].id", equalTo(second))
            .body("[0].formCount", equalTo(0))
            .body("[1].formCount", equalTo(2))
            .body("[1].status", equalTo("active"))
            .body("[1].createdAt", notNullValue());
    }

    @Test
    void 캠페인_상세는_폼_요약을_포함한다() {
        int campaign = createCampaign("c");
        int template = uploadTemplate();
        int form = createForm(campaign, template, "my-form");

        authed().get("/api/admin/campaigns/{id}", campaign).then().statusCode(200)
            .body("name", equalTo("c"))
            .body("forms", hasSize(1))
            .body("forms[0].id", equalTo(form))
            .body("forms[0].slug", equalTo("my-form"))
            .body("forms[0].title", equalTo("제목"))
            .body("forms[0].status", equalTo("draft"))
            .body("forms[0].publishedAt", nullValue())
            .body("forms[0].closedAt", nullValue());
    }

    @Test
    void 없는_캠페인은_404이다() {
        authed().get("/api/admin/campaigns/999").then().statusCode(404).body("code", equalTo("not_found"));
        authed().body(Map.of("name", "x")).patch("/api/admin/campaigns/999").then().statusCode(404);
    }

    @Test
    void 캠페인을_수정하면_준_값만_바뀌고_updatedAt이_갱신된다() {
        int id = createCampaign("원래");
        authed().body(Map.of("description", "d", "startsAt", "2026-11-01T00:00:00Z"))
            .patch("/api/admin/campaigns/{id}", id).then().statusCode(200);
        Object before = jdbc.queryForObject("SELECT updated_at FROM campaigns WHERE id = ?", Object.class, id);

        authed().body(Map.of("name", "새 이름")).patch("/api/admin/campaigns/{id}", id)
            .then().statusCode(200)
            .body("name", equalTo("새 이름"))
            .body("description", equalTo("d"))
            .body("startsAt", equalTo("2026-11-01T00:00:00Z"))
            .body("status", equalTo("active"));

        assertThat(jdbc.queryForObject("SELECT updated_at FROM campaigns WHERE id = ?", Object.class, id))
            .isNotEqualTo(before);
    }

    @Test
    void 수정으로_기간이_뒤집히거나_이름이_비면_422이다() {
        int id = createCampaign("c");
        authed().body(Map.of("startsAt", "2026-11-01T00:00:00Z", "endsAt", "2026-12-01T00:00:00Z"))
            .patch("/api/admin/campaigns/{id}", id).then().statusCode(200);

        authed().body(Map.of("startsAt", "2027-01-01T00:00:00Z")).patch("/api/admin/campaigns/{id}", id)
            .then().statusCode(422);
        authed().body(Map.of("name", " ")).patch("/api/admin/campaigns/{id}", id).then().statusCode(422);
    }

    @Test
    void 보존_기간은_기본_365일이고_생성과_수정에서_1에서_3650일까지_받는다() {
        int id = createCampaign("c");
        authed().get("/api/admin/campaigns/{id}", id).then().statusCode(200).body("retentionDays", equalTo(365));

        authed().body(Map.of("retentionDays", 3650)).patch("/api/admin/campaigns/{id}", id)
            .then().statusCode(200).body("retentionDays", equalTo(3650));
        authed().body(Map.of("name", "c", "retentionDays", 1)).post("/api/admin/campaigns")
            .then().statusCode(201).body("retentionDays", equalTo(1));
        assertThat(jdbc.queryForObject("SELECT retention_days FROM campaigns WHERE id = ?", Integer.class, id))
            .isEqualTo(3650);
    }

    @Test
    void 보존_기간이_범위를_벗어나면_422이고_값은_그대로다() {
        int id = createCampaign("c");

        for (int days : new int[] {0, 3651, -5}) {
            authed().body(Map.of("name", "c", "retentionDays", days)).post("/api/admin/campaigns")
                .then().statusCode(422).body("code", equalTo("validation_failed"))
                .body("errors[0].field", equalTo("retentionDays"));
            authed().body(Map.of("retentionDays", days)).patch("/api/admin/campaigns/{id}", id)
                .then().statusCode(422).body("errors[0].field", equalTo("retentionDays"));
        }
        authed().get("/api/admin/campaigns/{id}", id).then().body("retentionDays", equalTo(365));
    }

    @Test
    void 캠페인을_보관하면_archived가_되고_다시_보관하면_409이다() {
        int id = createCampaign("c");

        authed().body(Map.of("status", "archived")).patch("/api/admin/campaigns/{id}", id)
            .then().statusCode(200).body("status", equalTo("archived"));
        authed().body(Map.of("status", "archived")).patch("/api/admin/campaigns/{id}", id)
            .then().statusCode(409).body("code", equalTo("campaign_already_archived"));
    }

    @Test
    void 캠페인_상태에_archived_외_값을_주면_422이다() {
        int id = createCampaign("c");

        authed().body(Map.of("status", "active")).patch("/api/admin/campaigns/{id}", id)
            .then().statusCode(422).body("code", equalTo("validation_failed"))
            .body("errors[0].field", equalTo("status"));
        authed().body(Map.of("status", "bogus")).patch("/api/admin/campaigns/{id}", id).then().statusCode(422);
    }

    // ---- 폼 -----------------------------------------------------------------

    @Test
    void 폼을_만들면_draft이고_템플릿_필드를_스냅샷으로_복사한다() {
        int campaign = createCampaign("c");
        int template = uploadTemplate();

        int id = authed().body(Map.of("templateId", template, "title", "제목", "consentText", "동의합니다"))
            .post("/api/admin/campaigns/{id}/forms", campaign)
            .then().statusCode(201)
            .header("Location", matchesPattern(".*/api/admin/forms/\\d+"))
            .body("campaignId", equalTo(campaign))
            .body("templateId", equalTo(template))
            .body("slug", matchesPattern("[a-z0-9]{8}"))
            .body("status", equalTo("draft"))
            .body("consentText", equalTo("동의합니다"))
            .body("fieldConfig", hasSize(3))
            .body("fieldConfig[0].name", equalTo("name"))
            .body("fieldConfig[0].label", equalTo("이름"))
            .body("fieldConfig[0].required", equalTo(true))
            .body("fieldConfig[2].options", equalTo(List.of("a", "b")))
            .body("publishedAt", nullValue())
            .extract().path("id");

        authed().get("/api/admin/forms/{id}", id).then().statusCode(200)
            .body("fieldConfig", hasSize(3))
            .body("createdAt", notNullValue());
        assertThat(jdbc.queryForObject("SELECT field_config::text FROM forms WHERE id = ?", String.class, id))
            .isEqualTo(jdbc.queryForObject("SELECT fields::text FROM html_templates WHERE id = ?", String.class, template));
    }

    @Test
    void 폼_slug를_주면_그대로_쓰고_규칙에_어긋나면_422이다() {
        int campaign = createCampaign("c");
        int template = uploadTemplate();

        createForm(campaign, template, "spring-2026");
        authed().get("/api/admin/forms/1").then().body("slug", equalTo("spring-2026"));

        for (String bad : List.of("A-b", "-ab", "a", "ab", "a_b")) {
            authed().body(Map.of("templateId", template, "title", "t", "consentText", "c", "slug", bad))
                .post("/api/admin/campaigns/{id}/forms", campaign).then().statusCode(422)
                .body("errors[0].field", equalTo("slug"));
        }
    }

    @Test
    void slug가_중복이면_409_slug_taken이다() {
        int campaign = createCampaign("c");
        int template = uploadTemplate();
        createForm(campaign, template, "same-slug");

        authed().body(Map.of("templateId", template, "title", "t", "consentText", "c", "slug", "same-slug"))
            .post("/api/admin/campaigns/{id}/forms", campaign)
            .then().statusCode(409).body("code", equalTo("slug_taken"));
    }

    @Test
    void 폼_입력이_잘못되면_422이다() {
        int campaign = createCampaign("c");
        int template = uploadTemplate();

        authed().body(Map.of("templateId", template, "title", "", "consentText", "c"))
            .post("/api/admin/campaigns/{id}/forms", campaign).then().statusCode(422);
        authed().body(Map.of("templateId", template, "title", "t", "consentText", ""))
            .post("/api/admin/campaigns/{id}/forms", campaign).then().statusCode(422);
        authed().body(Map.of("templateId", template, "title", "t"))
            .post("/api/admin/campaigns/{id}/forms", campaign).then().statusCode(422);
        authed().body(Map.of("title", "t", "consentText", "c"))
            .post("/api/admin/campaigns/{id}/forms", campaign).then().statusCode(422);
    }

    @Test
    void 없는_캠페인이나_템플릿으로_폼을_만들면_404이다() {
        int campaign = createCampaign("c");
        int template = uploadTemplate();

        authed().body(Map.of("templateId", template, "title", "t", "consentText", "c"))
            .post("/api/admin/campaigns/999/forms").then().statusCode(404);
        authed().body(Map.of("templateId", 999, "title", "t", "consentText", "c"))
            .post("/api/admin/campaigns/{id}/forms", campaign).then().statusCode(404).body("code", equalTo("not_found"));
    }

    @Test
    void 보관된_캠페인에는_폼을_만들_수_없다() {
        int campaign = createCampaign("c");
        int template = uploadTemplate();
        authed().body(Map.of("status", "archived")).patch("/api/admin/campaigns/{id}", campaign).then().statusCode(200);

        authed().body(Map.of("templateId", template, "title", "t", "consentText", "c"))
            .post("/api/admin/campaigns/{id}/forms", campaign)
            .then().statusCode(409).body("code", equalTo("campaign_archived"));
    }

    @Test
    void 없는_폼은_404이다() {
        authed().get("/api/admin/forms/999").then().statusCode(404);
        authed().body(Map.of("title", "x")).patch("/api/admin/forms/999").then().statusCode(404);
    }

    @Test
    void 게시하고_마감하면_시각이_기록되고_되돌릴_수_없다() {
        int form = createForm(createCampaign("c"), uploadTemplate(), null);

        authed().body(Map.of("status", "published")).patch("/api/admin/forms/{id}", form)
            .then().statusCode(200).body("status", equalTo("published")).body("publishedAt", notNullValue())
            .body("closedAt", nullValue());
        authed().body(Map.of("status", "published")).patch("/api/admin/forms/{id}", form)
            .then().statusCode(409).body("code", equalTo("invalid_form_transition"));
        authed().body(Map.of("status", "closed")).patch("/api/admin/forms/{id}", form)
            .then().statusCode(200).body("status", equalTo("closed")).body("closedAt", notNullValue());
        authed().body(Map.of("status", "published")).patch("/api/admin/forms/{id}", form)
            .then().statusCode(409).body("code", equalTo("invalid_form_transition"));
        authed().body(Map.of("status", "closed")).patch("/api/admin/forms/{id}", form)
            .then().statusCode(409).body("code", equalTo("invalid_form_transition"));
    }

    @Test
    void draft를_바로_마감하려_하면_409이다() {
        int form = createForm(createCampaign("c"), uploadTemplate(), null);

        authed().body(Map.of("status", "closed")).patch("/api/admin/forms/{id}", form)
            .then().statusCode(409).body("code", equalTo("invalid_form_transition"));
    }

    @Test
    void 폼_상태에_허용하지_않는_값을_주면_422이다() {
        int form = createForm(createCampaign("c"), uploadTemplate(), null);

        authed().body(Map.of("status", "draft")).patch("/api/admin/forms/{id}", form).then().statusCode(422);
        authed().body(Map.of("status", "bogus")).patch("/api/admin/forms/{id}", form).then().statusCode(422);
    }

    @Test
    void draft에서는_제목_동의문구_필드의_label과_required를_고칠_수_있다() {
        int form = createForm(createCampaign("c"), uploadTemplate(), null);

        authed().body(Map.of("title", "새 제목", "consentText", "새 동문",
                "fieldConfig", List.of(
                    Map.of("name", "name", "type", "text", "required", false, "label", "성함"),
                    Map.of("name", "phone", "type", "tel", "required", true, "label", "연락처"),
                    Map.of("name", "plan", "type", "select", "required", false, "label", "플랜"))))
            .patch("/api/admin/forms/{id}", form)
            .then().statusCode(200)
            .body("title", equalTo("새 제목"))
            .body("consentText", equalTo("새 동문"))
            .body("fieldConfig[0].label", equalTo("성함"))
            .body("fieldConfig[0].required", equalTo(false))
            .body("fieldConfig[1].required", equalTo(true))
            .body("fieldConfig[2].options", equalTo(List.of("a", "b")));
    }

    @Test
    void 필드를_추가_삭제_이름변경하면_422이다() {
        int form = createForm(createCampaign("c"), uploadTemplate(), null);

        authed().body(Map.of("fieldConfig", List.of(
                Map.of("name", "name", "type", "text", "required", true, "label", "이름"))))
            .patch("/api/admin/forms/{id}", form).then().statusCode(422).body("code", equalTo("validation_failed"))
            .body("errors[0].field", equalTo("fieldConfig"));
        authed().body(Map.of("fieldConfig", List.of(
                Map.of("name", "name", "type", "text", "required", true, "label", "이름"),
                Map.of("name", "phone", "type", "tel", "required", false, "label", "전화"),
                Map.of("name", "plan", "type", "select", "required", false, "label", "플랜"),
                Map.of("name", "extra", "type", "text", "required", false, "label", "추가"))))
            .patch("/api/admin/forms/{id}", form).then().statusCode(422);
        authed().body(Map.of("fieldConfig", List.of(
                Map.of("name", "name", "type", "text", "required", true, "label", "이름"),
                Map.of("name", "mobile", "type", "tel", "required", false, "label", "전화"),
                Map.of("name", "plan", "type", "select", "required", false, "label", "플랜"))))
            .patch("/api/admin/forms/{id}", form).then().statusCode(422);
    }

    @Test
    void 필드에_required를_생략하면_현재_값을_유지한다() {
        int form = createForm(createCampaign("c"), uploadTemplate(), null);

        authed().body(Map.of("fieldConfig", List.of(
                Map.of("name", "name", "label", "성함"),
                Map.of("name", "phone"),
                Map.of("name", "plan"))))
            .patch("/api/admin/forms/{id}", form)
            .then().statusCode(200)
            .body("fieldConfig[0].label", equalTo("성함"))
            .body("fieldConfig[0].required", equalTo(true))
            .body("fieldConfig[1].required", equalTo(false));
    }

    @Test
    void 제목이_잘못되고_slug가_중복이면_409가_아니라_422이다() {
        int campaign = createCampaign("c");
        int template = uploadTemplate();
        createForm(campaign, template, "same-slug");

        authed().body(Map.of("templateId", template, "title", " ", "consentText", "c", "slug", "same-slug"))
            .post("/api/admin/campaigns/{id}/forms", campaign)
            .then().statusCode(422).body("errors[0].field", equalTo("title"));
    }

    @Test
    void 제목이나_동의문구가_비면_422이다() {
        int form = createForm(createCampaign("c"), uploadTemplate(), null);

        authed().body(Map.of("title", " ")).patch("/api/admin/forms/{id}", form).then().statusCode(422)
            .body("errors[0].field", equalTo("title"));
        authed().body(Map.of("consentText", "")).patch("/api/admin/forms/{id}", form).then().statusCode(422);
    }

    @Test
    void 마감_후_수정은_409_form_not_editable이다() {
        int form = createForm(createCampaign("c"), uploadTemplate(), null);
        authed().body(Map.of("status", "published")).patch("/api/admin/forms/{id}", form).then().statusCode(200);
        authed().body(Map.of("title", "게시 후")).patch("/api/admin/forms/{id}", form)
            .then().statusCode(409).body("code", equalTo("form_not_editable"));
        authed().body(Map.of("status", "closed")).patch("/api/admin/forms/{id}", form).then().statusCode(200);

        authed().body(Map.of("title", "마감 후")).patch("/api/admin/forms/{id}", form)
            .then().statusCode(409).body("code", equalTo("form_not_editable"));
        authed().get("/api/admin/forms/{id}", form).then().body("title", equalTo("제목"));
    }

    @Test
    void 수정과_게시를_한_번에_보낼_수_있다() {
        int form = createForm(createCampaign("c"), uploadTemplate(), null);

        authed().body(Map.of("title", "최종", "status", "published")).patch("/api/admin/forms/{id}", form)
            .then().statusCode(200).body("title", equalTo("최종")).body("status", equalTo("published"));
    }

    @Test
    void 인증_없이는_모든_엔드포인트가_401이다() {
        given().get("/api/admin/campaigns").then().statusCode(401);
        given().contentType(ContentType.JSON).body("{}").post("/api/admin/campaigns").then().statusCode(401);
        given().get("/api/admin/campaigns/1").then().statusCode(401);
        given().contentType(ContentType.JSON).body("{}").patch("/api/admin/campaigns/1").then().statusCode(401);
        given().contentType(ContentType.JSON).body("{}").post("/api/admin/campaigns/1/forms").then().statusCode(401);
        given().get("/api/admin/forms/1").then().statusCode(401);
        given().contentType(ContentType.JSON).body("{}").patch("/api/admin/forms/1")
            .then().statusCode(401).body("code", equalTo("unauthorized"));
    }

    // ---- helpers ------------------------------------------------------------

    private RequestSpecification authed() {
        return given().auth().oauth2(token).contentType(ContentType.JSON);
    }

    private int createCampaign(String name) {
        return authed().body(Map.of("name", name)).post("/api/admin/campaigns")
            .then().statusCode(201).extract().path("id");
    }

    private int uploadTemplate() {
        return given().auth().oauth2(token)
            .multiPart("file", "t.html", FORM_HTML.getBytes(StandardCharsets.UTF_8), "text/html")
            .post("/api/admin/templates").then().statusCode(201).extract().path("id");
    }

    private int createForm(int campaignId, int templateId, String slug) {
        Map<String, Object> body = new java.util.HashMap<>(
            Map.of("templateId", templateId, "title", "제목", "consentText", "동의합니다"));
        if (slug != null) {
            body.put("slug", slug);
        }
        ValidatableResponse response = authed().body(body)
            .post("/api/admin/campaigns/{id}/forms", campaignId).then().statusCode(201);
        return response.extract().path("id");
    }
}
