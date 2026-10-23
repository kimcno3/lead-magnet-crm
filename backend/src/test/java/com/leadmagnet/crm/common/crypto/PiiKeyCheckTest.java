package com.leadmagnet.crm.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.leadmagnet.crm.LeadMagnetApplication;
import com.leadmagnet.crm.common.crypto.PiiKeyCheck.Result;
import com.leadmagnet.crm.support.IntegrationTest;
import com.leadmagnet.crm.support.LegacyPiiCiphertext;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.NestedExceptionUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.springframework.jdbc.core.JdbcTemplate;

/** 기동 시 현재 키가 기존 데이터를 만든 키와 같은지 확인한다 (ADR 0015). */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class PiiKeyCheckTest {

    static final byte[] OTHER = new byte[32];

    static {
        for (int i = 0; i < 32; i++) {
            OTHER[i] = (byte) (0x80 + i);
        }
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PiiKeyCheck keyCheck;

    @Autowired
    PiiCipher cipher;

    @Autowired
    PiiHasher hasher;

    @Autowired
    PostgreSQLContainer<?> postgres;

    /**
     * 공유 컨텍스트의 표식과 같은 키 id(현재 2, 이전 1)에 바이트만 다른 현재 키. 키 id가 같아야 "모르는 키 id"가 아니라
     * GCM 태그 검사(키 바이트)로 실패하는지 본다.
     */
    static PiiCipher sameIdOtherBytes() {
        return new PiiCipher(2, Map.of(2, OTHER, 1, Base64.getDecoder().decode(IntegrationTest.TEST_PREVIOUS_ENC_KEY)));
    }

    void assertMarkerHasKeyId(int keyId) {
        byte[] enc = jdbc.queryForObject("SELECT enc_check FROM pii_key_check", byte[].class);
        assertThat(PiiCipher.keyIdOf(enc)).isEqualTo(keyId);
    }

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM pii_key_check");
    }

    @AfterEach
    void restore() {
        // 다른 테스트가 공유하는 컨텍스트의 키로 확인 행을 되돌린다.
        jdbc.update("DELETE FROM pii_key_check");
        keyCheck.verify();
    }

    @Test
    void 처음_기동하면_현재_키로_확인_행을_만든다() {
        assertThat(keyCheck.verify()).isEqualTo(Result.CREATED);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM pii_key_check", Integer.class)).isEqualTo(1);
        byte[] enc = jdbc.queryForObject("SELECT enc_check FROM pii_key_check", byte[].class);
        assertThat(cipher.decrypt(enc, PiiKeyCheck.COLUMN)).isEqualTo(PiiKeyCheck.MARKER);
        assertThat(jdbc.queryForObject("SELECT hmac_check FROM pii_key_check", String.class))
            .isEqualTo(hasher.hash(PiiKeyCheck.MARKER));
    }

    @Test
    void 같은_키로_다시_기동하면_통과하고_행을_바꾸지_않는다() {
        keyCheck.verify();
        byte[] before = jdbc.queryForObject("SELECT enc_check FROM pii_key_check", byte[].class);

        assertThat(keyCheck.verify()).isEqualTo(Result.VERIFIED);
        assertThat(jdbc.queryForObject("SELECT enc_check FROM pii_key_check", byte[].class)).isEqualTo(before);
    }

    @Test
    void 암호화_키가_다르면_기동이_실패하고_키를_출력하지_않는다(CapturedOutput output) {
        keyCheck.verify();
        assertMarkerHasKeyId(2);
        PiiKeyCheck otherEnc = new PiiKeyCheck(jdbc, sameIdOtherBytes(), hasher);

        assertThatThrownBy(otherEnc::verify).isInstanceOf(PiiKeyCheck.KeyMismatchException.class)
            .hasMessageContaining("LEAD_ENC_KEY가 기존 데이터를 암호화한 키와 다릅니다")
            .hasMessageNotContaining(IntegrationTest.TEST_ENC_KEY)
            .hasMessageNotContaining(Base64.getEncoder().encodeToString(OTHER));
        assertNoKeyMaterial(output);
    }

    @Test
    void 표식의_키_id를_모르는_키_설정이면_기동이_실패한다() {
        keyCheck.verify();
        assertMarkerHasKeyId(2);
        PiiKeyCheck unknownId = new PiiKeyCheck(jdbc, new PiiCipher(Base64.getDecoder().decode(IntegrationTest.TEST_ENC_KEY)),
            hasher);

        assertThatThrownBy(unknownId::verify).isInstanceOf(PiiKeyCheck.KeyMismatchException.class)
            .hasMessageContaining("LEAD_ENC_KEY");
    }

    @Test
    void HMAC_키가_다르면_기동이_실패하고_키를_출력하지_않는다(CapturedOutput output) {
        keyCheck.verify();
        PiiKeyCheck otherHmac = new PiiKeyCheck(jdbc, cipher, new PiiHasher(OTHER));

        assertThatThrownBy(otherHmac::verify).isInstanceOf(PiiKeyCheck.KeyMismatchException.class)
            .hasMessageContaining("LEAD_HMAC_KEY가 기존 데이터의 중복 판정 해시를 만든 키와 다릅니다")
            .hasMessageNotContaining(IntegrationTest.TEST_HMAC_KEY)
            .hasMessageNotContaining(Base64.getEncoder().encodeToString(OTHER));
        assertNoKeyMaterial(output);
    }

    @Test
    void 두_키가_모두_다르면_암호화_키부터_알린다() {
        keyCheck.verify();
        assertMarkerHasKeyId(2);
        PiiKeyCheck other = new PiiKeyCheck(jdbc, sameIdOtherBytes(), new PiiHasher(OTHER));

        assertThatThrownBy(other::verify).hasMessageContaining("LEAD_ENC_KEY");
    }

    @Test
    void 다른_키로_기동하면_웹_서버가_요청을_받기_전에_컨텍스트가_실패한다(CapturedOutput output) {
        keyCheck.verify();
        // 표식과 같은 키 id 2에 다른 키 바이트를 준다(키 id가 아니라 키 값 때문에 실패해야 한다).
        assertMarkerHasKeyId(2);
        AtomicBoolean webServerStarted = new AtomicBoolean(false);
        String otherKey = Base64.getEncoder().encodeToString(OTHER);
        SpringApplicationBuilder app = new SpringApplicationBuilder(LeadMagnetApplication.class)
            .properties("DB_URL=" + postgres.getJdbcUrl(),
                "DB_USERNAME=" + postgres.getUsername(),
                "DB_PASSWORD=" + postgres.getPassword(),
                "JWT_SECRET=test-only-jwt-secret-not-for-production-0123456789",
                "LEAD_ENC_KEY=" + otherKey, "LEAD_ENC_KEY_ID=2",
                "LEAD_ENC_PREVIOUS_KEYS=1:" + IntegrationTest.TEST_PREVIOUS_ENC_KEY,
                "LEAD_HMAC_KEY=" + IntegrationTest.TEST_HMAC_KEY,
                "FORM_TOKEN_KEY=" + IntegrationTest.TEST_FORM_TOKEN_KEY,
                "ADMIN_PORT=0", "PUBLIC_PORT=0", "spring.main.banner-mode=off")
            .listeners((ApplicationListener<WebServerInitializedEvent>) e -> webServerStarted.set(true));

        assertThatThrownBy(app::run).satisfies(e -> assertThat(NestedExceptionUtils.getMostSpecificCause(e))
            .isInstanceOf(PiiKeyCheck.KeyMismatchException.class).hasMessageContaining("LEAD_ENC_KEY"));
        // 커넥터가 요청을 받기 시작하기(WebServerInitializedEvent) 전에 멈췄다.
        assertThat(webServerStarted).isFalse();
        assertThat(output.getAll()).doesNotContain(otherKey).doesNotContain(IntegrationTest.TEST_HMAC_KEY);
    }

    static PiiCipher previousOnly() {
        return new PiiCipher(1, Map.of(1, Base64.getDecoder().decode(IntegrationTest.TEST_PREVIOUS_ENC_KEY)));
    }

    void markerEnc(byte[] enc) {
        jdbc.update("UPDATE pii_key_check SET enc_check = ? WHERE id = 1", enc);
    }

    @Test
    void 이전_키로_만든_표식이면_통과하고_현재_키가_아니라고_알린다() {
        keyCheck.verify();
        markerEnc(previousOnly().encrypt(PiiKeyCheck.MARKER, PiiKeyCheck.COLUMN));

        assertThat(keyCheck.verify()).isEqualTo(Result.VERIFIED);
        assertThat(keyCheck.markerOnCurrentKey()).isFalse();
    }

    @Test
    void v030의_형식1_표식도_이전_키_id_1로_읽어_통과한다() {
        keyCheck.verify();
        markerEnc(LegacyPiiCiphertext.v1(Base64.getDecoder().decode(IntegrationTest.TEST_PREVIOUS_ENC_KEY),
            PiiKeyCheck.MARKER, PiiKeyCheck.COLUMN));

        assertThat(keyCheck.verify()).isEqualTo(Result.VERIFIED);
    }

    @Test
    void 이전_키를_설정에서_지우면_이전_키로_남은_표식_때문에_기동이_실패한다() {
        keyCheck.verify();
        markerEnc(previousOnly().encrypt(PiiKeyCheck.MARKER, PiiKeyCheck.COLUMN));
        PiiCipher currentOnly = new PiiCipher(2, Map.of(2, Base64.getDecoder().decode(IntegrationTest.TEST_ENC_KEY)));

        assertThatThrownBy(() -> new PiiKeyCheck(jdbc, currentOnly, hasher).verify())
            .isInstanceOf(PiiKeyCheck.KeyMismatchException.class).hasMessageContaining("LEAD_ENC_KEY");
    }

    @Test
    void 표식을_현재_키로_다시_암호화한다() {
        keyCheck.verify();
        markerEnc(previousOnly().encrypt(PiiKeyCheck.MARKER, PiiKeyCheck.COLUMN));

        assertThat(keyCheck.reencryptMarker()).isTrue();

        assertThat(keyCheck.markerOnCurrentKey()).isTrue();
        byte[] enc = jdbc.queryForObject("SELECT enc_check FROM pii_key_check", byte[].class);
        assertThat(PiiCipher.keyIdOf(enc)).isEqualTo(2);
        assertThat(cipher.decrypt(enc, PiiKeyCheck.COLUMN)).isEqualTo(PiiKeyCheck.MARKER);
        assertThat(keyCheck.reencryptMarker()).isFalse();
    }

    @Test
    void 이전_HMAC_키를_줬는데_표식이_이전_키로도_현재_키로도_안_맞으면_기동이_실패한다() {
        keyCheck.verify();
        PiiHasher previous = new PiiHasher(Base64.getDecoder().decode(PiiCryptoPropertiesTest.HMAC_PREV_B64));
        PiiKeyCheck rotating = new PiiKeyCheck(jdbc, cipher, new PiiHasher(OTHER), previous, List.of());

        assertThatThrownBy(rotating::verify).isInstanceOf(PiiKeyCheck.KeyMismatchException.class)
            .hasMessageContaining("LEAD_HMAC_KEY");
        assertThat(jdbc.queryForObject("SELECT hmac_check FROM pii_key_check", String.class))
            .isEqualTo(hasher.hash(PiiKeyCheck.MARKER));
    }

    private static void assertNoKeyMaterial(CapturedOutput output) {
        assertThat(output.getAll()).doesNotContain(IntegrationTest.TEST_ENC_KEY)
            .doesNotContain(IntegrationTest.TEST_HMAC_KEY)
            .doesNotContain(Base64.getEncoder().encodeToString(OTHER))
            .doesNotContain(PiiKeyCheck.MARKER);
    }
}
