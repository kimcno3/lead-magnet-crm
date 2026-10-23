package com.leadmagnet.crm.common.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 중복 판정용 결정적 해시 (ADR 0015). HMAC-SHA256(키, 정규화한 값)을 소문자 hex 64자로 낸다.
 * 키 없는 해시(SHA-256)는 전화번호 공간이 작아(약 10억 개) DB만 유출돼도 전수 대입으로 원문을 되찾을 수 있어 쓰지 않는다.
 */
public final class PiiHasher {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    public PiiHasher(byte[] key) {
        if (key == null || key.length != PiiCipher.KEY_BYTES) {
            throw new IllegalArgumentException("HMAC 키는 " + PiiCipher.KEY_BYTES + "바이트여야 합니다");
        }
        this.key = new SecretKeySpec(key.clone(), ALGORITHM);
    }

    /** 전화번호는 숫자만 남겨 해시한다. 숫자가 없으면 null. */
    public String hashPhone(String phone) {
        return phone == null ? null : hash(phone.replaceAll("\\D", ""));
    }

    /** 이메일은 앞뒤 공백을 자르고 소문자로 바꿔 해시한다. 비었으면 null. */
    public String hashEmail(String email) {
        return email == null ? null : hash(email.trim().toLowerCase(Locale.ROOT));
    }

    /** 이미 정규화한 값을 해시한다. null이나 빈 값은 null. */
    public String hash(String normalized) {
        if (normalized == null || normalized.isEmpty()) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new PiiCryptoException("해시를 만들지 못했습니다", e);
        }
    }
}
