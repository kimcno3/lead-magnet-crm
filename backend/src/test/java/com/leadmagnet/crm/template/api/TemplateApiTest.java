package com.leadmagnet.crm.template.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import com.leadmagnet.crm.auth.domain.Operator;
import com.leadmagnet.crm.auth.domain.OperatorRepository;
import com.leadmagnet.crm.support.AdminLogin;
import com.leadmagnet.crm.support.DatabaseCleaner;
import com.leadmagnet.crm.support.IntegrationTest;
import io.restassured.RestAssured;
import io.restassured.builder.MultiPartSpecBuilder;
import io.restassured.response.ValidatableResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@IntegrationTest
class TemplateApiTest {

    static final String EMAIL = "owner@example.com";
    static final String PASSWORD = "correct-horse-battery";
    static final String FORM = """
        <!doctype html><html><head><title>t</title><script>console.log(1)</script></head><body>
        <form action="https://evil.example/x">
        <label for="n">이름</label><input id="n" name="name" required>
        <input type="tel" name="phone" placeholder="010">
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
        operatorId = operators.save(new Operator(EMAIL, passwordEncoder.encode(PASSWORD))).getId();
        token = AdminLogin.accessToken(EMAIL, PASSWORD);
    }

    @Test
    void 업로드하면_201과_추출한_필드를_주고_목록과_상세에서_조회된다() throws Exception {
        String sha = HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(FORM.getBytes(StandardCharsets.UTF_8)));

        int id = upload("landing.html", FORM, null)
            .statusCode(201)
            .header("Location", endsWith("/api/admin/templates/1"))
            .body("name", equalTo("landing"))
            .body("size", equalTo(FORM.getBytes(StandardCharsets.UTF_8).length))
            .body("sha256", equalTo(sha))
            .body("fields", hasSize(3))
            .body("fields[0].name", equalTo("name"))
            .body("fields[0].label", equalTo("이름"))
            .body("fields[0].required", equalTo(true))
            .body("fields[2].options", equalTo(java.util.List.of("a", "b")))
            .body("createdAt", notNullValue())
            .extract().path("id");

        assertThat(jdbc.queryForObject("SELECT created_by FROM html_templates WHERE id = ?", Long.class, id))
            .isEqualTo(operatorId);

        authed().get("/api/admin/templates").then().statusCode(200)
            .body("$", hasSize(1))
            .body("[0].id", equalTo(id))
            .body("[0].fieldCount", equalTo(3))
            .body("[0].name", equalTo("landing"));

        authed().get("/api/admin/templates/{id}", id).then().statusCode(200)
            .body("content", equalTo(FORM))
            .body("sha256", equalTo(sha))
            .body("fields", hasSize(3));
    }

    @Test
    void 이름을_주면_그_이름을_쓰고_BOM은_제거한다() {
        upload("a.HTM", "﻿" + FORM, "내 템플릿").statusCode(201).body("name", equalTo("내 템플릿"));

        authed().get("/api/admin/templates/{id}", 1).then().body("content", equalTo(FORM));
    }

    @Test
    void 목록은_최신순이다() {
        upload("first.html", FORM, null).statusCode(201);
        upload("second.html", FORM, null).statusCode(201);

        authed().get("/api/admin/templates").then().statusCode(200)
            .body("name", equalTo(java.util.List.of("second", "first")));
    }

    @Test
    void 토큰_없이_부르면_401이다() {
        given().multiPart("file", "a.html", FORM.getBytes(StandardCharsets.UTF_8), "text/html")
            .when().post("/api/admin/templates").then().statusCode(401).body("code", equalTo("unauthorized"));
        given().when().get("/api/admin/templates").then().statusCode(401);
        given().when().get("/api/admin/templates/1").then().statusCode(401);
    }

    @Test
    void 빈_파일은_422_template_empty이다() {
        upload("a.html", "", null).statusCode(422).contentType("application/problem+json")
            .body("code", equalTo("template_empty"));
    }

    @Test
    void 확장자가_html이_아니면_422이다() {
        upload("a.txt", FORM, null).statusCode(422).body("code", equalTo("template_invalid_extension"));
    }

    @Test
    void UTF8이_아니면_422이다() {
        given().auth().oauth2(token)
            .multiPart("file", "a.html", "<form>안</form>".getBytes(java.nio.charset.Charset.forName("EUC-KR")), "text/html")
            .when().post("/api/admin/templates")
            .then().statusCode(422).body("code", equalTo("template_not_utf8"));
    }

    @Test
    void NUL_문자가_있으면_422이다() {
        upload("a.html", FORM + "\u0000", null).statusCode(422).body("code", equalTo("template_not_utf8"));
    }

    @Test
    void 계약을_어기면_각각_다른_code로_422이다() {
        upload("a.html", "<p>no form</p>", null).statusCode(422).body("code", equalTo("template_form_count_invalid"));
        upload("a.html", "<form><input name=\"email\"><input type=\"file\" name=\"f\"></form>", null)
            .statusCode(422).body("code", equalTo("template_forbidden_field"));
        upload("a.html", "<form><input name=\"email\"><input name=\"1x\"></form>", null)
            .statusCode(422).body("code", equalTo("template_field_invalid"));
        upload("a.html", "<form><input name=\"company\"></form>", null)
            .statusCode(422).body("code", equalTo("template_contact_field_missing"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM html_templates", Long.class)).isZero();
    }

    @Test
    void 이름이_100자를_넘으면_422이다() {
        upload("a.html", FORM, "가".repeat(101)).statusCode(422).body("code", equalTo("validation_failed"))
            .body("errors[0].field", equalTo("name"));
    }

    @Test
    void 파일_파트가_없으면_400이다() {
        given().auth().oauth2(token).multiPart("name", "x")
            .when().post("/api/admin/templates").then().statusCode(400).body("code", equalTo("malformed_request"));
    }

    @Test
    void 파일이_512KB를_넘으면_413이다() {
        String big = FORM + "<!--" + "a".repeat(600 * 1024) + "-->";

        upload("big.html", big, null).statusCode(413).contentType("application/problem+json")
            .body("code", equalTo("template_too_large"));
    }

    @Test
    void 파일이_512KB_이하면_통과한다() {
        String limit = FORM + "<!--" + "a".repeat(512 * 1024 - FORM.getBytes(StandardCharsets.UTF_8).length - 7) + "-->";
        assertThat(limit.getBytes(StandardCharsets.UTF_8)).hasSize(512 * 1024);

        upload("edge.html", limit, null).statusCode(201);
    }

    @Test
    void multipart_한도_1MB를_넘으면_413이다() {
        upload("huge.html", "a".repeat(2 * 1024 * 1024), null).statusCode(413)
            .contentType("application/problem+json").body("code", equalTo("payload_too_large"));
    }

    @Test
    void 없는_id는_404이다() {
        authed().get("/api/admin/templates/{id}", 999).then().statusCode(404)
            .contentType("application/problem+json").body("code", equalTo("not_found"));
    }

    private io.restassured.specification.RequestSpecification authed() {
        return given().auth().oauth2(token).when();
    }

    private ValidatableResponse upload(String filename, String content, String name) {
        var request = given().auth().oauth2(token)
            .multiPart("file", filename, content.getBytes(StandardCharsets.UTF_8), "text/html");
        if (name != null) {
            request.multiPart(new MultiPartSpecBuilder(name).controlName("name").charset(StandardCharsets.UTF_8).build());
        }
        return request.when().post("/api/admin/templates").then();
    }
}
