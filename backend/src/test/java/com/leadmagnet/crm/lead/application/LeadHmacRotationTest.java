package com.leadmagnet.crm.lead.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.equalTo;

import com.leadmagnet.crm.LeadMagnetApplication;
import com.leadmagnet.crm.auth.domain.Operator;
import com.leadmagnet.crm.auth.domain.OperatorRepository;
import com.leadmagnet.crm.common.crypto.PiiCipher;
import com.leadmagnet.crm.common.crypto.PiiHashRotation;
import com.leadmagnet.crm.common.crypto.PiiHasher;
import com.leadmagnet.crm.common.crypto.PiiKeyCheck;
import com.leadmagnet.crm.common.crypto.PiiKeyCheck.Result;
import com.leadmagnet.crm.common.crypto.PiiKeyRotationException;
import com.leadmagnet.crm.common.web.PublicPort;
import com.leadmagnet.crm.support.AdminFixtures;
import com.leadmagnet.crm.support.AdminLogin;
import com.leadmagnet.crm.support.DatabaseCleaner;
import com.leadmagnet.crm.support.FormTokens;
import com.leadmagnet.crm.support.IntegrationTest;
import com.leadmagnet.crm.support.MutableClock;
import com.leadmagnet.crm.support.PublicOrigin;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * HMAC 키(LEAD_HMAC_KEY) 교체 (ADR 0022). LEAD_HMAC_PREVIOUS_KEY가 있으면 기동 중(웹 서버가 요청을 받기 전) 모든 신청의
 * 중복 판정 해시를 새 키로 다시 계산하고 표식 행을 마지막에 바꾼다. 컨텍스트의 HMAC 키(TEST_HMAC_KEY)로 만든 신청을
 * "이전 키" 데이터로 보고 NEW 키로 교체한다. 배치는 2건(@IntegrationTest)이라 신청 3건이면 keyset 페이지를 넘는다.
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class LeadHmacRotationTest {

    static final String HTML = """
        <form><input name="name"><input name="phone" type="tel"><input name="email" type="email"></form>
        """;
    static final String NEW_KEY = "oKGio6SlpqeoqaqrrK2ur7CxsrO0tba3uLm6u7y9vr8=";
    static final String[] PHONES = {"010-5555-0001", "010-5555-0002", "010-5555-0003"};

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
    PiiCipher cipher;

    @Autowired
    PiiHasher oldHasher;

    @Autowired
    List<PiiHashRotation> rotations;

    @Autowired
    PostgreSQLContainer<?> postgres;

    final PiiHasher newHasher = new PiiHasher(Base64.getDecoder().decode(NEW_KEY));

    @BeforeEach
    void setUp() {
        RestAssured.port = adminPort;
        DatabaseCleaner.truncateAll(jdbc);
        operators.save(new Operator("owner@example.com", passwordEncoder.encode("correct-horse-battery"), Instant.now()));
        String token = AdminLogin.accessToken("owner@example.com", "correct-horse-battery");
        AdminFixtures.createPublishedForm(token, "form-a", HTML);
        for (int i = 0; i < PHONES.length; i++) {
            submit(publicPort, PHONES[i], "user" + i + "@example.com").then().statusCode(201);
        }
    }

    @AfterEach
    void restore() {
        clock.reset();
        // 공유 컨텍스트의 키로 표식을 되돌린다. 신청은 다음 테스트가 비운다.
        jdbc.update("DELETE FROM pii_key_check");
        keyCheck.verify();
    }

    io.restassured.response.Response submit(PublicPort port, String phone, String email) {
        return PublicOrigin.given(port).contentType(ContentType.JSON)
            .body(Map.of("fields", Map.of("name", "홍길동", "phone", phone, "email", email), "consent", true,
                "ft", FormTokens.mature(publicPort, clock, "form-a")))
            .post("/api/public/forms/form-a/submissions");
    }

    PiiKeyCheck rotating() {
        return new PiiKeyCheck(jdbc, cipher, newHasher, oldHasher, rotations);
    }

    List<Map<String, Object>> hashes() {
        return jdbc.queryForList("SELECT id, phone_hash, email_hash FROM leads ORDER BY id");
    }

    String markerHmac() {
        return jdbc.queryForObject("SELECT hmac_check FROM pii_key_check WHERE id = 1", String.class);
    }

    static String digits(String phone) {
        return phone.replaceAll("\\D", "");
    }

    @Test
    void 이전_키로_만든_해시를_새_키로_바꾸고_표식도_바꾼다(CapturedOutput output) {
        // 파기된 신청(암호문·해시 NULL)은 건너뛴다.
        long anonymized = jdbc.queryForObject("SELECT max(id) FROM leads", Long.class);
        jdbc.update("UPDATE leads SET name_enc = NULL, phone_enc = NULL, email_enc = NULL, phone_hash = NULL, "
            + "email_hash = NULL, anonymized_at = now() WHERE id = ?", anonymized);

        assertThat(rotating().verify()).isEqualTo(Result.HMAC_ROTATED);

        List<Map<String, Object>> rows = hashes();
        for (int i = 0; i < 2; i++) {
            assertThat(rows.get(i)).containsEntry("phone_hash", newHasher.hashPhone(digits(PHONES[i])))
                .containsEntry("email_hash", newHasher.hashEmail("user" + i + "@example.com"));
        }
        assertThat(rows.get(2)).containsEntry("phone_hash", null).containsEntry("email_hash", null);
        assertThat(markerHmac()).isEqualTo(newHasher.hash("lead-magnet-crm:pii-key-check"));
        // 다시 기동해도(이전 키를 아직 안 지웠어도) 할 일이 없다. 이전 키만 가진 설정은 이제 기동하지 못한다.
        assertThat(rotating().verify()).isEqualTo(Result.VERIFIED);
        assertThatThrownBy(keyCheck::verify).isInstanceOf(PiiKeyCheck.KeyMismatchException.class)
            .hasMessageContaining("LEAD_HMAC_KEY");
        assertThat(output.getAll()).doesNotContain(digits(PHONES[0])).doesNotContain("user0@example.com")
            .doesNotContain(NEW_KEY).doesNotContain(IntegrationTest.TEST_HMAC_KEY);
    }

    @Test
    void 중간에_실패해도_다시_기동하면_바뀐_행은_건너뛰고_이어서_끝낸다(CapturedOutput output) {
        long third = jdbc.queryForObject("SELECT max(id) FROM leads", Long.class);
        byte[] original = jdbc.queryForObject("SELECT phone_enc FROM leads WHERE id = ?", byte[].class, third);
        byte[] broken = original.clone();
        broken[broken.length - 1] ^= 0x01;
        jdbc.update("UPDATE leads SET phone_enc = ? WHERE id = ?", broken, third);
        String oldMarker = markerHmac();

        assertThatThrownBy(rotating()::verify).isInstanceOf(PiiKeyRotationException.class)
            .hasMessageContaining("신청 id=" + third).hasMessageNotContaining(digits(PHONES[2]));

        // 첫 배치(2건)는 커밋됐고, 셋째 행과 표식은 아직 이전 키다.
        List<Map<String, Object>> rows = hashes();
        assertThat(rows.get(0)).containsEntry("phone_hash", newHasher.hashPhone(digits(PHONES[0])));
        assertThat(rows.get(1)).containsEntry("phone_hash", newHasher.hashPhone(digits(PHONES[1])));
        assertThat(rows.get(2)).containsEntry("phone_hash", oldHasher.hashPhone(digits(PHONES[2])));
        assertThat(markerHmac()).isEqualTo(oldMarker);
        // 이전 키를 빼고 기동하면 표식이 이전 키라 막힌다(섞인 상태로 요청을 받지 않는다).
        assertThatThrownBy(() -> new PiiKeyCheck(jdbc, cipher, newHasher).verify())
            .isInstanceOf(PiiKeyCheck.KeyMismatchException.class).hasMessageContaining("LEAD_HMAC_PREVIOUS_KEY");

        jdbc.update("UPDATE leads SET phone_enc = ? WHERE id = ?", original, third);
        assertThat(rotating().verify()).isEqualTo(Result.HMAC_ROTATED);

        rows = hashes();
        for (int i = 0; i < PHONES.length; i++) {
            assertThat(rows.get(i)).containsEntry("phone_hash", newHasher.hashPhone(digits(PHONES[i])))
                .containsEntry("email_hash", newHasher.hashEmail("user" + i + "@example.com"));
        }
        assertThat(markerHmac()).isEqualTo(newHasher.hash("lead-magnet-crm:pii-key-check"));
        assertThat(output.getAll()).doesNotContain(digits(PHONES[2]));
    }

    @Test
    void 같은_폼에서_새_해시가_겹치면_중단하고_표식을_바꾸지_않는다(CapturedOutput output) {
        List<Long> ids = jdbc.queryForList("SELECT id FROM leads ORDER BY id", Long.class);
        long a = ids.get(0);
        long b = ids.get(1);
        // a는 이미 새 키로 바뀐 상태, b는 a와 같은 번호인데 이전 키 해시(이론상 생기지 않는 상태를 만든다).
        jdbc.update("UPDATE leads SET phone_hash = ? WHERE id = ?", newHasher.hashPhone(digits(PHONES[0])), a);
        jdbc.update("UPDATE leads SET phone_enc = (SELECT phone_enc FROM leads WHERE id = ?), phone_hash = ? WHERE id = ?",
            a, oldHasher.hashPhone(digits(PHONES[0])), b);
        String oldMarker = markerHmac();

        assertThatThrownBy(rotating()::verify).isInstanceOf(PiiKeyRotationException.class)
            .hasMessageContaining("겹치").hasMessageContaining("신청 id=" + b).hasMessageContaining(String.valueOf(a))
            .hasMessageNotContaining(digits(PHONES[0]));

        assertThat(markerHmac()).isEqualTo(oldMarker);
        assertThat(jdbc.queryForObject("SELECT phone_hash FROM leads WHERE id = ?", String.class, b))
            .isEqualTo(oldHasher.hashPhone(digits(PHONES[0])));
        assertThat(output.getAll()).doesNotContain(digits(PHONES[0]));
    }

    @Test
    void 해시가_이전_키로도_새_키로도_안_맞는_행이_있으면_중단한다() {
        long first = jdbc.queryForObject("SELECT min(id) FROM leads", Long.class);
        jdbc.update("UPDATE leads SET email_hash = ? WHERE id = ?", "0".repeat(64), first);

        assertThatThrownBy(rotating()::verify).isInstanceOf(PiiKeyRotationException.class)
            .hasMessageContaining("신청 id=" + first);
        assertThat(markerHmac()).isEqualTo(oldHasher.hash("lead-magnet-crm:pii-key-check"));
    }

    @Test
    void 이전_키_없이_새_키만_주면_기동이_실패하고_해시는_그대로다() {
        List<Map<String, Object>> before = hashes();

        assertThatThrownBy(() -> new PiiKeyCheck(jdbc, cipher, newHasher, (PiiHasher) null, rotations).verify())
            .isInstanceOf(PiiKeyCheck.KeyMismatchException.class).hasMessageContaining("LEAD_HMAC_KEY");

        assertThat(hashes()).isEqualTo(before);
    }

    @Test
    void 이전_키로_만든_신청은_교체된_앱에서_같은_연락처로_다시_신청하면_409다() {
        AtomicReference<String> markerAtWebStart = new AtomicReference<>();
        SpringApplicationBuilder app = new SpringApplicationBuilder(LeadMagnetApplication.class)
            .properties("DB_URL=" + postgres.getJdbcUrl(), "DB_USERNAME=" + postgres.getUsername(),
                "DB_PASSWORD=" + postgres.getPassword(),
                "JWT_SECRET=test-only-jwt-secret-not-for-production-0123456789",
                "LEAD_ENC_KEY=" + IntegrationTest.TEST_ENC_KEY, "LEAD_ENC_KEY_ID=2",
                "LEAD_ENC_PREVIOUS_KEYS=1:" + IntegrationTest.TEST_PREVIOUS_ENC_KEY,
                "LEAD_HMAC_KEY=" + NEW_KEY, "LEAD_HMAC_PREVIOUS_KEY=" + IntegrationTest.TEST_HMAC_KEY,
                "FORM_TOKEN_KEY=" + IntegrationTest.TEST_FORM_TOKEN_KEY,
                "ADMIN_PORT=0", "PUBLIC_PORT=0", "app.retention.enabled=false",
                "app.public.rate-limit.ip-per-minute=100000", "app.public.rate-limit.visitor-per-minute=100000",
                "spring.main.banner-mode=off")
            .listeners((ApplicationListener<WebServerInitializedEvent>) e -> markerAtWebStart.compareAndSet(null,
                markerHmac()));

        try (ConfigurableApplicationContext rotated = app.run()) {
            // 웹 서버가 요청을 받기 시작할 때 이미 교체가 끝나 있다.
            assertThat(markerAtWebStart.get()).isEqualTo(newHasher.hash("lead-magnet-crm:pii-key-check"));
            PublicPort rotatedPort = rotated.getBean(PublicPort.class);

            submit(rotatedPort, PHONES[0], "fresh@example.com").then().statusCode(409)
                .body("code", equalTo("duplicate_submission"));
            submit(rotatedPort, "010-9999-0000", "user1@example.com").then().statusCode(409);
            submit(rotatedPort, "010-9999-0000", "fresh@example.com").then().statusCode(201);
        }
    }
}
