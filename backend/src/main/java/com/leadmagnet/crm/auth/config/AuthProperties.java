package com.leadmagnet.crm.auth.config;

import com.leadmagnet.crm.common.crypto.SecretKeyMaterial;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 관리자 인증 설정 (ADR 0003).
 *
 * @param jwtSecret    access token HS256 서명 키(JWT_SECRET). UTF-8 32바이트 미만이면 기동을 멈춘다.
 * @param adminOrigin  관리자 API CORS가 허용하는 유일한 출처(ADMIN_ORIGIN)
 * @param cookieSecure refresh 쿠키의 Secure 속성(COOKIE_SECURE). 로컬 http에서만 false로 둔다.
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(String jwtSecret, String adminOrigin, boolean cookieSecure) implements SecretKeyMaterial {

    static final int MIN_SECRET_BYTES = 32;

    public AuthProperties {
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException("JWT_SECRET은 UTF-8 기준 " + MIN_SECRET_BYTES + "바이트 이상이어야 합니다");
        }
        if (adminOrigin == null || adminOrigin.isBlank()) {
            throw new IllegalArgumentException("ADMIN_ORIGIN을 설정해야 합니다");
        }
    }

    @Override
    public String name() {
        return "JWT_SECRET";
    }

    /** 문자열 그대로의 UTF-8 바이트, 그리고 base64로 해독되면 그 바이트도(다른 키가 base64로 적힌 같은 값일 수 있다). */
    @Override
    public List<byte[]> candidates() {
        List<byte[]> candidates = new ArrayList<>();
        candidates.add(jwtSecret.getBytes(StandardCharsets.UTF_8));
        try {
            candidates.add(Base64.getDecoder().decode(jwtSecret.trim()));
        } catch (IllegalArgumentException e) {
            // base64가 아니면 원문 바이트만 본다
        }
        return candidates;
    }

    @Override
    public boolean mustDifferFromOthers() {
        return true;
    }
}
