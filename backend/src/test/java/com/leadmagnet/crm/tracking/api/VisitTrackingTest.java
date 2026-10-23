package com.leadmagnet.crm.tracking.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.leadmagnet.crm.auth.domain.Operator;
import com.leadmagnet.crm.auth.domain.OperatorRepository;
import com.leadmagnet.crm.common.web.PublicPort;
import com.leadmagnet.crm.support.AdminFixtures;
import com.leadmagnet.crm.support.AdminLogin;
import com.leadmagnet.crm.support.DatabaseCleaner;
import com.leadmagnet.crm.support.IntegrationTest;
import com.leadmagnet.crm.support.PublicOrigin;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 방문은 /f/{slug} 200 응답마다 한 행. 채널은 lm이 이 폼의 링크 code일 때만 그 링크, 아니면 direct (ADR 0006). */
@IntegrationTest
class VisitTrackingTest {

    static final String UUID_V4 = "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}";

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
    int formId;
    int otherFormId;

    @BeforeEach
    void setUp() {
        RestAssured.port = adminPort;
        DatabaseCleaner.truncateAll(jdbc);
        operators.save(new Operator("owner@example.com", passwordEncoder.encode("correct-horse-battery")));
        token = AdminLogin.accessToken("owner@example.com", "correct-horse-battery");
        formId = AdminFixtures.createPublishedForm(token, "spring-2026");
        otherFormId = AdminFixtures.createPublishedForm(token, "autumn-2026");
    }

    @Test
    void lm_없이_방문하면_direct_한_행이_쌓인다() {
        Response response = PublicOrigin.given(publicPort).get("/f/spring-2026");
        response.then().statusCode(200);
        String visitor = response.getCookie("lm_vid");

        List<Map<String, Object>> rows = visits();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("form_id", (long) formId).containsEntry("channel", "direct")
            .containsEntry("link_id", null).containsEntry("visitor_id", UUID.fromString(visitor));
    }

