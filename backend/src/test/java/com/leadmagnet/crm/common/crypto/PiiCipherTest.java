package com.leadmagnet.crm.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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
    void 저장_형식은_버전_IV_암호문과_태그다() {
        byte[] plain = "01012345678".getBytes(StandardCharsets.UTF_8);
        byte[] sealed = cipher.encrypt("01012345678", "phone");

        assertThat(sealed[0]).isEqualTo(PiiCipher.VERSION);
        assertThat(sealed).hasSize(1 + 12 + plain.length + 16);
        assertThat(new String(sealed, StandardCharsets.ISO_8859_1)).doesNotContain("01012345678");
    }

    @Test
    void 같은_값도_매번_다른_IV로_다른_암호문이_된다() {
        byte[] a = cipher.encrypt("a@example.com", "email");
        byte[] b = cipher.encrypt("a@example.com", "email");

        assertThat(a).isNotEqualTo(b);
        assertThat(Arrays.copyOfRange(a, 1, 13)).isNotEqualTo(Arrays.copyOfRange(b, 1, 13));
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
        sealed[1] ^= 0x01;

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
}
