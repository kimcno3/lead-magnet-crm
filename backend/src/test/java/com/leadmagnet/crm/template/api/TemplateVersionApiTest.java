package com.leadmagnet.crm.template.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import com.leadmagnet.crm.auth.domain.Operator;
import com.leadmagnet.crm.auth.domain.OperatorRepository;
import com.leadmagnet.crm.common.web.PublicPort;
import com.leadmagnet.crm.support.AdminFixtures;
import com.leadmagnet.crm.support.AdminLogin;
import com.leadmagnet.crm.support.DatabaseCleaner;
import com.leadmagnet.crm.support.IntegrationTest;
import com.leadmagnet.crm.support.PublicOrigin;
import com.leadmagnet.crm.support.FormTokens;
import com.leadmagnet.crm.support.MutableClock;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 새 버전 업로드(PUT)와 게시 시점 스냅샷 (ADR 0018). */
@IntegrationTest
public class TemplateVersionApiTest {

    static final String V1 = """
        <!doctype html><html><body><form>
        <label>이름 <input name="name" required></label>
        <label>전화 <input type="tel" name="phone" required></label>
        <button>신청</button></form></body></html>
        """;
    /** V1에서 phone이 선택으로 바뀌고 memo가 새로 생겼다. name의 라벨도 바뀌었다. */
    static final String V2 = """
        <!doctype html><html><body><form>
        <label>성함 <input name="name" required></label>
        <label>전화 <input type="tel" name="phone"></label>
        <label>메모 <input name="memo"></label>
        <button>신청 v2</button></form></body></html>
        """;
    static final String NO_CONTACT = "<!doctype html><html><body><form><input name=\"name\"></form></body></html>";

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

    String token;

    @BeforeEach
    void setUp() {
        RestAssured.port = adminPort;
        DatabaseCleaner.truncateAll(jdbc);
        operators.save(new Operator("owner@example.com", passwordEncoder.encode("correct-horse-battery"), Instant.now()));
        token = AdminLogin.accessToken("owner@example.com", "correct-horse-battery");
    }

    @Test
    void 게시한_뒤_템플릿을_새_버전으로_바꿔도_공개_html과_필드와_제출_검증은_그대로다() {
        int form = AdminFixtures.createPublishedForm(token, "spring-2026", V1);
        int template = templateIdOf(form);

        put(template, "v2.html", V2).statusCode(200).body("version", equalTo(2)).body("fields", hasSize(3))
            .body("affectedDraftForms", equalTo(0));

        PublicOrigin.given(publicPort).get("/api/public/forms/spring-2026").then().statusCode(200)
            .body("html", equalTo(V1)).body("fields", hasSize(2)).body("fields.name", org.hamcrest.Matchers.contains("name", "phone"));
        // 옛 기준 그대로: phone은 여전히 필수이고 memo는 모르는 필드다.
        submit("spring-2026", Map.of("name", "홍길동")).then().statusCode(422)
            .body("errors.find { it.field == 'phone' }.code", equalTo("required"));
        submit("spring-2026", Map.of("name", "홍길동", "phone", "010-1234-5678", "memo", "x")).then().statusCode(422)
            .body("errors.find { it.field == 'memo' }.code", equalTo("unknown_field"));
        submit("spring-2026", Map.of("name", "홍길동", "phone", "010-1234-5678")).then().statusCode(201);
    }

    @Test
    void 마감한_폼도_새_버전의_영향을_받지_않는다() {
        int form = AdminFixtures.createPublishedForm(token, "closed-one", V1);
        AdminFixtures.changeStatus(token, form, "closed");
        String before = formJson(form);

        put(templateIdOf(form), "v2.html", V2).statusCode(200).body("affectedDraftForms", equalTo(0));

        // 템플릿의 현재 sha256(templateSha256)만 달라지고 폼 자신의 값은 그대로다.
        assertThat(formJson(form).replaceAll("\"templateSha256\":\"[0-9a-f]+\"", ""))
            .isEqualTo(before.replaceAll("\"templateSha256\":\"[0-9a-f]+\"", ""));
        assertThat(publishedHtml(form)).isEqualTo(V1);
    }

