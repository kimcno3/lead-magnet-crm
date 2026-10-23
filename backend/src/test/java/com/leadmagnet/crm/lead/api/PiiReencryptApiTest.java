package com.leadmagnet.crm.lead.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import com.leadmagnet.crm.auth.domain.Operator;
import com.leadmagnet.crm.auth.domain.OperatorRepository;
import com.leadmagnet.crm.common.crypto.PiiCipher;
import com.leadmagnet.crm.common.crypto.PiiHasher;
import com.leadmagnet.crm.common.crypto.PiiKeyCheck;
import com.leadmagnet.crm.common.web.PublicPort;
import com.leadmagnet.crm.lead.application.PiiReencryptionService;
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
import io.restassured.specification.RequestSpecification;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 암호화 키 교체의 재암호화 (ADR 0022). 컨텍스트는 현재 키 id 2, 이전 키 id 1로 뜬다(@IntegrationTest). 신청은 공개 API로 넣은 뒤
 * 암호문을 이전 키(형식 2·키 id 1, 이름 하나는 v0.3.0 형식 1)로 바꿔 교체 직전 상태를 만든다. 배치는 2건이다.
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class PiiReencryptApiTest {

    static final String HTML = """
        <form><input name="name"><input name="phone" type="tel"><input name="email" type="email"></form>
        """;
    static final String MARKER = "lead-magnet-crm:pii-key-check";
    static final String MARKER_COLUMN = "pii_key_check";
    static final String[] PHONES = {"010-6666-0001", "010-6666-0002", "010-6666-0003"};
    static final byte[] PREVIOUS = Base64.getDecoder().decode(IntegrationTest.TEST_PREVIOUS_ENC_KEY);

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
    PiiCipher cipher;

    @Autowired
    PiiHasher hasher;

    @Autowired
    PiiKeyCheck keyCheck;

    @Autowired
    PlatformTransactionManager transactionManager;

    final PiiCipher previousCipher = new PiiCipher(1, Map.of(1, PREVIOUS));
    String token;

    @BeforeEach
    void setUp() {
        RestAssured.port = adminPort;
        DatabaseCleaner.truncateAll(jdbc);
        operators.save(new Operator("owner@example.com", passwordEncoder.encode("correct-horse-battery"), Instant.now()));
        token = AdminLogin.accessToken("owner@example.com", "correct-horse-battery");
        AdminFixtures.createPublishedForm(token, "form-a", HTML);
        for (int i = 0; i < PHONES.length; i++) {
            PublicOrigin.given(publicPort).contentType(ContentType.JSON)
                .body(Map.of("fields", Map.of("name", "홍길동" + i, "phone", PHONES[i], "email", "user" + i + "@example.com"),
                    "consent", true, "ft", FormTokens.mature(publicPort, clock, "form-a")))
                .post("/api/public/forms/form-a/submissions").then().statusCode(201);
        }
        // 교체 직전: 모든 암호문과 표식이 이전 키(id 1). 첫 신청의 이름은 v0.3.0 형식 1이다.
        List<Long> ids = ids();
        for (int i = 0; i < ids.size(); i++) {
            byte[] name = i == 0 ? LegacyPiiCiphertext.v1(PREVIOUS, "홍길동0", "name")
                : previousCipher.encrypt("홍길동" + i, "name");
            jdbc.update("UPDATE leads SET name_enc = ?, phone_enc = ?, email_enc = ? WHERE id = ?", name,
                previousCipher.encrypt(digits(PHONES[i]), "phone"), previousCipher.encrypt("user" + i + "@example.com",
                    "email"), ids.get(i));
        }
        jdbc.update("UPDATE pii_key_check SET enc_check = ? WHERE id = 1", previousCipher.encrypt(MARKER, MARKER_COLUMN));
    }

    @AfterEach
    void restore() {
        clock.reset();
        jdbc.update("DELETE FROM pii_key_check");
        keyCheck.verify();
    }

    static String digits(String phone) {
        return phone.replaceAll("\\D", "");
    }

    List<Long> ids() {
        return jdbc.queryForList("SELECT id FROM leads ORDER BY id", Long.class);
    }

    RequestSpecification authed() {
        return RestAssured.given().auth().oauth2(token);
    }

    List<Map<String, Object>> auditRows() {
        return jdbc.queryForList("SELECT operator_id, target_type, detail::text AS detail, "
            + "(detail->>'count')::int AS count, (detail->>'remaining')::int AS remaining, "
            + "(detail->>'unreadable')::int AS unreadable, (detail->>'completed')::boolean AS completed, "
            + "(detail->>'interrupted')::boolean AS interrupted FROM audit_logs WHERE action = 'pii.reencrypt' ORDER BY id");
    }

    byte[] column(String column, long id) {
        return jdbc.queryForObject("SELECT " + column + " FROM leads WHERE id = ?", byte[].class, id);
    }

    @Test
    void dryRun은_남은_건수만_주고_아무것도_바꾸거나_기록하지_않는다() {
        byte[] before = column("phone_enc", ids().getFirst());

        authed().post("/api/admin/pii/reencrypt?dryRun=true").then().statusCode(200)
            .body("dryRun", equalTo(true)).body("reencrypted", equalTo(0)).body("remaining", equalTo(4))
            .body("currentKeyId", equalTo(2)).body("interrupted", equalTo(false));

        assertThat(column("phone_enc", ids().getFirst())).isEqualTo(before);
        assertThat(keyCheck.markerOnCurrentKey()).isFalse();
        assertThat(auditRows()).isEmpty();
    }

    @Test
    void 실행하면_모든_암호문과_표식을_현재_키로_바꾸고_남은_0과_감사를_한_줄_남긴다(CapturedOutput output) {
        List<String> hashesBefore = jdbc.queryForList("SELECT phone_hash FROM leads ORDER BY id", String.class);

        authed().post("/api/admin/pii/reencrypt").then().statusCode(200)
            .body("dryRun", equalTo(false)).body("reencrypted", equalTo(3)).body("unreadable", equalTo(0))
            .body("remaining", equalTo(0)).body("interrupted", equalTo(false));

        List<Long> ids = ids();
        for (int i = 0; i < ids.size(); i++) {
            long id = ids.get(i);
            for (String c : List.of("name_enc", "phone_enc", "email_enc")) {
                assertThat(PiiCipher.keyIdOf(column(c, id))).as(c).isEqualTo(2);
            }
            assertThat(cipher.decrypt(column("name_enc", id), "name")).isEqualTo("홍길동" + i);
            assertThat(cipher.decrypt(column("phone_enc", id), "phone")).isEqualTo(digits(PHONES[i]));
            assertThat(cipher.decrypt(column("email_enc", id), "email")).isEqualTo("user" + i + "@example.com");
        }
        assertThat(jdbc.queryForList("SELECT phone_hash FROM leads ORDER BY id", String.class)).isEqualTo(hashesBefore);
        assertThat(keyCheck.markerOnCurrentKey()).isTrue();
        assertThat(auditRows()).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("count", 3).containsEntry("remaining", 0).containsEntry("unreadable", 0)
                .containsEntry("completed", true).containsEntry("interrupted", false).containsEntry("target_type", "lead");
            assertThat(row.get("operator_id")).isNotNull();
            assertThat((String) row.get("detail")).doesNotContain(digits(PHONES[0])).doesNotContain("user0@example.com");
        });
        // 남은 0이면 이전 키를 지워도 기동한다(표식이 현재 키다).
        PiiCipher currentOnly = new PiiCipher(2, Map.of(2, Base64.getDecoder().decode(IntegrationTest.TEST_ENC_KEY)));
        assertThat(new PiiKeyCheck(jdbc, currentOnly, hasher).verify()).isEqualTo(PiiKeyCheck.Result.VERIFIED);
        // 다시 실행하면 할 일이 없고 그래도 기록한다.
        authed().post("/api/admin/pii/reencrypt").then().statusCode(200)
            .body("reencrypted", equalTo(0)).body("remaining", equalTo(0));
        assertThat(auditRows()).hasSize(2);
        assertThat(output.getAll()).doesNotContain(digits(PHONES[0])).doesNotContain("user0@example.com")
            .doesNotContain(IntegrationTest.TEST_PREVIOUS_ENC_KEY).doesNotContain(IntegrationTest.TEST_ENC_KEY);
    }

    @Test
    void 재암호화가_끝나기_전에_이전_키를_지우면_기동이_실패한다() {
        PiiCipher currentOnly = new PiiCipher(2, Map.of(2, Base64.getDecoder().decode(IntegrationTest.TEST_ENC_KEY)));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new PiiKeyCheck(jdbc, currentOnly, hasher).verify())
            .isInstanceOf(PiiKeyCheck.KeyMismatchException.class);
    }

    @Test
    void 파기된_신청은_건너뛴다() {
        long anonymized = ids().getLast();
        jdbc.update("UPDATE leads SET name_enc = NULL, phone_enc = NULL, email_enc = NULL, phone_hash = NULL, "
            + "email_hash = NULL, anonymized_at = now() WHERE id = ?", anonymized);

        authed().post("/api/admin/pii/reencrypt?dryRun=true").then().body("remaining", equalTo(3));
        authed().post("/api/admin/pii/reencrypt").then().statusCode(200)
            .body("reencrypted", equalTo(2)).body("remaining", equalTo(0));

        assertThat(column("phone_enc", anonymized)).isNull();
    }

    @Test
    void 풀_수_없는_신청은_건너뛰고_남은_건수에_남기며_표식은_이전_키로_둔다(CapturedOutput output) {
        long broken = ids().get(1);
        byte[] unknownKey = previousCipher.encrypt(digits(PHONES[1]), "phone");
        unknownKey[1] = 9;
        jdbc.update("UPDATE leads SET phone_enc = ? WHERE id = ?", unknownKey, broken);

        authed().post("/api/admin/pii/reencrypt").then().statusCode(200)
            .body("reencrypted", equalTo(2)).body("unreadable", equalTo(1)).body("remaining", equalTo(2));

        assertThat(column("phone_enc", broken)).isEqualTo(unknownKey);
        assertThat(keyCheck.markerOnCurrentKey()).isFalse();
        assertThat(auditRows()).singleElement().satisfies(row -> assertThat(row)
            .containsEntry("unreadable", 1).containsEntry("remaining", 2).containsEntry("completed", true));
        assertThat(output.getAll()).contains("id=" + broken).doesNotContain(digits(PHONES[1]));
    }

    @Test
    void 다른_실행이_잠금을_쥐고_있으면_아무것도_처리하지_않고_interrupted를_준다() throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> holder = pool.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(s -> {
                jdbc.queryForObject("SELECT pg_advisory_xact_lock(?)", Object.class, PiiReencryptionService.LOCK_KEY);
                locked.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            authed().post("/api/admin/pii/reencrypt").then().statusCode(200)
                .body("reencrypted", equalTo(0)).body("interrupted", equalTo(true)).body("remaining", equalTo(4));
            assertThat(auditRows()).singleElement().satisfies(row -> assertThat(row)
                .containsEntry("interrupted", true).containsEntry("count", 0));
            assertThat(keyCheck.markerOnCurrentKey()).isFalse();

            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void 토큰이_없으면_401이다() {
        RestAssured.given().post("/api/admin/pii/reencrypt").then().statusCode(401).body("code", equalTo("unauthorized"));
        assertThat(keyCheck.markerOnCurrentKey()).isFalse();
    }

    @Test
    void 파기_배치와_다른_잠금_키를_쓴다() {
        assertThat(PiiReencryptionService.LOCK_KEY).isNotEqualTo(0x4C4D_5245_5441_494EL);
    }
}
