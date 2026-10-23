package com.leadmagnet.crm.audit.api;

import com.leadmagnet.crm.audit.domain.AuditActor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * 요청에서 감사 주체를 만든다 (ADR 0016). ip는 {@code getRemoteAddr()}만 쓴다. X-Forwarded-For는 클라이언트가
 * 마음대로 넣을 수 있고 신뢰할 프록시 설정이 없어 읽지 않는다(리버스 프록시 뒤에서는 프록시 주소가 남는다).
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
