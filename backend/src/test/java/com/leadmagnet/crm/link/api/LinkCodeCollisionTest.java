package com.leadmagnet.crm.link.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.leadmagnet.crm.auth.domain.Operator;
import com.leadmagnet.crm.auth.domain.OperatorRepository;
import com.leadmagnet.crm.link.application.RandomLinkCodeGenerator;
import com.leadmagnet.crm.support.AdminLogin;
import com.leadmagnet.crm.support.DatabaseCleaner;
import com.leadmagnet.crm.support.IntegrationTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** 실제 Postgres의 ux_distribution_links_code 위반이 재시도로 이어지는지 확인한다. */
@IntegrationTest
class LinkCodeCollisionTest {

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    OperatorRepository operators;

    @Autowired
    PasswordEncoder passwordEncoder;

    @MockitoSpyBean
    RandomLinkCodeGenerator generator;

    String token;
    int formId;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        DatabaseCleaner.truncateAll(jdbc);
        operators.save(new Operator("owner@example.com", passwordEncoder.encode("correct-horse-battery")));
        token = AdminLogin.accessToken("owner@example.com", "correct-horse-battery");
        int campaign = authed().body(Map.of("name", "c")).post("/api/admin/campaigns").then().extract().path("id");
        int template = given().auth().oauth2(token)
            .multiPart("file", "t.html", "<form><input name=\"phone\"></form>".getBytes(StandardCharsets.UTF_8), "text/html")
            .post("/api/admin/templates").then().statusCode(201).extract().path("id");
        formId = authed().body(Map.of("templateId", template, "title", "t", "consentText", "c", "slug", "collide-form"))
            .post("/api/admin/campaigns/{id}/forms", campaign).then().statusCode(201).extract().path("id");
    }

    @Test
    void 이미_있는_코드가_나오면_유니크_인덱스_위반을_넘어_다음_코드로_저장한다() {
        authed().body(Map.of("channel", "instagram")).post("/api/admin/forms/{id}/links", formId).then().statusCode(201);
        String taken = jdbc.queryForObject("SELECT code FROM distribution_links", String.class);
        when(generator.next()).thenReturn(taken, taken, "freeCode");

        String code = authed().body(Map.of("channel", "x")).post("/api/admin/forms/{id}/links", formId)
            .then().statusCode(201).extract().path("code");

        assertThat(code).isEqualTo("freeCode");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM distribution_links", Integer.class)).isEqualTo(2);
    }

    @Test
    void 다섯_번_모두_충돌하면_500이고_링크는_늘지_않는다() {
        authed().body(Map.of("channel", "instagram")).post("/api/admin/forms/{id}/links", formId).then().statusCode(201);
        String taken = jdbc.queryForObject("SELECT code FROM distribution_links", String.class);
        when(generator.next()).thenReturn(taken);

        authed().body(Map.of("channel", "x")).post("/api/admin/forms/{id}/links", formId).then().statusCode(500);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM distribution_links", Integer.class)).isEqualTo(1);
    }

    private io.restassured.specification.RequestSpecification authed() {
        return given().auth().oauth2(token).contentType(ContentType.JSON);
    }
}