    @Test
    void draft_폼은_새_필드로_다시_스냅샷하고_운영자가_바꾼_label과_required는_이름이_같으면_유지한다() {
        int form = AdminFixtures.createDraftForm(token, "draft-one", V1);
        int template = templateIdOf(form);
        // 운영자 수정: name의 label은 직접 바꿨고, phone의 required는 건드리지 않았다.
        given().auth().oauth2(token).contentType(ContentType.JSON)
            .body(Map.of("fieldConfig", List.of(Map.of("name", "name", "label", "신청자 이름"), Map.of("name", "phone"))))
            .patch("/api/admin/forms/{id}", form).then().statusCode(200);

        put(template, "v2.html", V2).statusCode(200).body("affectedDraftForms", equalTo(1));

        given().auth().oauth2(token).get("/api/admin/forms/{id}", form).then().statusCode(200)
            .body("fieldConfig.name", org.hamcrest.Matchers.contains("name", "phone", "memo"))
            .body("fieldConfig[0].label", equalTo("신청자 이름"))   // 운영자 override 유지
            .body("fieldConfig[1].required", equalTo(false))         // override 없음 → 새 템플릿 값
            .body("fieldConfig[2].label", equalTo("메모"))
            .body("publishedTemplateSha256", nullValue());
    }

    @Test
    void 운영자가_required를_바꾼_필드는_새_버전에서도_그_값을_유지한다() {
        int form = AdminFixtures.createDraftForm(token, "draft-req", V1);
        given().auth().oauth2(token).contentType(ContentType.JSON)
            .body(Map.of("fieldConfig", List.of(Map.of("name", "name"), Map.of("name", "phone", "required", false))))
            .patch("/api/admin/forms/{id}", form).then().statusCode(200);

        put(templateIdOf(form), "v2.html", V2.replace("name=\"phone\"", "name=\"phone\" required")).statusCode(200);

        given().auth().oauth2(token).get("/api/admin/forms/{id}", form).then()
            .body("fieldConfig[1].required", equalTo(false));
    }

    @Test
    void 새_버전_뒤에_게시하면_새_내용이_스냅샷된다() {
        int form = AdminFixtures.createDraftForm(token, "draft-pub", V1);
        put(templateIdOf(form), "v2.html", V2).statusCode(200);

        AdminFixtures.changeStatus(token, form, "published");

        PublicOrigin.given(publicPort).get("/api/public/forms/draft-pub").then().statusCode(200)
            .body("html", equalTo(V2)).body("fields", hasSize(3));
        given().auth().oauth2(token).get("/api/admin/forms/{id}", form).then()
            .body("publishedTemplateSha256", equalTo(sha256(V2)));
        submit("draft-pub", Map.of("name", "홍", "phone", "010-1234-5678", "memo", "x")).then().statusCode(201);
    }

    @Test
    void 게시하면_그_시점_템플릿_sha256이_폼에_남고_새_버전_뒤_템플릿_상세는_다른_sha256이다() {
        int form = AdminFixtures.createPublishedForm(token, "sha-one", V1);
        int template = templateIdOf(form);
        given().auth().oauth2(token).get("/api/admin/forms/{id}", form).then()
            .body("publishedTemplateSha256", equalTo(sha256(V1)));

        put(template, "v2.html", V2).statusCode(200);

        given().auth().oauth2(token).get("/api/admin/forms/{id}", form).then()
            .body("publishedTemplateSha256", equalTo(sha256(V1)));
        given().auth().oauth2(token).get("/api/admin/templates/{id}", template).then()
            .body("sha256", equalTo(sha256(V2))).body("version", equalTo(2)).body("updatedAt", notNullValue())
            .body("content", equalTo(V2));
    }

    @Test
    void 새_버전이_갱신하는_값은_content_sha256_fields_size_inspection_version_updatedAt이다() {
        int form = AdminFixtures.createDraftForm(token, "all-fields", V1);
        int template = templateIdOf(form);
        String createdAt = given().auth().oauth2(token).get("/api/admin/templates/{id}", template).path("createdAt");

        put(template, "v2.html", V2.replace("</form>", "<img src=\"https://cdn.example.com/a.png\"></form>"))
            .statusCode(200).body("sha256", notNullValue()).body("fields", hasSize(3))
            .body("inspection.code", org.hamcrest.Matchers.hasItem("external_image")).body("version", equalTo(2))
            .body("name", equalTo("t")).body("affectedDraftForms", equalTo(1));

        given().auth().oauth2(token).get("/api/admin/templates").then().body("[0].version", equalTo(2))
            .body("[0].updatedAt", notNullValue()).body("[0].fieldCount", equalTo(3));
        given().auth().oauth2(token).get("/api/admin/templates/{id}", template).then()
            .body("createdAt", equalTo(createdAt)).body("size", org.hamcrest.Matchers.greaterThan(V1.length()));
    }

