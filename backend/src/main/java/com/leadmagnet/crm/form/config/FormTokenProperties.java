package com.leadmagnet.crm.form.config;

import com.leadmagnet.crm.common.crypto.SecretKeyMaterial;
import java.util.Base64;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 폼 작성 시간 토큰 서명 키 (ADR 0019). 필수이고 base64로 인코딩한 32바이트여야 한다. 아니면 기동을 멈춘다.
 * 개인정보 키(LEAD_*)와 따로 둔다: 공개 API로 나가는 토큰의 서명 키라 노출 범위와 교체 주기가 다르다.
 * 오류 메시지에는 키 값을 싣지 않는다.
 *
 * @param key HMAC-SHA256 키(FORM_TOKEN_KEY)
 */
@ConfigurationProperties(prefix = "app.form-token")
public record FormTokenProperties(String key) implements SecretKeyMaterial {

    static final int KEY_BYTES = 32;
    private static final String RULE =
        "FORM_TOKEN_KEY는 base64로 인코딩한 " + KEY_BYTES + "바이트여야 합니다(openssl rand -base64 32)";

    public FormTokenProperties {
        decode(key);
    }

    public byte[] keyBytes() {
        return decode(key);
    }

    @Override
    public String name() {
        return "FORM_TOKEN_KEY";
    }

    @Override
    public List<byte[]> candidates() {
        return List.of(keyBytes());
    }

    @Override
    public String toString() {
        return "FormTokenProperties[key=***]";
    }

    private static byte[] decode(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("FORM_TOKEN_KEY를 설정해야 합니다. " + RULE);
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(value.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(RULE);
        }
        if (bytes.length != KEY_BYTES) {
            throw new IllegalArgumentException(RULE);
        }
        return bytes;
    }
}
