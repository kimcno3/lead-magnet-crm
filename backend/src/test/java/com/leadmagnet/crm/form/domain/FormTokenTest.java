package com.leadmagnet.crm.form.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.leadmagnet.crm.form.domain.FormToken.Check;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class FormTokenTest {

    static final byte[] KEY = new byte[32];
    static final byte[] OTHER_KEY = new byte[32];

    static {
        for (int i = 0; i < 32; i++) {
            KEY[i] = (byte) i;
            OTHER_KEY[i] = (byte) (i + 1);
        }
    }

    static final Instant ISSUED = Instant.parse("2026-10-24T00:00:00Z");

    final FormToken tokens = new FormToken(KEY);

    @Test
    void 발급한_토큰은_2초_뒤부터_24시간까지_유효하다() {
        String ft = tokens.issue("spring-2026", ISSUED);

        assertThat(tokens.check(ft, "spring-2026", ISSUED.plusSeconds(2))).isEqualTo(Check.VALID);
        assertThat(tokens.check(ft, "spring-2026", ISSUED.plus(Duration.ofHours(24)))).isEqualTo(Check.VALID);
    }

    @Test
    void 토큰은_base64url_문자만_쓴다() {
        assertThat(tokens.issue("spring-2026", ISSUED)).matches("^[A-Za-z0-9_-]+$");
    }

    @Test
    void 발급_후_2초_미만이면_너무_빠른_제출이다() {
        String ft = tokens.issue("spring-2026", ISSUED);

        assertThat(tokens.check(ft, "spring-2026", ISSUED)).isEqualTo(Check.TOO_FAST);
        assertThat(tokens.check(ft, "spring-2026", ISSUED.plusMillis(1999))).isEqualTo(Check.TOO_FAST);
    }

    @Test
    void 발급_시각이_미래면_너무_빠른_제출로_본다() {
        String ft = tokens.issue("spring-2026", ISSUED.plusSeconds(60));

        assertThat(tokens.check(ft, "spring-2026", ISSUED)).isEqualTo(Check.TOO_FAST);
    }

    @Test
    void 발급_후_24시간이_지나면_만료다() {
        String ft = tokens.issue("spring-2026", ISSUED);

        assertThat(tokens.check(ft, "spring-2026", ISSUED.plus(Duration.ofHours(24)).plusMillis(1)))
            .isEqualTo(Check.EXPIRED);
    }

    @Test
    void 다른_slug의_토큰은_쓸_수_없다() {
        String ft = tokens.issue("autumn-2026", ISSUED);

        assertThat(tokens.check(ft, "spring-2026", ISSUED.plusSeconds(10))).isEqualTo(Check.SLUG_MISMATCH);
    }

    @Test
    void 다른_키로_서명했거나_내용을_바꾸면_서명_불일치다() {
        String forged = new FormToken(OTHER_KEY).issue("spring-2026", ISSUED);
        assertThat(tokens.check(forged, "spring-2026", ISSUED.plusSeconds(10))).isEqualTo(Check.BAD_SIGNATURE);

        String[] parts = decode(tokens.issue("spring-2026", ISSUED)).split("\\|");
        String older = encode(parts[0] + "|" + ISSUED.minusSeconds(3600).toEpochMilli() + "|" + parts[2]);
        assertThat(tokens.check(older, "spring-2026", ISSUED.plusSeconds(10))).isEqualTo(Check.BAD_SIGNATURE);
    }

    @Test
    void 서명은_발급_시각을_해석하기_전에_확인한다() {
        Instant now = ISSUED.plusSeconds(10);

        assertThat(tokens.check(encode("spring-2026|abc|sig"), "spring-2026", now)).isEqualTo(Check.BAD_SIGNATURE);
        assertThat(tokens.check(encode("spring-2026|abc|" + sign(OTHER_KEY, "spring-2026|abc")), "spring-2026", now))
            .isEqualTo(Check.BAD_SIGNATURE);
    }

    @Test
    void 없거나_형식이_틀린_토큰은_malformed다() {
        Instant now = ISSUED.plusSeconds(10);
        assertThat(tokens.check(null, "spring-2026", now)).isEqualTo(Check.MALFORMED);
        assertThat(tokens.check("", "spring-2026", now)).isEqualTo(Check.MALFORMED);
        assertThat(tokens.check("not base64!", "spring-2026", now)).isEqualTo(Check.MALFORMED);
        assertThat(tokens.check(encode("spring-2026|abc|" + sign(KEY, "spring-2026|abc")), "spring-2026", now))
            .isEqualTo(Check.MALFORMED);
        assertThat(tokens.check(encode("spring-2026|123"), "spring-2026", now)).isEqualTo(Check.MALFORMED);
        assertThat(tokens.check(encode("spring-2026|123|%%%"), "spring-2026", now)).isEqualTo(Check.MALFORMED);
        assertThat(tokens.check("A".repeat(FormToken.MAX_LENGTH + 1), "spring-2026", now)).isEqualTo(Check.MALFORMED);
    }

    static String sign(byte[] key, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String decode(String token) {
        return new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
    }

    static String encode(String raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