    @Test
    void 계약을_어긴_새_버전은_422이고_템플릿과_draft_폼은_그대로다() {
        int form = AdminFixtures.createDraftForm(token, "bad-put", V1);
        int template = templateIdOf(form);
        String before = given().auth().oauth2(token).get("/api/admin/templates/{id}", template).asString();
        String formBefore = formJson(form);

        put(template, "bad.html", NO_CONTACT).statusCode(422).body("code", equalTo("template_contact_field_missing"));
        put(template, "bad.txt", V2).statusCode(422).body("code", equalTo("template_invalid_extension"));
        put(template, "empty.html", " ").statusCode(422).body("code", equalTo("template_empty"));
        put(template, "big.html", V2 + " ".repeat(600 * 1024)).statusCode(413);

        assertThat(given().auth().oauth2(token).get("/api/admin/templates/{id}", template).asString()).isEqualTo(before);
        assertThat(formJson(form)).isEqualTo(formBefore);
    }

    @Test
    void UTF8이_아닌_파일은_422다() {
        int template = templateIdOf(AdminFixtures.createDraftForm(token, "latin", V1));

        given().auth().oauth2(token).multiPart("file", "x.html", new byte[] {(byte) 0xFF, (byte) 0xFE, 0x41}, "text/html")
            .put("/api/admin/templates/{id}", template).then().statusCode(422).body("code", equalTo("template_not_utf8"));
    }

    @Test
    void 없는_템플릿의_새_버전은_404다() {
        put(9999, "v2.html", V2).statusCode(404).body("code", equalTo("not_found"));
    }

    @Test
    void 없는_템플릿에_잘못된_파일을_올려도_404가_먼저다() {
        put(9999, "bad.txt", NO_CONTACT).statusCode(404).body("code", equalTo("not_found"));
    }

    @Test
    void 목록은_수정일_최신순이다() {
        int first = templateIdOf(AdminFixtures.createDraftForm(token, "list-a", V1));
        int second = templateIdOf(AdminFixtures.createDraftForm(token, "list-b", V1));
        given().auth().oauth2(token).get("/api/admin/templates").then().body("id", org.hamcrest.Matchers.contains(second, first));

        put(first, "v2.html", V2).statusCode(200);

        given().auth().oauth2(token).get("/api/admin/templates").then().body("id", org.hamcrest.Matchers.contains(first, second));
    }

    @Test
    void 토큰_없이는_새_버전_업로드가_401이다() {
        int template = templateIdOf(AdminFixtures.createDraftForm(token, "anon", V1));

        given().multiPart("file", "v2.html", V2.getBytes(StandardCharsets.UTF_8), "text/html")
            .put("/api/admin/templates/{id}", template).then().statusCode(401).body("code", equalTo("unauthorized"));

        assertThat(jdbc.queryForObject("SELECT version FROM html_templates WHERE id = ?", Integer.class, template))
            .isEqualTo(1);
    }

    private ValidatableResponse put(int template, String filename, String content) {
        return given().auth().oauth2(token)
            .multiPart("file", filename, content.getBytes(StandardCharsets.UTF_8), "text/html")
            .when().put("/api/admin/templates/{id}", template).then();
    }

    private io.restassured.response.Response submit(String slug, Map<String, Object> fields) {
        return PublicOrigin.given(publicPort).contentType(ContentType.JSON)
            .body(Map.of("fields", fields, "consent", true, "ft", FormTokens.mature(publicPort, clock, slug)))
            .post("/api/public/forms/{slug}/submissions", slug);
    }

    private int templateIdOf(int form) {
        return jdbc.queryForObject("SELECT template_id FROM forms WHERE id = ?", Integer.class, form);
    }

    private String publishedHtml(int form) {
        return jdbc.queryForObject("SELECT published_html FROM forms WHERE id = ?", String.class, form);
    }

    private String formJson(int form) {
        return given().auth().oauth2(token).get("/api/admin/forms/{id}", form).asString();
    }

    public static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
