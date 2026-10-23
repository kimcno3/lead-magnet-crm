package com.leadmagnet.crm.form.domain;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 공개 폼 작성 시간 토큰 {@code ft} (ADR 0019). 폼을 받을 때 발급하고 제출할 때 확인한다.
 * 모양은 {@code base64url(slug|발급시각epochMs|base64url(HMAC-SHA256(key, "slug|발급시각")))}이다.
 * 서버는 상태를 두지 않는다(같은 토큰을 24시간 동안 여러 번 쓸 수 있고, 반복 제출은 rate limit이 막는다).
 * <ul>
 *   <li>발급 후 {@link #MIN_AGE} 미만이면 사람이 폼을 채웠다고 보기 어렵다(스크립트 제출)</li>
 *   <li>{@link #MAX_AGE}를 넘으면 오래 열어 둔 탭이거나 재사용이다</li>
 * </ul>
 * 두 값은 일부러 설정으로 빼지 않는다(테스트는 Clock을 옮긴다).
 */
public final class FormToken {

    public static final Duration MIN_AGE = Duration.ofSeconds(2);
    public static final Duration MAX_AGE = Duration.ofHours(24);
    /** 디코드 전에 거르는 길이 상한. slug 64자 + 시각 13자 + 서명 43자를 base64url로 감싸도 200자 안이다. */
    public static final int MAX_LENGTH = 256;

    private static final String ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    /** 확인 결과. VALID가 아니면 제출을 받지 않는다(응답은 모두 같은 422, 사유는 로그·테스트용). */
    public enum Check { VALID, MALFORMED, BAD_SIGNATURE, SLUG_MISMATCH, TOO_FAST, EXPIRED }

    private final SecretKeySpec key;

    public FormToken(byte[] key) {
        this.key = new SecretKeySpec(key.clone(), ALGORITHM);
    }

    public String issue(String slug, Instant now) {
        String payload = slug + "|" + now.toEpochMilli();
        return encode(payload + "|" + ENCODER.encodeToString(sign(payload)));
    }

    public Check check(String token, String slug, Instant now) {
        if (token == null || token.isEmpty() || token.length() > MAX_LENGTH) {
            return Check.MALFORMED;
        }
        String raw;
        try {
            raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return Check.MALFORMED;
        }
        String[] parts = raw.split("\\|", -1);
        if (parts.length != 3) {
            return Check.MALFORMED;
        }
        long issuedAt;
        byte[] signature;
        try {
            issuedAt = Long.parseLong(parts[1]);
            signature = Base64.getUrlDecoder().decode(parts[2]);
        } catch (IllegalArgumentException e) {
            return Check.MALFORMED;
        }
        if (!MessageDigest.isEqual(signature, sign(parts[0] + "|" + parts[1]))) {
            return Check.BAD_SIGNATURE;
        }
        if (!parts[0].equals(slug)) {
            return Check.SLUG_MISMATCH;
        }
        Duration age = Duration.between(Instant.ofEpochMilli(issuedAt), now);
        if (age.compareTo(MIN_AGE) < 0) {
            return Check.TOO_FAST;
        }
        if (age.compareTo(MAX_AGE) > 0) {
            return Check.EXPIRED;
        }
        return Check.VALID;
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("폼 토큰 서명에 실패했습니다", e);
        }
    }

    private static String encode(String raw) {
        return ENCODER.encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
