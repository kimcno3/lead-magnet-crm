package com.leadmagnet.crm.support;

import static io.restassured.RestAssured.given;

import com.leadmagnet.crm.auth.domain.Operator;
import com.leadmagnet.crm.auth.domain.OperatorRepository;
import com.leadmagnet.crm.common.web.PublicPort;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 시나리오 테스트 공통 준비: DB 비우기, 운영자 시드, 포트 설정, 그리고 AI가 만든 듯한 폼 HTML. */
public abstract class ScenarioBase {

    public static final String EMAIL = "owner@example.com";
    public static final String PASSWORD = "correct-horse-battery";
    public static final String REFRESH_COOKIE = "lm_refresh";

    /** 인라인 style·script, select, 체크박스 그룹, textarea가 든 AI 스타일 단일 HTML 폼. */
    public static final String AI_FORM_HTML = """
        <!DOCTYPE html>
        <html lang="ko"><head><meta charset="utf-8"><title>봄 특강</title>
        <style>body{font-family:sans-serif;background:#f6f7fb}.card{max-width:480px;margin:40px auto}</style>
        </head><body><div class="card">
        <h1>봄 특강 사전 신청</h1>
        <form id="lead-form" action="#" method="post">
          <label>이름 <input type="text" name="name" required></label>
          <label>휴대폰 <input type="tel" name="phone" required></label>
          <label>이메일 <input type="email" name="email"></label>
          <label>직업 <select name="job"><option value="student">학생</option><option value="worker">직장인</option></select></label>
          <fieldset><legend>관심 주제</legend>
            <label><input type="checkbox" name="interests" value="ai">AI</label>
            <label><input type="checkbox" name="interests" value="marketing">마케팅</label>
          </fieldset>
          <label>문의 <textarea name="memo"></textarea></label>
          <button type="submit">신청하기</button>
        </form></div>
        <script>document.getElementById('lead-form').addEventListener('submit',function(e){e.preventDefault();});</script>
        </body></html>
        """;

    @LocalServerPort
    protected int adminPort;

    @Autowired
    protected PublicPort publicPort;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected OperatorRepository operators;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @BeforeEach
    void prepareScenario() {
        RestAssured.port = adminPort;
        DatabaseCleaner.truncateAll(jdbc);
        operators.save(new Operator(EMAIL, passwordEncoder.encode(PASSWORD), Instant.now()));
    }

    protected PublicVisitor newVisitor() {
        return new PublicVisitor(publicPort);
    }

    protected String publicOrigin() {
        return "http://localhost:" + publicPort.get();
    }

    protected Response login(String email, String password) {
        return given().contentType(ContentType.JSON).body(Map.of("email", email, "password", password))
            .post("/api/admin/auth/login");
    }

    protected Response refresh(String refreshToken) {
        return given().cookie(REFRESH_COOKIE, refreshToken).post("/api/admin/auth/refresh");
    }

    protected static RequestSpecification authed(String token) {
        return given().auth().oauth2(token);
    }

    protected static RequestSpecification authedJson(String token) {
        return authed(token).contentType(ContentType.JSON);
    }

    protected static Response upload(String token, String filename, byte[] content, String contentType) {
        return authed(token).multiPart("file", filename, content, contentType).post("/api/admin/templates");
    }

    protected static int uploadTemplate(String token, String html) {
        return upload(token, "spring.html", html.getBytes(StandardCharsets.UTF_8), "text/html")
            .then().statusCode(201).extract().path("id");
    }

    protected static int createForm(String token, int campaign, int template, String slug) {
        return authedJson(token)
            .body(Map.of("templateId", template, "title", "봄 특강 신청", "consentText", "개인정보 수집에 동의합니다",
                "slug", slug))
            .post("/api/admin/campaigns/{id}/forms", campaign).then().statusCode(201).extract().path("id");
    }

    protected static Response createLink(String token, int formId, String channel) {
        return authedJson(token).body(Map.of("channel", channel)).post("/api/admin/forms/{id}/links", formId);
    }
}
