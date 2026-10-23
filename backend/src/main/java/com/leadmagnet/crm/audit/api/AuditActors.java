package com.leadmagnet.crm.audit.api;

import com.leadmagnet.crm.audit.domain.AuditActor;
import com.leadmagnet.crm.common.web.ClientIpFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * 요청에서 감사 주체를 만든다 (ADR 0016). ip는 {@code getRemoteAddr()}다. admin 포트에서 직전 피어가 신뢰 프록시면
 * {@link ClientIpFilter}가 이미 X-Forwarded-For의 클라이언트 주소로 바꿔 두었고, 그 밖(신뢰하지 않는 피어, public 포트)은
 * 피어 주소 그대로다(ADR 0021). 여기서는 헤더를 직접 읽지 않는다.
 */
public final class AuditActors {

    private AuditActors() {
    }

    /** 인증된 관리자 요청. 운영자 id는 JWT sub. */
    public static AuditActor of(Jwt jwt, HttpServletRequest request) {
        return new AuditActor(Long.parseLong(jwt.getSubject()), request.getRemoteAddr(),
            request.getHeader(HttpHeaders.USER_AGENT));
    }

    /** 아직 운영자를 모르는 요청(로그인). */
    public static AuditActor anonymous(HttpServletRequest request) {
        return new AuditActor(null, request.getRemoteAddr(), request.getHeader(HttpHeaders.USER_AGENT));
    }
}
