package com.leadmagnet.crm.tracking.api;

import com.leadmagnet.crm.auth.config.AuthProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 방문자 쿠키 lm_vid (UUID v4, HttpOnly, SameSite=Lax, Path=/, 1년). 공개 폼 페이지와 단축 URL에서 쓴다.
 * 쿠키 값은 브라우저가 보낸 것이라 믿지 않는다: 정규 형식의 UUID v4가 아니면 버리고 새로 발급한다.
 */
@Component
public class VisitorIdCookie {

    public static final String NAME = "lm_vid";
    static final Duration MAX_AGE = Duration.ofDays(365);

    private static final Pattern UUID_V4 =
        Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");

    private final boolean secure;

    public VisitorIdCookie(AuthProperties properties) {
        this.secure = properties.cookieSecure();
    }

    /** 요청의 방문자 id. 없거나 형식이 틀리면 새 id를 만들고 Set-Cookie로 발급한다. */
    public UUID resolve(HttpServletRequest request, HttpServletResponse response) {
        Optional<UUID> existing = read(request);
        if (existing.isPresent()) {
            return existing.get();
        }
        UUID issued = UUID.randomUUID();
        ResponseCookie cookie = ResponseCookie.from(NAME, issued.toString())
            .httpOnly(true)
            .secure(secure)
            .sameSite("Lax")
            .path("/")
            .maxAge(MAX_AGE)
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return issued;
    }

    /** 요청 쿠키의 방문자 id. 없거나 형식이 틀리면 비어 있다. 쿠키를 발급하지 않는다(신청 접수 등 읽기 전용 사용). */
    public Optional<UUID> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (NAME.equals(cookie.getName())) {
                return parse(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    static Optional<UUID> parse(String value) {
        if (value == null || !UUID_V4.matcher(value).matches()) {
            return Optional.empty();
        }
        return Optional.of(UUID.fromString(value));
    }
}
