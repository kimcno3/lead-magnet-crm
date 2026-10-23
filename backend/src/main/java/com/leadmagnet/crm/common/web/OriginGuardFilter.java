package com.leadmagnet.crm.common.web;

import com.leadmagnet.crm.common.error.ErrorCode;
import com.leadmagnet.crm.common.error.ProblemDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 출처 격리 (ADR 0010). Spring Security보다 먼저 돈다.
 * <ul>
 *   <li>public 포트: /f/**, /l/**, /api/public/** 만 통과. 관리자 API, 헬스, 문서, /error 등 나머지는 404</li>
 *   <li>admin 포트: 공개 경로를 404. 그 밖의 요청은 그대로 Security 체인이 판단한다</li>
 * </ul>
 * 경로가 존재하는지 드러내지 않도록 403이 아니라 404 ProblemDetail로 답한다.
 */
public class OriginGuardFilter extends OncePerRequestFilter {

    private final PublicPort publicPort;

    public OriginGuardFilter(PublicPort publicPort) {
        this.publicPort = publicPort;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean onPublicPort = publicPort.matches(request.getLocalPort());
        if (onPublicPort != PublicPaths.matches(request)) {
            ProblemDetails.write(request, response, ErrorCode.NOT_FOUND);
            return;
        }
        chain.doFilter(request, response);
    }
}
