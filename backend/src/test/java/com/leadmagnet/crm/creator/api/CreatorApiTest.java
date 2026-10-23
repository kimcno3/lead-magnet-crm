package com.leadmagnet.crm.creator.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import com.leadmagnet.crm.auth.domain.Operator;
import com.leadmagnet.crm.auth.domain.OperatorRepository;
import com.leadmagnet.crm.support.AdminLogin;
import com.leadmagnet.crm.support.DatabaseCleaner;
import com.leadmagnet.crm.support.IntegrationTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class CreatorApiTest {

    static final String EMAIL = "owner@example.com";
    static final String PASSWORD = "correct-horse-battery";

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

    @Test
    void 크리에이터를_만들면_201과_Location_active_상태를_돌려준다() {
        int id = authed().body(Map.of("name", " 김하늘 ", "handle", "@sky_kim", "memo", "R/S 20%"))
            .post("/api/admin/creators").then().statusCode(201)
            .header("Location", containsString("/api/admin/creators/"))
            .body("name", equalTo("김하늘")).body("handle", equalTo("@sky_kim")).body("memo", equalTo("R/S 20%"))
            .body("status", equalTo("active")).body("createdAt", notNullValue()).body("updatedAt", notNullValue())
            .extract().path("id");

        assertThat(jdbc.queryForObject("SELECT created_by FROM creators WHERE id = ?", Long.class, id))
            .isEqualTo(operatorId);
        authed().get("/api/admin/creators/{id}", id).then().statusCode(200)
            .body("id", equalTo(id)).body("name", equalTo("김하늘"));
    }

    @Test
    void 핸들과_메모는_선택이다() {
        authed().body(Map.of("name", "박바다")).post("/api/admin/creators").then().statusCode(201)
            .body("handle", nullValue()).body("memo", nullValue());
    }

    @Test
    void 이름이_없거나_너무_길면_422다() {
        authed().body(Map.of()).post("/api/admin/creators").then().statusCode(422)
            .body("code", equalTo("validation_failed")).body("errors[0].field", equalTo("name"));
        authed().body(Map.of("name", "a".repeat(101))).post("/api/admin/creators").then().statusCode(422)
            .body("errors[0].field", equalTo("name"));
        authed().body(Map.of("name", "김", "handle", "h".repeat(101))).post("/api/admin/creators").then()
            .statusCode(422).body("errors[0].field", equalTo("handle"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM creators", Integer.class)).isZero();
    }

    @Test
    void 목록은_최신순이다() {
        int first = create("첫째");
        int second = create("둘째");

        authed().get("/api/admin/creators").then().statusCode(200)
            .body("$", hasSize(2)).body("id", equalTo(List.of(second, first)))
            .body("name", equalTo(List.of("둘째", "첫째")));
    }

    @Test
    void 수정은_준_필드만_바꾸고_빈_문자열은_핸들을_지운다() {
        int id = authed().body(Map.of("name", "김하늘", "handle", "@sky", "memo", "메모"))
            .post("/api/admin/creators").then().statusCode(201).extract().path("id");

        authed().body(Map.of("name", "김하늘2", "handle", "")).patch("/api/admin/creators/{id}", id).then()
            .statusCode(200).body("name", equalTo("김하늘2")).body("handle", nullValue()).body("memo", equalTo("메모"))
            .body("status", equalTo("active"));
    }

    @Test
    void status_archived로_보관하고_다시_보관하면_409다() {
        int id = create("김하늘");

        authed().body(Map.of("status", "archived")).patch("/api/admin/creators/{id}", id).then().statusCode(200)
            .body("status", equalTo("archived"));
        authed().body(Map.of("status", "archived")).patch("/api/admin/creators/{id}", id).then().statusCode(409)
            .body("code", equalTo("creator_already_archived"));
    }

    @Test
    void status는_archived만_받는다() {
        int id = create("김하늘");

        authed().body(Map.of("status", "active")).patch("/api/admin/creators/{id}", id).then().statusCode(422)
            .body("errors[0].field", equalTo("status"));
        Map<String, Object> body = new HashMap<>();
        body.put("name", "새 이름");
        body.put("status", "deleted");
        authed().body(body).patch("/api/admin/creators/{id}", id).then().statusCode(422);
        authed().get("/api/admin/creators/{id}", id).then().body("name", equalTo("김하늘"))
            .body("status", equalTo("active"));
    }

    @Test
    void 없는_크리에이터는_404다() {
        authed().get("/api/admin/creators/{id}", 9999).then().statusCode(404).body("code", equalTo("not_found"));
        authed().body(Map.of("name", "x")).patch("/api/admin/creators/{id}", 9999).then().statusCode(404);
    }

    @Test
    void 미인증_요청은_401이다() {
        int id = create("김하늘");
        given().contentType(ContentType.JSON).body(Map.of("name", "x")).post("/api/admin/creators").then()
            .statusCode(401).contentType(containsString("application/problem+json"));
        given().get("/api/admin/creators").then().statusCode(401);
        given().get("/api/admin/creators/{id}", id).then().statusCode(401);
        given().contentType(ContentType.JSON).body(Map.of("status", "archived"))
            .patch("/api/admin/creators/{id}", id).then().statusCode(401);
        assertThat(jdbc.queryForObject("SELECT status FROM creators WHERE id = ?", String.class, id))
            .isEqualTo("active");
    }

    @Test
    void 메모의_연락처와_제어_문자_거절은_로그에_원문을_남기지_않는다(CapturedOutput output) {
        int id = authed().body(Map.of("name", "김하늘", "memo", "정산 담당 010-4321-8765 sky.pay@example.com"))
            .post("/api/admin/creators").then().statusCode(201).extract().path("id");
        authed().body(Map.of("memo", "비밀\u0000메모 010-4321-8765")).patch("/api/admin/creators/{id}", id).then()
            .statusCode(422).body("errors[0].field", equalTo("memo"));
        authed().get("/api/admin/creators").then().statusCode(200);

        assertThat(output.getAll()).doesNotContain("010-4321-8765").doesNotContain("01043218765")
            .doesNotContain("sky.pay@example.com").doesNotContain("비밀");
    }

    private int create(String name) {
        return authed().body(Map.of("name", name)).post("/api/admin/creators").then().statusCode(201)
            .extract().path("id");
    }

    private RequestSpecification authed() {
        return given().auth().oauth2(token).contentType(ContentType.JSON);
    }
}
