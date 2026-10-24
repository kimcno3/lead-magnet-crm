package com.leadmagnet.crm.lead.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import com.leadmagnet.crm.LeadMagnetApplication;
import com.leadmagnet.crm.auth.domain.Operator;
import com.leadmagnet.crm.auth.domain.OperatorRepository;
import com.leadmagnet.crm.common.crypto.PiiCipher;
import com.leadmagnet.crm.common.crypto.PiiKeyCheck;
import com.leadmagnet.crm.common.web.PublicPort;
import com.leadmagnet.crm.support.AdminFixtures;
import com.leadmagnet.crm.support.AdminLogin;
import com.leadmagnet.crm.support.DatabaseCleaner;
import com.leadmagnet.crm.support.FormTokens;
import com.leadmagnet.crm.support.IntegrationTest;
import com.leadmagnet.crm.support.LegacyPiiCiphertext;
import com.leadmagnet.crm.support.MutableClock;
import com.leadmagnet.crm.support.PublicOrigin;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 운영 기본 설정(LEAD_ENC_KEY_ID 없음 = 1, 이전 키 없음)으로 v0.3.0 데이터 위에 기동한다 (ADR 0022). 공유 컨텍스트는 교체 중
 * 설정(키 id 2)이므로 같은 DB에 기본 설정의 앱을 하나 더 띄운다. v0.3.0의 형식 1 표식이 통과하고, 새 신청은 형식 2·키 id 1로
 * 저장되며, 원문 보기가 되고, 재암호화할 것이 없어야 한다.
 */
@IntegrationTest
class PiiDefaultKeyConfigTest {

    static final String HTML = """
        <form><input name="name"><input name="phone" type="tel"><input name="email" type="email"></form>
        """;
    static final byte[] V030_KEY = Base64.getDecoder().decode(IntegrationTest.TEST_ENC_KEY);

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

    @Autowired
    PiiKeyCheck keyCheck;

    @Autowired
    PostgreSQLContainer<?> postgres;

    String token;

    @BeforeEach
    void setUp() {
        RestAssured.port = adminPort;
        DatabaseCleaner.truncateAll(jdbc);
        operators.save(new Operator("owner@example.com", passwordEncoder.encode("correct-horse-battery"), Instant.now()));
        token = AdminLogin.accessToken("owner@example.com", "correct-horse-battery");
        AdminFixtures.createPublishedForm(token, "form-a", HTML);
        // v0.3.0이 만든 표식: 형식 1, 그때의 키(TEST_ENC_KEY).
        keyCheck.verify();
        jdbc.update("UPDATE pii_key_check SET enc_check = ? WHERE id = 1",
            LegacyPiiCiphertext.v1(V030_KEY, "lead-magnet-crm:pii-key-check", "pii_key_check"));
    }

    @AfterEach
    void restore() {
        clock.reset();
        jdbc.update("DELETE FROM pii_key_check");
        keyCheck.verify();
    }

    @Test
    void 기본_설정이면_형식1_표식이_통과하고_새_신청은_형식2_키_id_1로_저장되며_원문을_볼_수_있다() {
        SpringApplicationBuilder app = new SpringApplicationBuilder(LeadMagnetApplication.class)
            .properties("DB_URL=" + postgres.getJdbcUrl(), "DB_USERNAME=" + postgres.getUsername(),
                "DB_PASSWORD=" + postgres.getPassword(),
                "JWT_SECRET=test-only-jwt-secret-not-for-production-0123456789",
                "LEAD_ENC_KEY=" + IntegrationTest.TEST_ENC_KEY, "LEAD_HMAC_KEY=" + IntegrationTest.TEST_HMAC_KEY,
                "FORM_TOKEN_KEY=" + IntegrationTest.TEST_FORM_TOKEN_KEY,
                "ADMIN_PORT=0", "PUBLIC_PORT=0", "app.retention.enabled=false",
                "app.public.rate-limit.ip-per-minute=100000", "app.public.rate-limit.visitor-per-minute=100000",
                "spring.main.banner-mode=off");

        try (ConfigurableApplicationContext defaults = app.run()) {
            PiiCipher cipher = defaults.getBean(PiiCipher.class);
            assertThat(cipher.currentKeyId()).isEqualTo(1);
            assertThat(defaults.getBean(PiiKeyCheck.class).markerOnCurrentKey()).isTrue();

            PublicOrigin.given(defaults.getBean(PublicPort.class)).contentType(ContentType.JSON)
                .body(Map.of("fields", Map.of("name", "홍길동", "phone", "010-8888-0001", "email", "kim@example.com"),
                    "consent", true, "ft", FormTokens.mature(publicPort, clock, "form-a")))
                .post("/api/public/forms/form-a/submissions").then().statusCode(201);

            long id = jdbc.queryForObject("SELECT id FROM leads", Long.class);
            byte[] phone = jdbc.queryForObject("SELECT phone_enc FROM leads WHERE id = ?", byte[].class, id);
            assertThat(phone[0]).isEqualTo(PiiCipher.VERSION);
            assertThat(PiiCipher.keyIdOf(phone)).isEqualTo(1);

            int port = ((ServletWebServerApplicationContext) defaults).getWebServer().getPort();
            RestAssured.given().port(port).auth().oauth2(token).get("/api/admin/leads/{id}/contact", id)
                .then().statusCode(200).body("name", equalTo("홍길동")).body("phone", equalTo("01088880001"))
                .body("email", equalTo("kim@example.com"));
            // 형식 1(키 id 1)도 현재 키라 재암호화할 것이 없다.
            RestAssured.given().port(port).auth().oauth2(token).post("/api/admin/pii/reencrypt?dryRun=true")
                .then().statusCode(200).body("remaining", equalTo(0)).body("currentKeyId", equalTo(1));
        }
    }
}
