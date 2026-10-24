package com.leadmagnet.crm.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/** 기동 시 @ConfigurationProperties 바인딩이 실패하면 컨텍스트가 뜨지 않는다. 키 규칙을 확인한다. */
class PiiCryptoPropertiesTest {

    static final String ENC = Base64.getEncoder().encodeToString(PiiCipherTest.KEY);
    static final String HMAC = Base64.getEncoder().encodeToString(PiiCipherTest.OTHER_KEY);

    @Test
    void base64_32바이트_키_두_개면_바인딩된다() {
        PiiCryptoProperties properties = bind(ENC, HMAC);

        assertThat(properties.encKeyBytes()).isEqualTo(PiiCipherTest.KEY);
        assertThat(properties.hmacKeyBytes()).isEqualTo(PiiCipherTest.OTHER_KEY);
    }

    @Test
    void 암호화_키가_없으면_기동이_실패한다() {
        assertThatThrownBy(() -> bind(null, HMAC)).isInstanceOf(BindException.class)
            .rootCause().hasMessageContaining("LEAD_ENC_KEY");
        assertThatThrownBy(() -> bind("", HMAC)).isInstanceOf(BindException.class);
    }

    @Test
    void HMAC_키가_없으면_기동이_실패한다() {
        assertThatThrownBy(() -> bind(ENC, null)).isInstanceOf(BindException.class)
            .rootCause().hasMessageContaining("LEAD_HMAC_KEY");
    }

    @Test
    void 길이가_32바이트가_아니면_기동이_실패하고_키_값은_메시지에_싣지_않는다() {
        String short16 = Base64.getEncoder().encodeToString(new byte[16]);
        String long48 = Base64.getEncoder().encodeToString(new byte[48]);

        assertThatThrownBy(() -> bind(short16, HMAC)).isInstanceOf(BindException.class)
            .rootCause().hasMessageContaining("LEAD_ENC_KEY").hasMessageContaining("32바이트")
            .hasMessageNotContaining(short16);
        assertThatThrownBy(() -> bind(ENC, long48)).isInstanceOf(BindException.class)
            .rootCause().hasMessageContaining("LEAD_HMAC_KEY");
    }

    @Test
    void base64가_아니면_기동이_실패한다() {
        assertThatThrownBy(() -> bind("change-me!!", HMAC)).isInstanceOf(BindException.class)
            .rootCause().hasMessageContaining("LEAD_ENC_KEY").hasMessageNotContaining("change-me!!");
    }

    @Test
    void 두_키가_같으면_기동이_실패한다() {
        assertThatThrownBy(() -> bind(ENC, ENC)).isInstanceOf(BindException.class)
            .rootCause().hasMessageContaining("달라야");
    }

    static final byte[] PREV1 = filled(0x50);
    static final byte[] PREV2 = filled(0x70);
    static final byte[] HMAC_PREV = filled(0x90);
    static final String PREV1_B64 = Base64.getEncoder().encodeToString(PREV1);
    static final String PREV2_B64 = Base64.getEncoder().encodeToString(PREV2);
    static final String HMAC_PREV_B64 = Base64.getEncoder().encodeToString(HMAC_PREV);

    @Test
    void 키_id를_주지_않으면_1이고_이전_키는_없다() {
        PiiCryptoProperties properties = bind(ENC, HMAC);

        assertThat(properties.currentKeyId()).isEqualTo(1);
        assertThat(properties.encKeyRing()).containsOnlyKeys(1);
        assertThat(properties.hmacPreviousKeyBytes()).isEmpty();
        assertThat(properties.batchSize()).isEqualTo(500);
    }

    @Test
    void 현재_키_id와_이전_키_목록을_읽는다() {
        PiiCryptoProperties properties = bind(Map.of("enc-key-id", "3",
            "enc-previous-keys", " 1:" + PREV1_B64 + " , 2:" + PREV2_B64 + " ", "hmac-previous-key", HMAC_PREV_B64));

        assertThat(properties.currentKeyId()).isEqualTo(3);
        assertThat(properties.encKeyRing()).containsOnlyKeys(1, 2, 3);
        assertThat(properties.encKeyRing().get(3)).isEqualTo(PiiCipherTest.KEY);
        assertThat(properties.encKeyRing().get(1)).isEqualTo(PREV1);
        assertThat(properties.encKeyRing().get(2)).isEqualTo(PREV2);
        assertThat(properties.hmacPreviousKeyBytes()).contains(HMAC_PREV);
        assertThat(properties.candidates()).hasSize(5);
        assertThat(properties.toString()).doesNotContain(PREV1_B64).doesNotContain(HMAC_PREV_B64);
    }

    @Test
    void 키_id가_1에서_255_밖이거나_숫자가_아니면_기동이_실패한다() {
        for (String id : new String[] {"0", "256", "-1"}) {
            assertThatThrownBy(() -> bind(Map.of("enc-key-id", id))).isInstanceOf(BindException.class)
                .rootCause().hasMessageContaining("LEAD_ENC_KEY_ID");
        }
        assertThatThrownBy(() -> bind(Map.of("enc-key-id", "abc"))).isInstanceOf(BindException.class)
            .rootCause().hasMessageContaining("LEAD_ENC_KEY_ID는 1~255여야 합니다");
        assertThatThrownBy(() -> bind(Map.of("enc-key-id", "1.5"))).isInstanceOf(BindException.class)
            .rootCause().hasMessageContaining("LEAD_ENC_KEY_ID는 1~255여야 합니다");
    }

