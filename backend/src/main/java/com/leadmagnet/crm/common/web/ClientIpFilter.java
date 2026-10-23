package com.leadmagnet.crm.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 실제 클라이언트 IP (ADR 0021). 모든 필터보다 먼저 돌아, admin 포트로 들어온 요청의 직전 피어가 신뢰 프록시
 * ({@link TrustedProxies})면 X-Forwarded-For에서 클라이언트 주소를 골라 {@code getRemoteAddr()}·{@code getRemoteHost()}가
 * 그 값을 돌려주게 감싼다. 그래서 감사 IP({@code AuditActors})와 rate limit·로그인 제한이 모두 같은 값을 본다.
 *
 * <p>public 포트는 앞단 프록시가 없으므로 XFF를 절대 읽지 않는다(피어가 신뢰 프록시 대역이어도). X-Real-IP는 어느 포트에서도
 * 읽지 않는다. Tomcat RemoteIpValve는 엔진 단위라 두 커넥터에 함께 걸리므로 쓰지 않았다.
 */
public class ClientIpFilter extends OncePerRequestFilter {

    static final String FORWARDED_FOR = "X-Forwarded-For";

    private final PublicPort publicPort;
    private final TrustedProxies trustedProxies;

    public ClientIpFilter(PublicPort publicPort, TrustedProxies trustedProxies) {
        this.publicPort = publicPort;
        this.trustedProxies = trustedProxies;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (trustedProxies.isEmpty() || publicPort.matches(request.getLocalPort())) {
            chain.doFilter(request, response);
            return;
        }
        String peer = request.getRemoteAddr();
        String client = trustedProxies.resolve(peer, Collections.list(request.getHeaders(FORWARDED_FOR)));
        chain.doFilter(client.equals(peer) ? request : new ClientAddressRequest(request, client), response);
    }

    private static final class ClientAddressRequest extends HttpServletRequestWrapper {

        private final String client;

        ClientAddressRequest(HttpServletRequest request, String client) {
            super(request);
            this.client = client;
        }

        @Override
        public String getRemoteAddr() {
            return client;
        }

        @Override
        public String getRemoteHost() {
            return client;
        }
    }
}
