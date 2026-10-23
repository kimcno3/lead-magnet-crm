package com.leadmagnet.crm.lead.api;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

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
import io.restassured.specification.RequestSpecification;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 캠페인별 명단. 최신순, 폼·채널 필터, 페이징. */
@IntegrationTest
class LeadListApiTest {

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

    String token;
    int campaign;
    int formA;
    int formB;
    String instagramCode;

    @BeforeEach
    void setUp() {
        RestAssured.port = adminPort;
        DatabaseCleaner.truncateAll(jdbc);
        operators.save(new Operator("owner@example.com", passwordEncoder.encode("correct-horse-battery")));
        token = AdminLogin.accessToken("owner@example.com", "correct-horse-battery");
        campaign = AdminFixtures.createCampaign(token, "봄 캠페인");
        formA = AdminFixtures.createPublishedFormIn(token, campaign, "form-a", AdminFixtures.FORM_HTML);
        formB = AdminFixtures.createPublishedFormIn(token, campaign, "form-b", AdminFixtures.FORM_HTML);
        instagramCode = AdminFixtures.createLink(token, formA, "instagram");
    }

    RequestSpecification authed() {
        return RestAssured.given().auth().oauth2(token);
    }

    void submit(String slug, String name, String phone, String lm) {
        Map<String, Object> body = lm == null
            ? Map.of("fields", Map.of("name", name, "phone", phone), "consent", true)
            : Map.of("fields", Map.of("name", name, "phone", phone), "consent", true, "lm", lm);
        PublicOrigin.given(publicPort).contentType(ContentType.JSON).body(body)
            .post("/api/public/forms/{slug}/submissions", slug).then().statusCode(201);
    }

    @Test
    void 명단은_최신순이고_항목_모양이_계약과_같다() {
        submit("form-a", "첫째", "010-1111-0001", instagramCode);
        submit("form-b", "둘째", "010-1111-0002", null);
        submit("form-a", "셋째", "010-1111-0003", null);

        authed().get("/api/admin/campaigns/{id}/leads", campaign).then().statusCode(200)
            .body("items.name", contains("셋째", "둘째", "첫째"))
            .body("page", equalTo(0)).body("size", equalTo(20)).body("totalElements", equalTo(3))
            .body("totalPages", equalTo(1))
            .body("items[2].id", notNullValue()).body("items[2].formId", equalTo(formA))
            .body("items[2].formTitle", equalTo("봄 특강 신청")).body("items[2].channel", equalTo("instagram"))
            .body("items[2].phone", equalTo("01011110001")).body("items[2].email", equalTo(null))
            .body("items[2].payload", equalTo(Map.of())).body("items[2].createdAt", notNullValue())
            .body("items[0].channel", equalTo("direct"));
    }

    @Test
    void formId_필터로_한_폼의_신청만_본다() {
        submit("form-a", "가", "010-1111-0001", null);
        submit("form-b", "나", "010-1111-0002", null);

        authed().queryParam("formId", formB).get("/api/admin/campaigns/{id}/leads", campaign).then().statusCode(200)
            .body("items.name", contains("나")).body("totalElements", equalTo(1));
    }

    @Test
    void channel_필터로_채널별_신청만_본다() {
        submit("form-a", "가", "010-1111-0001", instagramCode);
        submit("form-a", "나", "010-1111-0002", null);

        authed().queryParam("channel", "instagram").get("/api/admin/campaigns/{id}/leads", campaign).then()
            .statusCode(200).body("items.name", contains("가"));
        authed().queryParam("channel", "direct").get("/api/admin/campaigns/{id}/leads", campaign).then()
            .statusCode(200).body("items.name", contains("나"));
        authed().queryParam("channel", "youtube").get("/api/admin/campaigns/{id}/leads", campaign).then()
            .statusCode(200).body("items", hasSize(0)).body("totalElements", equalTo(0)).body("totalPages", equalTo(0));
    }

