package com.leadmagnet.crm.audit.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.leadmagnet.crm.auth.domain.Operator;
import com.leadmagnet.crm.auth.domain.OperatorRepository;
import com.leadmagnet.crm.support.AdminFixtures;
import com.leadmagnet.crm.support.AdminLogin;
import com.leadmagnet.crm.support.DatabaseCleaner;
import com.leadmagnet.crm.support.IntegrationTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 새 감사 행의 IP (ADR 0016, 0021). 테스트 클라이언트(루프백)는 신뢰 프록시이므로 admin 포트의 X-Forwarded-For 마지막
 * 주소가 audit_logs.ip가 된다. 공개 포트가 XFF로 IP를 바꾸지 못한다는 확인은 ClientIpFilterTest·PublicOriginIsolationTest가 한다.
 */
@IntegrationTest
class AuditClientIpTest {

    static final String EMAIL = "owner@example.com";
    static final String PASSWORD = "correct-horse-battery";
    static final String V1 = "<form><input name=\"name\"><input name=\"phone\" type=\"tel\"></form>";
    static final String V2 = "<form><input name=\"name\"><input name=\"phone\" type=\"tel\"><input name=\"memo\"></form>";
    static final String CLIENT_IP = "203.0.113.45";
    /** 앞 값은 클라이언트가 붙인 위조 값이다. 신뢰 프록시가 오른쪽에 덧붙인 실제 클라이언트 주소만 쓴다. */
    static final String XFF = "198.51.100.200, " + CLIENT_IP;

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    OperatorRepository operators;

    @Autowired
    PasswordEncoder passwordEncoder;

    String token;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        DatabaseCleaner.truncateAll(jdbc);
        operators.save(new Operator(EMAIL, passwordEncoder.encode(PASSWORD), Instant.now()));
        token = AdminLogin.accessToken(EMAIL, PASSWORD);
    }

    RequestSpecification viaProxy() {
        return given().auth().oauth2(token).contentType(ContentType.JSON).header("X-Forwarded-For", XFF);
    }

    String ipOf(String action) {
        return jdbc.queryForObject("SELECT ip FROM audit_logs WHERE action = ? ORDER BY id DESC LIMIT 1",
            String.class, action);
    }

    @Test
    void 폼_게시의_ip는_XFF의_클라이언트_주소다() {
        int form = AdminFixtures.createDraftForm(token, "ip-publish");

        viaProxy().body(Map.of("status", "published")).patch("/api/admin/forms/{id}", form).then().statusCode(200);

        assertThat(ipOf("form.publish")).isEqualTo(CLIENT_IP);
    }

    @Test
    void 템플릿_새_버전_업로드의_ip는_XFF의_클라이언트_주소다() {
        int template = given().auth().oauth2(token)
            .multiPart("file", "t.html", V1.getBytes(StandardCharsets.UTF_8), "text/html")
            .post("/api/admin/templates").then().statusCode(201).extract().path("id");

        given().auth().oauth2(token).header("X-Forwarded-For", XFF)
            .multiPart("file", "t2.html", V2.getBytes(StandardCharsets.UTF_8), "text/html")
            .put("/api/admin/templates/{id}", template).then().statusCode(200);

        assertThat(ipOf("template.version.upload")).isEqualTo(CLIENT_IP);
    }

    @Test
    void 크리에이터_보관의_ip는_XFF의_클라이언트_주소다() {
        int creator = viaProxy().body(Map.of("name", "김하늘", "handle", "@sky_kim")).post("/api/admin/creators")
            .then().statusCode(201).extract().path("id");

        viaProxy().body(Map.of("status", "archived")).patch("/api/admin/creators/{id}", creator).then().statusCode(200);

        assertThat(ipOf("creator.archive")).isEqualTo(CLIENT_IP);
    }

    @Test
    void 로그아웃의_ip는_XFF의_클라이언트_주소다() {
        String cookie = given().contentType(ContentType.JSON).body(Map.of("email", EMAIL, "password", PASSWORD))
            .post("/api/admin/auth/login").getCookie("lm_refresh");

        given().cookie("lm_refresh", cookie).header("X-Forwarded-For", XFF).post("/api/admin/auth/logout")
            .then().statusCode(204);

        assertThat(ipOf("auth.logout")).isEqualTo(CLIENT_IP);
    }
}
