package com.leadmagnet.crm.auth.config;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 관리자 인증 설정 (ADR 0003).
 *
 * @param jwtSecret    access token HS256 서명 키(JWT_SECRET). UTF-8 32바이트 미만이면 기동을 멈춘다.
 * @param adminOrigin  관리자 API CORS가 허용하는 유일한 출처(ADMIN_ORIGIN)
 * @param cookieSecure refresh 쿠키의 Secure 속성(COOKIE_SECURE). 로컬 http에서만 false로 둔다.
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(String jwtSecret, String adminOrigin, boolean cookieSecure) {

    static final int MIN_SECRET_BYTES = 32;

    public AuthProperties {
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException("JWT_SECRET은 UTF-8 기준 " + MIN_SECRET_BYTES + "바이트 이상이어야 합니다");
        }
        if (adminOrigin == null || adminOrigin.isBlank()) {
            throw new IllegalArgumentException("ADMIN_ORIGIN을 설정해야 합니다");
        }
    }
}