    @Test
    void 이전_키_형식이_틀리면_기동이_실패하고_키_값은_메시지에_싣지_않는다() {
        String[] bad = {PREV1_B64, "x:" + PREV1_B64, "0:" + PREV1_B64, "256:" + PREV1_B64, "1:",
            "1:" + Base64.getEncoder().encodeToString(new byte[16]), "1:not-base64!!", "1:" + PREV1_B64 + ",,"};
        for (String value : bad) {
            assertThatThrownBy(() -> bind(Map.of("enc-key-id", "2", "enc-previous-keys", value)))
                .as(value).isInstanceOf(BindException.class)
                .rootCause().hasMessageContaining("LEAD_ENC_PREVIOUS_KEYS").hasMessageNotContaining(PREV1_B64);
        }
    }

    @Test
    void 이전_키_id가_중복되면_기동이_실패한다() {
        assertThatThrownBy(() -> bind(Map.of("enc-key-id", "3",
            "enc-previous-keys", "1:" + PREV1_B64 + ",1:" + PREV2_B64)))
            .isInstanceOf(BindException.class).rootCause().hasMessageContaining("중복");
    }

    @Test
    void 이전_키_id가_현재_키_id와_같으면_기동이_실패한다() {
        assertThatThrownBy(() -> bind(Map.of("enc-previous-keys", "1:" + PREV1_B64)))
            .isInstanceOf(BindException.class).rootCause().hasMessageContaining("LEAD_ENC_KEY_ID")
            .hasMessageNotContaining(PREV1_B64);
    }

    @Test
    void 같은_키_값이_두_번_쓰이면_기동이_실패한다() {
        // 이전 암호화 키가 현재 암호화 키와 같은 값
        assertThatThrownBy(() -> bind(Map.of("enc-key-id", "2", "enc-previous-keys", "1:" + ENC)))
            .isInstanceOf(BindException.class).rootCause().hasMessageContaining("달라야").hasMessageNotContaining(ENC);
        // 이전 HMAC 키가 현재 HMAC 키와 같은 값
        assertThatThrownBy(() -> bind(Map.of("hmac-previous-key", HMAC)))
            .isInstanceOf(BindException.class).rootCause().hasMessageContaining("달라야");
        // 이전 HMAC 키가 암호화 키(이전 포함)와 같은 값
        assertThatThrownBy(() -> bind(Map.of("enc-key-id", "2", "enc-previous-keys", "1:" + PREV1_B64,
            "hmac-previous-key", PREV1_B64))).isInstanceOf(BindException.class).rootCause().hasMessageContaining("달라야");
    }

    @Test
    void 이전_HMAC_키_형식이_틀리면_기동이_실패한다() {
        assertThatThrownBy(() -> bind(Map.of("hmac-previous-key", "short")))
            .isInstanceOf(BindException.class).rootCause().hasMessageContaining("LEAD_HMAC_PREVIOUS_KEY")
            .hasMessageNotContaining("short");
    }

    @Test
    void 빈_이전_키_설정은_없는_것으로_본다() {
        PiiCryptoProperties properties = bind(Map.of("enc-key-id", " ", "enc-previous-keys", " ", "hmac-previous-key", ""));

        assertThat(properties.currentKeyId()).isEqualTo(1);
        assertThat(properties.encKeyRing()).containsOnlyKeys(1);
        assertThat(properties.hmacPreviousKeyBytes()).isEmpty();
    }

    @Test
    void 배치_크기는_1_이상이어야_한다() {
        assertThatThrownBy(() -> bind(Map.of("batch-size", "0"))).isInstanceOf(BindException.class);
    }

    private static byte[] filled(int start) {
        byte[] bytes = new byte[32];
        for (int i = 0; i < 32; i++) {
            bytes[i] = (byte) (start + i);
        }
        return bytes;
    }

    /** ENC·HMAC에 더해 extra(app.pii. 뒤 이름 → 값)를 준다. */
    private static PiiCryptoProperties bind(Map<String, String> extra) {
        Map<String, String> values = new HashMap<>();
        values.put("app.pii.enc-key", ENC);
        values.put("app.pii.hmac-key", HMAC);
        extra.forEach((k, v) -> values.put("app.pii." + k, v));
        return new Binder(new MapConfigurationPropertySource(values)).bindOrCreate("app.pii", PiiCryptoProperties.class);
    }

    private static PiiCryptoProperties bind(String enc, String hmac) {
        Map<String, String> values = new HashMap<>();
        if (enc != null) {
            values.put("app.pii.enc-key", enc);
        }
        if (hmac != null) {
            values.put("app.pii.hmac-key", hmac);
        }
        return new Binder(new MapConfigurationPropertySource(values)).bindOrCreate("app.pii", PiiCryptoProperties.class);
    }
}
