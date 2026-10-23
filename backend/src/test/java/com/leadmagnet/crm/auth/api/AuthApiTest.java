package com.leadmagnet.crm.auth.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;

import com.leadmagnet.crm.auth.domain.Operator;
import com.leadmagnet.crm.auth.domain.OperatorRepository;
import com.leadmagnet.crm.auth.domain.RefreshToken;
import com.leadmagnet.crm.support.DatabaseCleaner;
import com.leadmagnet.crm.support.IntegrationTest;
import com.leadmagnet.crm.support.MutableClock;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
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
class AuthApiTest {

    static final String EMAIL = "owner@example.com";
    static final String PASSWORD = "correct-horse-battery";
    static final String COOKIE = "lm_refresh";

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    OperatorRepository operators;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    MutableClock clock;

    long operatorId;

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        DatabaseCleaner.truncateAll(jdbc);
        operatorId = operators.save(new Operator(EMAIL, passwordEncoder.encode(PASSWORD))).getId();
    }

    @Test
    void 로그인하면_access_token을_바디로_주고_refresh_token을_보호된_쿠키로_준다() {
        Response login = login(EMAIL, PASSWORD);

        login.then().statusCode(200)
            .body("accessToken", notNullValue())
            .body("tokenType", equalTo("Bearer"))
            .body("expiresIn", equalTo(900));
        String setCookie = login.getHeader("Set-Cookie");
        assertThat(setCookie)
            .startsWith(COOKIE + "=")
            .contains("HttpOnly", "SameSite=Strict", "Path=/api/admin/auth", "Max-Age=1209600")
            .doesNotContain("Secure");

        given().auth().oauth2(login.path("accessToken"))
            .when().get("/api/admin/auth/me")
            .then().statusCode(200)
            .body("id", equalTo((int) operatorId))
            .body("email", equalTo(EMAIL));
    }

    @Test
    void 로그인_이메일은_대소문자를_구분하지_않는다() {
        login("Owner@Example.COM", PASSWORD).then().statusCode(200);
    }

    @Test
    void 틀린_비밀번호와_없는_이메일은_같은_401_코드로_거부한다() {
        Response wrongPassword = login(EMAIL, "wrong-password-123");
        Response unknownEmail = login("nobody@example.com", PASSWORD);

        wrongPassword.then().statusCode(401).contentType("application/problem+json")
            .body("code", equalTo("invalid_credentials"));
        unknownEmail.then().statusCode(401).contentType("application/problem+json")
            .body("code", equalTo("invalid_credentials"));
        assertThat(wrongPassword.asString()).isEqualTo(unknownEmail.asString());
        assertThat(wrongPassword.getHeader("Set-Cookie")).isNull();
    }

    @Test
    void 비밀번호가_72바이트를_넘어도_401로_거부한다() {
        login(EMAIL, "가".repeat(30)).then().statusCode(401).body("code", equalTo("invalid_credentials"));
    }

    @Test
    void 형식이_틀린_로그인_요청은_422와_필드_오류를_준다() {
        given().contentType(ContentType.JSON).body(Map.of("email", "not-an-email", "password", ""))
            .when().post("/api/admin/auth/login")
            .then().statusCode(422).contentType("application/problem+json")
            .body("code", equalTo("validation_failed"))
            .body("type", equalTo("https://leadmagnet.example/problems/validation_failed"))
            .body("errors.field", hasItem("email"))
            .body("errors.field", hasItem("password"))
            .body("errors.find { it.field == 'password' }.code", equalTo("not_blank"))
            .body("errors.find { it.field == 'email' }.code", equalTo("email"));
    }

    @Test
    void JSON이_아닌_로그인_요청은_400이다() {
        given().contentType(ContentType.JSON).body("{not json")
            .when().post("/api/admin/auth/login")
            .then().statusCode(400).contentType("application/problem+json")
            .body("code", equalTo("malformed_request"));
    }

    @Test
    void refresh하면_새_access_token과_교체된_refresh_쿠키를_주고_새_쿠키로_다시_refresh할_수_있다() {
        Response login = login(EMAIL, PASSWORD);
        String first = login.getCookie(COOKIE);

        Response refreshed = refresh(first);
        refreshed.then().statusCode(200)
            .body("accessToken", notNullValue())
            .body("tokenType", equalTo("Bearer"))
            .body("expiresIn", equalTo(900));
        String second = refreshed.getCookie(COOKIE);
        assertThat(second).isNotBlank().isNotEqualTo(first);
        assertThat(refreshed.getHeader("Set-Cookie")).contains("HttpOnly", "SameSite=Strict", "Path=/api/admin/auth");

        given().auth().oauth2(refreshed.path("accessToken"))
            .when().get("/api/admin/auth/me").then().statusCode(200);

        Response again = refresh(second);
        again.then().statusCode(200);
        assertThat(again.getCookie(COOKIE)).isNotEqualTo(second);

        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT family_id, revoked_at FROM refresh_tokens ORDER BY id");
        assertThat(rows).hasSize(3);
        assertThat(rows).extracting(r -> r.get("family_id")).containsOnly(rows.get(0).get("family_id"));
        assertThat(rows.get(0).get("revoked_at")).isNotNull();
        assertThat(rows.get(1).get("revoked_at")).isNotNull();
        assertThat(rows.get(2).get("revoked_at")).isNull();
    }

    @Test
    void 로그인마다_새_토큰_패밀리를_만든다() {
        login(EMAIL, PASSWORD);
        login(EMAIL, PASSWORD);

        assertThat(jdbc.queryForObject("SELECT count(DISTINCT family_id) FROM refresh_tokens", Long.class))
            .isEqualTo(2);
    }

    @Test
    void 이미_교체된_refresh_token을_다시_쓰면_재사용으로_보고_패밀리_전체를_폐기한다() {
        String tokenA = login(EMAIL, PASSWORD).getCookie(COOKIE);
        String tokenB = refresh(tokenA).getCookie(COOKIE);

        refresh(tokenA).then().statusCode(401).contentType("application/problem+json")
            .body("code", equalTo("refresh_token_reused"));
        refresh(tokenB).then().statusCode(401);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE revoked_at IS NULL", Long.class))
            .isZero();
    }

    @Test
    void 재사용_감지는_다른_로그인의_패밀리를_건드리지_않는다() {
        String tokenA = login(EMAIL, PASSWORD).getCookie(COOKIE);
        refresh(tokenA);
        String otherSession = login(EMAIL, PASSWORD).getCookie(COOKIE);

        refresh(tokenA).then().statusCode(401);

        refresh(otherSession).then().statusCode(200);
    }

    @Test
    void 쿠키가_없거나_모르는_refresh_token이면_401이다() {
        given().when().post("/api/admin/auth/refresh")
            .then().statusCode(401).contentType("application/problem+json")
            .body("code", equalTo("invalid_refresh_token"));
        refresh("unknown-token").then().statusCode(401).body("code", equalTo("invalid_refresh_token"));
    }

    @Test
    void 만료된_refresh_token은_401이다() {
        String token = login(EMAIL, PASSWORD).getCookie(COOKIE);
        jdbc.update("UPDATE refresh_tokens SET expires_at = now() - interval '1 second'");

        refresh(token).then().statusCode(401).body("code", equalTo("invalid_refresh_token"));
    }

    @Test
    void Clock이_14일을_넘기면_refresh_token은_만료되어_401이다() {
        String token = login(EMAIL, PASSWORD).getCookie(COOKIE);

        clock.advance(Duration.ofDays(14).minusMinutes(1));
        String rotated = refresh(token).then().statusCode(200).extract().cookie(COOKIE);

        // 교체된 토큰도 교체 시점부터 14일이다
        clock.advance(Duration.ofDays(14).plusSeconds(1));
        refresh(rotated).then().statusCode(401).body("code", equalTo("invalid_refresh_token"));
    }

    @Test
    void Clock이_15분을_넘기면_access_token은_만료되어_401이다() {
        String accessToken = login(EMAIL, PASSWORD).path("accessToken");
        given().auth().oauth2(accessToken).when().get("/api/admin/auth/me").then().statusCode(200);

        // 디코더의 기본 허용 오차 60초를 넘긴다
        clock.advance(Duration.ofMinutes(15).plusSeconds(61));

        given().auth().oauth2(accessToken).when().get("/api/admin/auth/me")
            .then().statusCode(401).body("code", equalTo("unauthorized"));
    }

    @Test
    void 로그아웃하면_204와_삭제_쿠키를_주고_그_refresh_token은_더_쓸_수_없다() {
        String tokenA = login(EMAIL, PASSWORD).getCookie(COOKIE);
        String tokenB = refresh(tokenA).getCookie(COOKIE);

        Response logout = given().cookie(COOKIE, tokenB).when().post("/api/admin/auth/logout");

        logout.then().statusCode(204);
        assertThat(logout.getHeader("Set-Cookie"))
            .startsWith(COOKIE + "=;")
            .contains("Max-Age=0", "Path=/api/admin/auth", "HttpOnly", "SameSite=Strict");
        refresh(tokenB).then().statusCode(401);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE revoked_at IS NULL", Long.class))
            .isZero();
    }

    @Test
    void 쿠키_없이_로그아웃해도_204다() {
        given().when().post("/api/admin/auth/logout").then().statusCode(204);
        given().cookie(COOKIE, "unknown-token").when().post("/api/admin/auth/logout").then().statusCode(204);
    }

    @Test
    void 응답과_로그에_비밀번호와_refresh_token_해시가_남지_않고_DB에는_해시만_저장한다(CapturedOutput output) {
        Response loginResponse = login(EMAIL, PASSWORD);
        String raw = loginResponse.getCookie(COOKIE);
        Response refreshResponse = refresh(raw);
        login(EMAIL, "wrong-password-123");
        login("nobody@example.com", PASSWORD);

        String hash = RefreshToken.hashOf(raw);
        assertThat(jdbc.queryForList("SELECT token_hash FROM refresh_tokens", String.class))
            .contains(hash).doesNotContain(raw);
        assertThat(loginResponse.asString()).doesNotContain(raw, hash);
        assertThat(refreshResponse.asString()).doesNotContain(raw, hash, refreshResponse.getCookie(COOKIE));
        assertThat(output.getAll())
            .doesNotContain(PASSWORD, "wrong-password-123", raw, hash)
            .doesNotContain(EMAIL, "nobody@example.com");
    }

    private Response login(String email, String password) {
        return given().contentType(ContentType.JSON).body(Map.of("email", email, "password", password))
            .when().post("/api/admin/auth/login");
    }

    private Response refresh(String refreshToken) {
        return given().cookie(COOKIE, refreshToken).when().post("/api/admin/auth/refresh");
    }
}
