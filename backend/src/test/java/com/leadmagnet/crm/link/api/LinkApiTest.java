package com.leadmagnet.crm.link.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;

import com.leadmagnet.crm.auth.domain.Operator;
import com.leadmagnet.crm.auth.domain.OperatorRepository;
import com.leadmagnet.crm.link.application.LinkService;
import com.leadmagnet.crm.support.AdminLogin;
import com.leadmagnet.crm.support.DatabaseCleaner;
import com.leadmagnet.crm.support.IntegrationTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@IntegrationTest
class LinkApiTest {

    static final String EMAIL = "owner@example.com";
    static final String PASSWORD = "correct-horse-battery";
    static final String FORM_HTML = """
        <!doctype html><html><body><form>
        <input name="name" required><input type="tel" name="phone"><button>신청</button></form></body></html>
        """;

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    OperatorRepository operators;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    LinkService linkService;

    String token;
    int formId;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        DatabaseCleaner.truncateAll(jdbc);
        operators.save(new Operator(EMAIL, passwordEncoder.encode(PASSWORD)));
        token = AdminLogin.accessToken(EMAIL, PASSWORD);
        int campaign = authed().body(Map.of("name", "캠페인")).post("/api/admin/campaigns")
            .then().statusCode(201).extract().path("id");
        int template = given().auth().oauth2(token)
            .multiPart("file", "t.html", FORM_HTML.getBytes(StandardCharsets.UTF_8), "text/html")
            .post("/api/admin/templates").then().statusCode(201).extract().path("id");
        formId = authed().body(Map.of("templateId", template, "title", "제목", "consentText", "동의", "slug", "spring-2026"))
            .post("/api/admin/campaigns/{id}/forms", campaign).then().statusCode(201).extract().path("id");
    }

    @Test
    void 네_채널_링크를_만들면_URL과_UTM이_채워진다() {
        for (String channel : List.of("instagram", "x", "youtube", "threads")) {
            String code = authed().body(Map.of("channel", channel)).post("/api/admin/forms/{id}/links", formId)
                .then().statusCode(201)
                .body("channel", equalTo(channel))
                .body("code", matchesPattern("[0-9A-Za-z]{8}"))
                .body("utmSource", equalTo(channel))
                .body("utmMedium", equalTo("social"))
                .body("utmCampaign", equalTo("spring-2026"))
                .body("createdAt", notNullValue())
                .extract().path("code");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM distribution_links WHERE code = ?", Integer.class, code))
                .isEqualTo(1);
        }
    }

    @Test
    void 응답_url은_PUBLIC_BASE_URL과_code로_만든다() {
        authed().body(Map.of("channel", "instagram")).post("/api/admin/forms/{id}/links", formId)
            .then().statusCode(201)
            .body("url", matchesPattern("http://localhost:8081/l/[0-9A-Za-z]{8}"));
    }

    @Test
    void 목록은_채널_순서로_나온다() {
        for (String channel : List.of("threads", "youtube", "x", "instagram")) {
            authed().body(Map.of("channel", channel)).post("/api/admin/forms/{id}/links", formId).then().statusCode(201);
        }
        authed().get("/api/admin/forms/{id}/links", formId).then().statusCode(200)
            .body("$", hasSize(4))
            .body("channel", equalTo(List.of("instagram", "x", "youtube", "threads")));
    }

    @Test
    void 같은_채널을_두_번_만들면_409_link_exists다() {
        authed().body(Map.of("channel", "x")).post("/api/admin/forms/{id}/links", formId).then().statusCode(201);
        authed().body(Map.of("channel", "x")).post("/api/admin/forms/{id}/links", formId)
            .then().statusCode(409).body("code", equalTo("link_exists"));
    }

    @Test
    void closed_폼에는_링크를_만들_수_없다_published_폼에는_만들_수_있다() {
        authed().body(Map.of("status", "published")).patch("/api/admin/forms/{id}", formId).then().statusCode(200);
        authed().body(Map.of("channel", "x")).post("/api/admin/forms/{id}/links", formId).then().statusCode(201);
        authed().body(Map.of("status", "closed")).patch("/api/admin/forms/{id}", formId).then().statusCode(200);
        authed().body(Map.of("channel", "youtube")).post("/api/admin/forms/{id}/links", formId)
            .then().statusCode(409).body("code", equalTo("form_closed"));
    }

    @Test
    void direct_모르는_채널_빈_채널은_422다() {
        for (String channel : List.of("direct", "facebook", "")) {
            authed().body(Map.of("channel", channel)).post("/api/admin/forms/{id}/links", formId)
                .then().statusCode(422).body("errors[0].field", equalTo("channel"));
        }
        authed().body(Map.of()).post("/api/admin/forms/{id}/links", formId).then().statusCode(422);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM distribution_links", Integer.class)).isZero();
    }

    @Test
    void 없는_폼은_404다() {
        authed().body(Map.of("channel", "x")).post("/api/admin/forms/{id}/links", 9999).then().statusCode(404);
        authed().get("/api/admin/forms/{id}/links", 9999).then().statusCode(404);
    }

    @Test
    void 미인증_요청은_401이다() {
        given().contentType(ContentType.JSON).body(Map.of("channel", "x"))
            .post("/api/admin/forms/{id}/links", formId).then().statusCode(401);
        given().get("/api/admin/forms/{id}/links", formId).then().statusCode(401);
    }

    @Test
    void 공개_단축_URL은_published_폼의_공개_경로로_302한다() {
        authed().body(Map.of("status", "published")).patch("/api/admin/forms/{id}", formId).then().statusCode(200);
        String code = createLink("youtube");
        given().redirects().follow(false).get("/l/{code}", code)
            .then().statusCode(302).header("Location", equalTo("/f/spring-2026?lm=" + code));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM visits", Integer.class)).isZero();
    }

    @Test
    void 없는_code는_404다() {
        given().redirects().follow(false).get("/l/{code}", "zzzzzzzz").then().statusCode(404);
        given().redirects().follow(false).get("/l/{code}", "bad!").then().statusCode(404);
    }

    @Test
    void draft_폼의_code는_404다() {
        String code = createLink("x");
        given().redirects().follow(false).get("/l/{code}", code).then().statusCode(404);
    }

    @Test
    void closed_폼의_code는_410이다() {
        authed().body(Map.of("status", "published")).patch("/api/admin/forms/{id}", formId).then().statusCode(200);
        String code = createLink("x");
        authed().body(Map.of("status", "closed")).patch("/api/admin/forms/{id}", formId).then().statusCode(200);
        given().redirects().follow(false).get("/l/{code}", code)
            .then().statusCode(410).body("code", equalTo("gone"));
    }

    @Test
    void 귀속_조회는_해당_폼의_code만_링크로_돌려준다() {
        String code = createLink("threads");
        assertThat(linkService.resolveForForm(formId, code)).hasValueSatisfying(l ->
            assertThat(l.getChannel().value()).isEqualTo("threads"));
        assertThat(linkService.resolveForForm(formId + 1, code)).isEmpty();
        assertThat(linkService.resolveForForm(formId, "zzzzzzzz")).isEmpty();
    }

    private String createLink(String channel) {
        return authed().body(Map.of("channel", channel)).post("/api/admin/forms/{id}/links", formId)
            .then().statusCode(201).extract().path("code");
    }

    private RequestSpecification authed() {
        return given().auth().oauth2(token).contentType(ContentType.JSON);
    }
}
