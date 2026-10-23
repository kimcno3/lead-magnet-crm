package com.leadmagnet.crm.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ClientIpFilterTest {

    static final int ADMIN = 8080;
    static final int PUBLIC = 8081;

    final PublicPort publicPort = publicPort();
    final ClientIpFilter filter = new ClientIpFilter(publicPort, TrustedProxies.parse(List.of("172.16.57.0/24")));

    static PublicPort publicPort() {
        PublicPort port = mock(PublicPort.class);
        when(port.matches(PUBLIC)).thenReturn(true);
        return port;
    }

    HttpServletRequest pass(int localPort, String peer, String... forwardedFor) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/admin/auth/login");
        request.setLocalPort(localPort);
        request.setRemoteAddr(peer);
        for (String value : forwardedFor) {
            request.addHeader("X-Forwarded-For", value);
        }
        request.addHeader("X-Real-IP", "198.51.100.99");
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return (HttpServletRequest) chain.getRequest();
    }

    @Test
    void admin_포트에서_신뢰_프록시를_거친_요청은_XFF의_클라이언트_주소가_원격_주소가_된다() throws Exception {
        HttpServletRequest seen = pass(ADMIN, "172.16.57.3", "198.51.100.1, 203.0.113.7");

        assertThat(seen.getRemoteAddr()).isEqualTo("203.0.113.7");
        assertThat(seen.getRemoteHost()).isEqualTo("203.0.113.7");
    }

    @Test
    void admin_포트라도_신뢰하지_않는_피어의_XFF는_무시한다() throws Exception {
        assertThat(pass(ADMIN, "203.0.113.50", "10.0.0.1").getRemoteAddr()).isEqualTo("203.0.113.50");
    }

    @Test
    void public_포트는_신뢰_프록시_대역에서_와도_XFF와_X_Real_IP를_읽지_않는다() throws Exception {
        assertThat(pass(PUBLIC, "172.16.57.3", "203.0.113.7").getRemoteAddr()).isEqualTo("172.16.57.3");
    }

    @Test
    void X_Real_IP만_있으면_피어_주소를_쓴다() throws Exception {
        assertThat(pass(ADMIN, "172.16.57.3").getRemoteAddr()).isEqualTo("172.16.57.3");
    }
}
