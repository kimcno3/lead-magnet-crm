package com.leadmagnet.crm.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.leadmagnet.crm.support.LegacyPiiCiphertext;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PiiCipherTest {

    static final byte[] KEY = new byte[32];
    static final byte[] OTHER_KEY = new byte[32];

    static {
        for (int i = 0; i < 32; i++) {
            KEY[i] = (byte) i;
            OTHER_KEY[i] = (byte) (i + 32);
        }
    }

    final PiiCipher cipher = new PiiCipher(KEY);

    @Test
    void 암호화한_값을_복호화하면_원문이_나온다() {
        byte[] sealed = cipher.encrypt("홍길동 010-1234-5678", "phone");

        assertThat(cipher.decrypt(sealed, "phone")).isEqualTo("홍길동 010-1234-5678");
    }

    @Test
    void 저장_형식은_버전2_키id_IV_암호문과_태그다() {
        byte[] plain = "01012345678".getBytes(StandardCharsets.UTF_8);
        byte[] sealed = cipher.encrypt("01012345678", "phone");

        assertThat(sealed[0]).isEqualTo(PiiCipher.VERSION);
        assertThat(PiiCipher.VERSION).isEqualTo((byte) 2);
        assertThat(sealed[1]).isEqualTo((byte) 1);
        assertThat(sealed).hasSize(2 + 12 + plain.length + 16);
        assertThat(new String(sealed, StandardCharsets.ISO_8859_1)).doesNotContain("01012345678");
    }

    @Test
    void 새로_쓰는_값은_현재_키_id로_쓴다() {
        PiiCipher rotated = new PiiCipher(7, Map.of(7, OTHER_KEY, 1, KEY));

        byte[] sealed = rotated.encrypt("a@example.com", "email");

        assertThat(sealed[1] & 0xFF).isEqualTo(7);
        assertThat(PiiCipher.keyIdOf(sealed)).isEqualTo(7);
        assertThat(rotated.isCurrent(sealed)).isTrue();
        assertThat(rotated.decrypt(sealed, "email")).isEqualTo("a@example.com");
    }

    @Test
    void 키_id_255도_쓸_수_있다() {
        PiiCipher max = new PiiCipher(255, Map.of(255, KEY));

        byte[] sealed = max.encrypt("x", "name");

        assertThat(PiiCipher.keyIdOf(sealed)).isEqualTo(255);
        assertThat(max.decrypt(sealed, "name")).isEqualTo("x");
    }

    @Test
    void 버전1_암호문은_키_id_1로_읽는다() {
        byte[] legacy = legacyV1(KEY, "01012345678", "phone");
        PiiCipher rotated = new PiiCipher(2, Map.of(2, OTHER_KEY, 1, KEY));

        assertThat(PiiCipher.keyIdOf(legacy)).isEqualTo(1);
        assertThat(cipher.decrypt(legacy, "phone")).isEqualTo("01012345678");
        assertThat(rotated.decrypt(legacy, "phone")).isEqualTo("01012345678");
        assertThat(cipher.isCurrent(legacy)).isTrue();
        assertThat(rotated.isCurrent(legacy)).isFalse();
    }

    @Test
    void 버전1_암호문도_컬럼_AAD가_다르면_거부한다() {
        byte[] legacy = legacyV1(KEY, "01012345678", "phone");

        assertThatThrownBy(() -> cipher.decrypt(legacy, "email")).isInstanceOf(PiiCryptoException.class);
    }

    @Test
    void 이전_키로_만든_값을_현재_키로_다시_암호화한다() {
        byte[] old = cipher.encrypt("홍길동", "name");
        PiiCipher rotated = new PiiCipher(2, Map.of(2, OTHER_KEY, 1, KEY));

        byte[] again = rotated.reencrypt(old, "name");

        assertThat(PiiCipher.keyIdOf(again)).isEqualTo(2);
        assertThat(rotated.decrypt(again, "name")).isEqualTo("홍길동");
        assertThatThrownBy(() -> cipher.decrypt(again, "name")).isInstanceOf(PiiCryptoException.class)
            .hasMessageContaining("키 id");
        assertThat(rotated.reencrypt(again, "name")).isSameAs(again);
        assertThat(rotated.reencrypt(null, "name")).isNull();
    }

    @Test
    void 모르는_키_id의_암호문은_거부하고_키를_싣지_않는다() {
        PiiCipher rotated = new PiiCipher(2, Map.of(2, OTHER_KEY, 1, KEY));
        byte[] sealed = rotated.encrypt("01012345678", "phone");

        assertThatThrownBy(() -> cipher.decrypt(sealed, "phone")).isInstanceOf(PiiCryptoException.class)
            .hasMessageContaining("키 id").hasMessageNotContaining("01012345678");
        assertThatThrownBy(() -> cipher.reencrypt(sealed, "phone")).isInstanceOf(PiiCryptoException.class);
    }

    @Test
    void 키_id_바이트를_바꾸면_같은_키라도_AAD가_달라_거부한다() {
        PiiCipher both = new PiiCipher(2, Map.of(2, KEY, 1, KEY));
        byte[] sealed = both.encrypt("01012345678", "phone");
        sealed[1] = 1;

        assertThatThrownBy(() -> both.decrypt(sealed, "phone")).isInstanceOf(PiiCryptoException.class);
    }

    @Test
    void 현재_키_id가_키_목록에_없거나_범위를_벗어나면_만들_수_없다() {
        assertThatThrownBy(() -> new PiiCipher(2, Map.of(1, KEY))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PiiCipher(0, Map.of(0, KEY))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PiiCipher(256, Map.of(256, KEY))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PiiCipher(1, Map.of(1, KEY, 2, new byte[16])))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 형식을_모르는_값의_키_id는_음수다() {
        assertThat(PiiCipher.keyIdOf(new byte[] {9, 1, 2})).isNegative();
        assertThat(PiiCipher.keyIdOf(new byte[] {2})).isNegative();
        assertThat(PiiCipher.keyIdOf(null)).isNegative();
    }

    @Test
    void 같은_값도_매번_다른_IV로_다른_암호문이_된다() {
        byte[] a = cipher.encrypt("a@example.com", "email");
        byte[] b = cipher.encrypt("a@example.com", "email");

        assertThat(a).isNotEqualTo(b);
        assertThat(Arrays.copyOfRange(a, 2, 14)).isNotEqualTo(Arrays.copyOfRange(b, 2, 14));
    }

    @Test
    void null은_null로_둔다() {
        assertThat(cipher.encrypt(null, "phone")).isNull();
        assertThat(cipher.decrypt(null, "phone")).isNull();
    }

    @Test
    void 변조된_암호문은_거부한다() {
        byte[] sealed = cipher.encrypt("01012345678", "phone");
        sealed[sealed.length - 1] ^= 0x01;

        assertThatThrownBy(() -> cipher.decrypt(sealed, "phone")).isInstanceOf(PiiCryptoException.class)
            .hasMessageNotContaining("01012345678");
    }

    @Test
    void 변조된_IV는_거부한다() {
        byte[] sealed = cipher.encrypt("01012345678", "phone");
        sealed[2] ^= 0x01;

        assertThatThrownBy(() -> cipher.decrypt(sealed, "phone")).isInstanceOf(PiiCryptoException.class);
    }

    @Test
    void 다른_키로는_복호화할_수_없다() {
        byte[] sealed = cipher.encrypt("01012345678", "phone");

        assertThatThrownBy(() -> new PiiCipher(OTHER_KEY).decrypt(sealed, "phone")).isInstanceOf(PiiCryptoException.class);
    }

    @Test
    void 모르는_버전이나_너무_짧은_값은_거부한다() {
        byte[] sealed = cipher.encrypt("01012345678", "phone");
        sealed[0] = 9;

        assertThatThrownBy(() -> cipher.decrypt(sealed, "phone")).isInstanceOf(PiiCryptoException.class)
            .hasMessageContaining("버전");
        assertThatThrownBy(() -> cipher.decrypt(new byte[] {PiiCipher.VERSION, 1, 2}, "phone"))
            .isInstanceOf(PiiCryptoException.class);
        assertThatThrownBy(() -> cipher.decrypt(new byte[] {1, 1, 2}, "phone"))
            .isInstanceOf(PiiCryptoException.class);
    }

    @Test
    void 다른_컬럼의_암호문으로_바꿔_넣으면_복호화할_수_없다() {
        byte[] phone = cipher.encrypt("01012345678", "phone");
        byte[] email = cipher.encrypt("a@example.com", "email");

        assertThat(cipher.decrypt(phone, "phone")).isEqualTo("01012345678");
        assertThatThrownBy(() -> cipher.decrypt(phone, "email")).isInstanceOf(PiiCryptoException.class);
        assertThatThrownBy(() -> cipher.decrypt(email, "name")).isInstanceOf(PiiCryptoException.class);
    }

    @Test
    void 키가_32바이트가_아니면_만들_수_없다() {
        assertThatThrownBy(() -> new PiiCipher(new byte[16])).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("32바이트");
        assertThatThrownBy(() -> new PiiCipher(null)).isInstanceOf(IllegalArgumentException.class);
    }

    static byte[] legacyV1(byte[] key, String plaintext, String column) {
        return LegacyPiiCiphertext.v1(key, plaintext, column);
    }
}
