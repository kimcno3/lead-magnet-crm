package com.leadmagnet.crm.lead.api;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 캠페인별 명단. 최신순, 폼·채널 필터, 페이징. 이름·전화번호·이메일은 가린 값만 돌려준다 (ADR 0015). */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
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
        operators.save(new Operator("owner@example.com", passwordEncoder.encode("correct-horse-battery"), Instant.now()));
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
        submit("form-a", "김첫째", "010-1111-0001", instagramCode);
        submit("form-b", "이둘째", "010-1111-0002", null);
        submit("form-a", "박셋째", "010-1111-0003", null);

        authed().get("/api/admin/campaigns/{id}/leads", campaign).then().statusCode(200)
            .body("items.name", contains("박*째", "이*째", "김*째"))
            .body("page", equalTo(0)).body("size", equalTo(20)).body("totalElements", equalTo(3))
            .body("totalPages", equalTo(1))
            .body("items[2].id", notNullValue()).body("items[2].formId", equalTo(formA))
            .body("items[2].formTitle", equalTo("봄 특강 신청")).body("items[2].channel", equalTo("instagram"))
            .body("items[2].phone", equalTo("010-****-0001")).body("items[2].email", equalTo(null))
            .body("items[2].payload", equalTo(Map.of())).body("items[2].createdAt", notNullValue())
            .body("items[2].contactUnavailable", equalTo(false))
            .body("items[0].channel", equalTo("direct"));
    }

    @Test
    void formId_필터로_한_폼의_신청만_본다() {
        submit("form-a", "가나다", "010-1111-0001", null);
        submit("form-b", "라마바", "010-1111-0002", null);

        authed().queryParam("formId", formB).get("/api/admin/campaigns/{id}/leads", campaign).then().statusCode(200)
            .body("items.name", contains("라*바")).body("totalElements", equalTo(1));
    }

    @Test
    void channel_필터로_채널별_신청만_본다() {
        submit("form-a", "가나다", "010-1111-0001", instagramCode);
        submit("form-a", "라마바", "010-1111-0002", null);

        authed().queryParam("channel", "instagram").get("/api/admin/campaigns/{id}/leads", campaign).then()
            .statusCode(200).body("items.name", contains("가*다"));
        authed().queryParam("channel", "direct").get("/api/admin/campaigns/{id}/leads", campaign).then()
            .statusCode(200).body("items.name", contains("라*바"));
        authed().queryParam("channel", "youtube").get("/api/admin/campaigns/{id}/leads", campaign).then()
            .statusCode(200).body("items", hasSize(0)).body("totalElements", equalTo(0)).body("totalPages", equalTo(0));
    }

    @Test
    void 페이지와_크기로_나눠_본다() {
        for (int i = 1; i <= 5; i++) {
            submit("form-a", "신청자" + i, "010-2222-000" + i, null);
        }

        authed().queryParam("size", 2).get("/api/admin/campaigns/{id}/leads", campaign).then().statusCode(200)
            .body("items.name", contains("신*5", "신*4")).body("size", equalTo(2)).body("totalElements", equalTo(5))
            .body("totalPages", equalTo(3));
        authed().queryParams(Map.of("size", 2, "page", 2)).get("/api/admin/campaigns/{id}/leads", campaign).then()
            .statusCode(200).body("items.name", contains("신*1")).body("page", equalTo(2));
        authed().queryParams(Map.of("size", 2, "page", 9)).get("/api/admin/campaigns/{id}/leads", campaign).then()
            .statusCode(200).body("items", hasSize(0)).body("totalElements", equalTo(5));
    }

    @Test
    void 다른_캠페인의_신청은_섞이지_않는다() {
        int otherCampaign = AdminFixtures.createCampaign(token, "다른 캠페인");
        AdminFixtures.createPublishedFormIn(token, otherCampaign, "form-c", AdminFixtures.FORM_HTML);
        submit("form-a", "가나다", "010-1111-0001", null);
        submit("form-c", "라마바", "010-1111-0002", null);

        authed().get("/api/admin/campaigns/{id}/leads", otherCampaign).then().statusCode(200)
            .body("items.name", contains("라*바"));
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
            .body("items[0].email", equalTo("a***@b.co")).body("items[0].phone", equalTo(null))
            .body("items[0].name", equalTo(null))
            .body("items[0].payload.job", equalTo("student"));
    }

    @Test
    void 명단은_암호문을_복호화해_가린다() {
        submit("form-a", "홍길동", "010-1234-5678", null);

        authed().get("/api/admin/campaigns/{id}/leads", campaign).then().statusCode(200)
            .body("items[0].name", equalTo("홍*동")).body("items[0].phone", equalTo("010-****-5678"));
    }

    @Test
    void 복호화할_수_없는_행이_있어도_명단은_열리고_그_행만_contactUnavailable이다(CapturedOutput output) {
        submit("form-a", "홍길동", "010-1234-5678", null);
        submit("form-a", "김영희", "010-7777-6666", null);
        long broken = jdbc.queryForObject("SELECT min(id) FROM leads", Long.class);
        // 다른 컬럼의 암호문을 옮겨 넣으면(AAD 불일치) 복호화가 실패한다.
        jdbc.update("UPDATE leads SET name_enc = phone_enc WHERE id = ?", broken);

        authed().get("/api/admin/campaigns/{id}/leads", campaign).then().statusCode(200)
            .body("items[1].id", equalTo((int) broken)).body("items[1].contactUnavailable", equalTo(true))
            .body("items[1].name", equalTo(null)).body("items[1].phone", equalTo(null))
            .body("items[1].email", equalTo(null))
            .body("items[0].contactUnavailable", equalTo(false)).body("items[0].name", equalTo("김*희"));
        assertThat(output.getAll()).contains("leadId=" + broken).doesNotContain("홍길동")
            .doesNotContain("01012345678");
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
            .statusCode(422).body("errors.field", hasItem("channel"))
            .body("errors[0].code", equalTo("invalid_value"))
            .body("errors[0].message", equalTo("channel은 instagram, x, youtube, threads, direct 중 하나여야 합니다"));
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
