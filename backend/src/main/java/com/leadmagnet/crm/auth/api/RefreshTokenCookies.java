package com.leadmagnet.crm.auth.api;

import com.leadmagnet.crm.auth.application.AuthService;
import com.leadmagnet.crm.auth.config.AuthProperties;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** refresh token 쿠키. HttpOnly, SameSite=Strict, Path=/api/admin/auth로 refresh·logout 요청에만 실린다. */
@Component
public class RefreshTokenCookies {

    public static final String NAME = "lm_refresh";
    static final String PATH = "/api/admin/auth";

    private final boolean secure;

    public RefreshTokenCookies(AuthProperties properties) {
        this.secure = properties.cookieSecure();
    }

    public ResponseCookie issue(String rawRefreshToken) {
        return base(rawRefreshToken).maxAge(AuthService.REFRESH_TOKEN_TTL).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Strict")
            .path(PATH);
    }
}