    @Test
    void 이_폼의_링크_code로_방문하면_그_링크와_채널이_기록된다() {
        String code = AdminFixtures.createLink(token, formId, "instagram");
        PublicOrigin.given(publicPort).get("/f/spring-2026?lm={code}", code).then().statusCode(200);

        Long linkId = jdbc.queryForObject("SELECT id FROM distribution_links WHERE code = ?", Long.class, code);
        assertThat(visits()).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("channel", "instagram");
            assertThat(row).containsEntry("link_id", linkId);
        });
    }

    @Test
    void 다른_폼의_code나_모르는_code나_잘못된_code는_direct다() {
        String otherCode = AdminFixtures.createLink(token, otherFormId, "youtube");
        for (String lm : List.of(otherCode, "zzzzzzzz", "bad!", "x".repeat(300), "")) {
            PublicOrigin.given(publicPort).queryParam("lm", lm).get("/f/spring-2026").then().statusCode(200);
        }
        assertThat(visits()).hasSize(5).allSatisfy(row -> {
            assertThat(row).containsEntry("channel", "direct").containsEntry("link_id", null);
            assertThat(row).containsEntry("form_id", (long) formId);
        });
    }

    @Test
    void HEAD_요청은_방문을_기록하지_않는다() {
        PublicOrigin.given(publicPort).head("/f/spring-2026").then().statusCode(200);
        assertThat(visits()).isEmpty();
        PublicOrigin.given(publicPort).get("/f/spring-2026").then().statusCode(200);
        assertThat(visits()).hasSize(1);
    }

    @Test
    void 쿠키가_없으면_lm_vid를_발급한다() {
        Response response = PublicOrigin.given(publicPort).get("/f/spring-2026");
        String header = lmVidHeader(response);
        assertThat(header).matches("lm_vid=" + UUID_V4 + ";.*");
        assertThat(header).contains("Path=/").contains("Max-Age=31536000").contains("HttpOnly")
            .contains("SameSite=Lax").doesNotContain("Secure");
    }

    @Test
    void 같은_쿠키로_두_번_방문하면_두_행에_방문자는_하나다() {
        String visitor = PublicOrigin.given(publicPort).get("/f/spring-2026").getCookie("lm_vid");
        Response second = PublicOrigin.given(publicPort).cookie("lm_vid", visitor).get("/f/spring-2026");

        assertThat(second.headers().getValues("Set-Cookie")).noneMatch(c -> c.startsWith("lm_vid="));
        assertThat(visits()).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT visitor_id) FROM visits", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT visitor_id::text FROM visits LIMIT 1", String.class)).isEqualTo(visitor);
    }

    @Test
    void 형식이_틀린_쿠키는_새로_발급하고_새_값으로_기록한다() {
        for (String bad : List.of("not-a-uuid", "1-1-1-1-1", "00000000-0000-1000-8000-000000000000", "")) {
            Response response = PublicOrigin.given(publicPort).cookie("lm_vid", bad).get("/f/spring-2026");
            String issued = response.getCookie("lm_vid");
            assertThat(issued).as(bad).matches(UUID_V4);
            assertThat(jdbc.queryForObject("SELECT visitor_id::text FROM visits ORDER BY id DESC LIMIT 1",
                String.class)).isEqualTo(issued);
        }
    }

    @Test
    void referrer와_user_agent는_512자까지만_저장한다() {
        PublicOrigin.given(publicPort)
            .header("Referer", "https://example.com/" + "r".repeat(600))
            .header("User-Agent", "u".repeat(700))
            .get("/f/spring-2026").then().statusCode(200);
        Map<String, Object> row = visits().get(0);
        assertThat((String) row.get("referrer")).hasSize(512).startsWith("https://example.com/r");
        assertThat((String) row.get("user_agent")).hasSize(512);
    }

    @Test
    void 열리지_않은_폼은_방문을_기록하지_않고_쿠키도_주지_않는다() {
        int draft = AdminFixtures.createDraftForm(token, "draft-form");
        AdminFixtures.changeStatus(token, otherFormId, "closed");
        for (String slug : List.of("draft-form", "autumn-2026", "no-such-form")) {
            Response response = PublicOrigin.given(publicPort).get("/f/{slug}", slug);
            assertThat(response.statusCode()).isIn(404, 410);
            assertThat(response.getCookie("lm_vid")).isNull();
        }
        assertThat(draft).isPositive();
        assertThat(visits()).isEmpty();
    }

    @Test
    void 단축_URL은_쿠키를_발급하지만_방문은_기록하지_않는다() {
        String code = AdminFixtures.createLink(token, formId, "threads");
        Response first = PublicOrigin.given(publicPort).get("/l/{code}", code);
        first.then().statusCode(302);
        String visitor = first.getCookie("lm_vid");
        assertThat(visitor).matches(UUID_V4);
        assertThat(lmVidHeader(first)).contains("HttpOnly").contains("SameSite=Lax");

        Response again = PublicOrigin.given(publicPort).cookie("lm_vid", visitor).get("/l/{code}", code);
        again.then().statusCode(302);
        assertThat(again.headers().getValues("Set-Cookie")).noneMatch(c -> c.startsWith("lm_vid="));
        assertThat(visits()).isEmpty();
    }

    @Test
    void 단축_URL로_받은_쿠키로_폼을_열면_같은_방문자로_기록된다() {
        String code = AdminFixtures.createLink(token, formId, "x");
        String visitor = PublicOrigin.given(publicPort).get("/l/{code}", code).getCookie("lm_vid");
        PublicOrigin.given(publicPort).cookie("lm_vid", visitor).get("/f/spring-2026?lm={code}", code)
            .then().statusCode(200);
        assertThat(visits()).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("visitor_id", UUID.fromString(visitor));
            assertThat(row).containsEntry("channel", "x");
        });
    }

    private List<Map<String, Object>> visits() {
        return jdbc.queryForList("SELECT form_id, link_id, channel, visitor_id, referrer, user_agent FROM visits ORDER BY id");
    }

    private static String lmVidHeader(Response response) {
        return response.headers().getValues("Set-Cookie").stream()
            .filter(c -> c.startsWith("lm_vid=")).findFirst().orElseThrow();
    }
}
