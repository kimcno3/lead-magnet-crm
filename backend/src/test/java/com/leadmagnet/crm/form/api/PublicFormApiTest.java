package com.leadmagnet.crm.form.api;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;

import com.leadmagnet.crm.auth.domain.Operator;
import com.leadmagnet.crm.auth.domain.OperatorRepository;
import com.leadmagnet.crm.common.web.PublicPort;
import com.leadmagnet.crm.support.AdminFixtures;
import com.leadmagnet.crm.support.AdminLogin;
import com.leadmagnet.crm.support.DatabaseCleaner;
import com.leadmagnet.crm.support.IntegrationTest;
import com.leadmagnet.crm.support.PublicOrigin;
import io.restassured.RestAssured;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 공개 폼 API와 래퍼 페이지. published만 열리고 draft·없는 slug는 404, closed는 410이다. */
@IntegrationTest
class PublicFormApiTest {

    static final String PROBLEM_JSON = "application/problem+json";

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

    @BeforeEach
    void setUp() {
        RestAssured.port = adminPort;
        DatabaseCleaner.truncateAll(jdbc);
        operators.save(new Operator("owner@example.com", passwordEncoder.encode("correct-horse-battery")));
        token = AdminLogin.accessToken("owner@example.com", "correct-horse-battery");
    }

    @Test
    void published_폼의_공개_API는_제목_동의문구_필드_템플릿_원문을_준다() {
        AdminFixtures.createPublishedForm(token, "spring-2026");
        PublicOrigin.given(publicPort).get("/api/public/forms/spring-2026")
            .then().statusCode(200)
            .body("slug", equalTo("spring-2026"))
            .body("title", equalTo("봄 특강 신청"))
            .body("consentText", equalTo("개인정보 수집에 동의합니다"))
            .body("fields.name", equalTo(List.of("name", "phone")))
            .body("fields[0].required", equalTo(true))
            .body("fields[1].type", equalTo("tel"))
            .body("html", equalTo(AdminFixtures.FORM_HTML));
    }

    @Test
    void draft_폼의_공개_API는_404다() {
        AdminFixtures.createDraftForm(token, "spring-2026");
        PublicOrigin.given(publicPort).get("/api/public/forms/spring-2026")
            .then().statusCode(404).contentType(PROBLEM_JSON).body("code", equalTo("not_found"));
    }

    @Test
    void closed_폼의_공개_API는_410이다() {
        int formId = AdminFixtures.createPublishedForm(token, "spring-2026");
        AdminFixtures.changeStatus(token, formId, "closed");
        PublicOrigin.given(publicPort).get("/api/public/forms/spring-2026")
            .then().statusCode(410).contentType(PROBLEM_JSON).body("code", equalTo("gone"));
    }

    @Test
    void 없는_slug의_공개_API는_404다() {
        PublicOrigin.given(publicPort).get("/api/public/forms/no-such-form")
            .then().statusCode(404).contentType(PROBLEM_JSON).body("code", equalTo("not_found"));
        PublicOrigin.given(publicPort).get("/api/public/forms/_assets")
            .then().statusCode(404).contentType(PROBLEM_JSON);
    }

    @Test
    void published_폼의_래퍼_페이지는_200_HTML이다() {
        AdminFixtures.createPublishedForm(token, "spring-2026");
        PublicOrigin.given(publicPort).get("/f/spring-2026")
            .then().statusCode(200).contentType(startsWith("text/html"))
            .body(containsString("<html lang=\"ko\">"))
            .body(containsString("title=\"신청 폼\""))
            .body(containsString("<label for=\"lm-consent\""))
            .body(containsString("aria-live=\"polite\""));
    }

    @Test
    void draft_폼과_없는_slug의_래퍼_페이지는_404_HTML_안내다() {
        AdminFixtures.createDraftForm(token, "spring-2026");
        for (String slug : List.of("spring-2026", "no-such-form")) {
            PublicOrigin.given(publicPort).get("/f/{slug}", slug)
                .then().statusCode(404).contentType(startsWith("text/html"))
                .body(containsString("신청 폼을 찾을 수 없습니다"))
                .body(containsString("<html lang=\"ko\">"));
        }
    }

    @Test
    void closed_폼의_래퍼_페이지는_410_HTML_안내다() {
        int formId = AdminFixtures.createPublishedForm(token, "spring-2026");
        AdminFixtures.changeStatus(token, formId, "closed");
        PublicOrigin.given(publicPort).get("/f/spring-2026")
            .then().statusCode(410).contentType(startsWith("text/html"))
            .body(containsString("마감된 신청입니다"));
    }

    @Test
    void 래퍼_스크립트는_public_포트에서_자바스크립트로_서빙된다() {
        PublicOrigin.given(publicPort).get("/f/_assets/form-wrapper.js")
            .then().statusCode(200)
            .contentType(anyOf(startsWith("application/javascript"), startsWith("text/javascript")))
            .body(containsString("lm:submit"))
            .body(containsString("/api/public/forms/"));
    }

    @Test
    void 없는_정적_파일은_404다() {
        PublicOrigin.given(publicPort).get("/f/_assets/missing.js")
            .then().statusCode(404).contentType(PROBLEM_JSON);
    }
}
