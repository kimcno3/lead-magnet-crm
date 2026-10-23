package com.leadmagnet.crm.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * public 포트의 모든 응답(래퍼 셸, 정적 스크립트, 공개 API, 404·410 안내, OriginGuard 404)에 보안 헤더를 붙인다 (ADR 0002).
 * OriginGuardFilter보다 먼저 돌아 차단 응답에도 붙는다. Spring Security의 기본 헤더 작성기는 이미 있는 헤더를 덮지 않는다.
 */
public class PublicSecurityHeadersFilter extends OncePerRequestFilter {

    /**
     * srcdoc 문서는 부모의 CSP를 상속하므로 업로드 HTML의 인라인 스크립트·스타일을 위해 'unsafe-inline'이 필요하다.
     * 외부 전송은 connect-src 'self'와 form-action 'none'으로 막는다. 업로드 HTML이 방문자 입력을 GET URL에 담아
     * 내보낼 수 있으므로 이미지·스타일·폰트도 외부 https:를 허용하지 않는다(ADR 0002 변경 기록).
     */
    public static final String CONTENT_SECURITY_POLICY = "default-src 'none'; script-src 'self' 'unsafe-inline'; "
        + "style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:; "
        + "connect-src 'self'; frame-src 'self'; form-action 'none'; base-uri 'none'; frame-ancestors 'none'";

    private final PublicPort publicPort;

    public PublicSecurityHeadersFilter(PublicPort publicPort) {
        this.publicPort = publicPort;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (publicPort.matches(request.getLocalPort())) {
            response.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY);
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
            response.setHeader("X-Frame-Options", "DENY");
        }
        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }
}
