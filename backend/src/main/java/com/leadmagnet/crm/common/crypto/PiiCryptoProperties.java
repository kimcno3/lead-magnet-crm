package com.leadmagnet.crm.common.crypto;

import java.util.Arrays;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 개인정보 암호화 키 (ADR 0015). 둘 다 필수이고 base64로 인코딩한 32바이트여야 한다. 아니면 기동을 멈춘다.
 * 오류 메시지에는 키 값을 싣지 않는다.
 *
 * @param encKey  AES-256-GCM 키(LEAD_ENC_KEY)
 * @param hmacKey 중복 판정 HMAC-SHA256 키(LEAD_HMAC_KEY). 암호화 키와 달라야 한다.
 */
@ConfigurationProperties(prefix = "app.pii")
public record PiiCryptoProperties(String encKey, String hmacKey) {

    public PiiCryptoProperties {
        byte[] enc = decode("LEAD_ENC_KEY", encKey);
        byte[] hmac = decode("LEAD_HMAC_KEY", hmacKey);
        if (Arrays.equals(enc, hmac)) {
            throw new IllegalArgumentException("LEAD_ENC_KEY와 LEAD_HMAC_KEY는 서로 달라야 합니다");
        }
    }

    public byte[] encKeyBytes() {
        return decode("LEAD_ENC_KEY", encKey);
    }

    public byte[] hmacKeyBytes() {
        return decode("LEAD_HMAC_KEY", hmacKey);
    }

    @Override
    public String toString() {
        return "PiiCryptoProperties[encKey=***, hmacKey=***]";
    }

    private static byte[] decode(String name, String value) {
        String rule = name + "는 base64로 인코딩한 " + PiiCipher.KEY_BYTES + "바이트여야 합니다(openssl rand -base64 32)";
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + "를 설정해야 합니다. " + rule);
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(value.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(rule);
        }
        if (bytes.length != PiiCipher.KEY_BYTES) {
            throw new IllegalArgumentException(rule);
        }
        return bytes;
    }
}