    @Test
    void 페이지와_크기로_나눠_본다() {
        for (int i = 1; i <= 5; i++) {
            submit("form-a", "신청" + i, "010-2222-000" + i, null);
        }

        authed().queryParam("size", 2).get("/api/admin/campaigns/{id}/leads", campaign).then().statusCode(200)
            .body("items.name", contains("신청5", "신청4")).body("size", equalTo(2)).body("totalElements", equalTo(5))
            .body("totalPages", equalTo(3));
        authed().queryParams(Map.of("size", 2, "page", 2)).get("/api/admin/campaigns/{id}/leads", campaign).then()
            .statusCode(200).body("items.name", contains("신청1")).body("page", equalTo(2));
        authed().queryParams(Map.of("size", 2, "page", 9)).get("/api/admin/campaigns/{id}/leads", campaign).then()
            .statusCode(200).body("items", hasSize(0)).body("totalElements", equalTo(5));
    }

    @Test
    void 다른_캠페인의_신청은_섞이지_않는다() {
        int otherCampaign = AdminFixtures.createCampaign(token, "다른 캠페인");
        AdminFixtures.createPublishedFormIn(token, otherCampaign, "form-c", AdminFixtures.FORM_HTML);
        submit("form-a", "가", "010-1111-0001", null);
        submit("form-c", "나", "010-1111-0002", null);

        authed().get("/api/admin/campaigns/{id}/leads", otherCampaign).then().statusCode(200)
            .body("items.name", contains("나"));
    }

    @Test
    void 이메일과_payload도_함께_돌려준다() {
        String html = """
            <form><input name="phone" type="tel"><input name="email" type="email">
            <select name="job"><option value="student">s</option></select></form>
            """;
        AdminFixtures.createPublishedFormIn(token, campaign, "form-d", html);
        PublicOrigin.given(publicPort).contentType(ContentType.JSON)
            .body(Map.of("fields", Map.of("email", "A@B.co", "job", "student"), "consent", true))
            .post("/api/public/forms/form-d/submissions").then().statusCode(201);

        authed().queryParam("formId", jdbc.queryForObject("SELECT id FROM forms WHERE slug='form-d'", Integer.class))
            .get("/api/admin/campaigns/{id}/leads", campaign).then().statusCode(200)
            .body("items[0].email", equalTo("a@b.co")).body("items[0].phone", equalTo(null))
            .body("items[0].payload.job", equalTo("student"));
    }

    @Test
    void 없는_캠페인은_404다() {
        authed().get("/api/admin/campaigns/{id}/leads", 99999).then().statusCode(404).body("code", equalTo("not_found"));
    }

    @Test
    void 토큰이_없으면_401이다() {
        RestAssured.get("/api/admin/campaigns/{id}/leads", campaign).then().statusCode(401)
            .contentType(containsString("application/problem+json"));
    }

    @Test
    void 잘못된_channel은_422다() {
        authed().queryParam("channel", "facebook").get("/api/admin/campaigns/{id}/leads", campaign).then()
            .statusCode(422).body("errors.field", hasItem("channel"));
        authed().queryParam("channel", "INSTAGRAM").get("/api/admin/campaigns/{id}/leads", campaign).then()
            .statusCode(422);
    }

    @Test
    void 범위를_벗어난_page_size는_422다() {
        authed().queryParam("size", 101).get("/api/admin/campaigns/{id}/leads", campaign).then().statusCode(422)
            .body("errors.field", hasItem("size"));
        authed().queryParam("size", 0).get("/api/admin/campaigns/{id}/leads", campaign).then().statusCode(422);
        authed().queryParam("page", -1).get("/api/admin/campaigns/{id}/leads", campaign).then().statusCode(422)
            .body("errors.field", hasItem("page"));
        authed().queryParam("size", 100).get("/api/admin/campaigns/{id}/leads", campaign).then().statusCode(200)
            .body("size", equalTo(100));
    }

    @Test
    void 명단은_public_포트에서_열리지_않는다() {
        PublicOrigin.given(publicPort).auth().oauth2(token).get("/api/admin/campaigns/{id}/leads", campaign)
            .then().statusCode(404);
    }
}
