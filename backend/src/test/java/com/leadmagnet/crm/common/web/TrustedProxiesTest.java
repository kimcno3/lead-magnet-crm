package com.leadmagnet.crm.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class TrustedProxiesTest {

    final TrustedProxies proxies = TrustedProxies.parse(List.of("172.16.57.0/24", "::1"));

    @Test
    void 신뢰_프록시가_보낸_XFF는_오른쪽부터_신뢰_프록시가_아닌_첫_주소를_쓴다() {
        assertThat(proxies.resolve("172.16.57.3", List.of("203.0.113.7"))).isEqualTo("203.0.113.7");
        // 클라이언트가 위조한 앞쪽 값(198.51.100.1)은 보지 않는다
        assertThat(proxies.resolve("172.16.57.3", List.of("198.51.100.1, 203.0.113.7"))).isEqualTo("203.0.113.7");
        // 신뢰 프록시를 여러 번 거쳤으면 건너뛴다(헤더가 여러 줄이어도 이어 붙여 본다)
        assertThat(proxies.resolve("172.16.57.3", List.of("198.51.100.1, 203.0.113.7", "172.16.57.9")))
            .isEqualTo("203.0.113.7");
    }

    @Test
    void 신뢰하지_않는_피어의_XFF는_무시한다() {
        assertThat(proxies.resolve("203.0.113.50", List.of("10.0.0.1"))).isEqualTo("203.0.113.50");
        assertThat(proxies.resolve("172.16.58.3", List.of("10.0.0.1"))).isEqualTo("172.16.58.3");
    }

    @Test
    void XFF가_없거나_비었으면_피어_주소다() {
        assertThat(proxies.resolve("172.16.57.3", List.of())).isEqualTo("172.16.57.3");
        assertThat(proxies.resolve("172.16.57.3", List.of(" , "))).isEqualTo("172.16.57.3");
    }

    @Test
    void IP_형식이_아닌_값을_만나면_그_앞은_믿지_않는다() {
        assertThat(proxies.resolve("172.16.57.3", List.of("evil.example"))).isEqualTo("172.16.57.3");
        assertThat(proxies.resolve("172.16.57.3", List.of("203.0.113.7, 999.1.1.1"))).isEqualTo("172.16.57.3");
        assertThat(proxies.resolve("172.16.57.3", List.of("203.0.113.7, unknown, 172.16.57.9")))
            .isEqualTo("172.16.57.9");
    }

    @Test
    void 모든_값이_신뢰_프록시면_가장_왼쪽_값이다() {
        assertThat(proxies.resolve("172.16.57.3", List.of("172.16.57.8, 172.16.57.9"))).isEqualTo("172.16.57.8");
    }

    @Test
    void IPv6_주소도_다룬다() {
        assertThat(proxies.resolve("0:0:0:0:0:0:0:1", List.of("2001:db8::7"))).isEqualTo("2001:db8::7");
        assertThat(proxies.contains("::1")).isTrue();
        assertThat(proxies.contains("2001:db8::7")).isFalse();
    }

    @Test
    void 기본값은_admin_web_고정_주소만_믿고_docker_게이트웨이는_믿지_않는다() {
        // 공개된 포트로 들어온 연결은 Docker Desktop·userland-proxy에서 게이트웨이(172.16.57.1) 주소로 보인다
        TrustedProxies defaults = TrustedProxies.parse(List.of("172.16.57.10/32"));

        assertThat(defaults.contains("172.16.57.10")).isTrue();
        assertThat(defaults.contains("172.16.57.1")).isFalse();
        assertThat(defaults.resolve("172.16.57.1", List.of("203.0.113.7"))).isEqualTo("172.16.57.1");
        assertThat(defaults.resolve("172.16.57.10", List.of("203.0.113.7"))).isEqualTo("203.0.113.7");
    }

    @Test
    void 비어_있으면_아무도_신뢰하지_않는다() {
        TrustedProxies none = TrustedProxies.parse(List.of(" ", ""));

        assertThat(none.isEmpty()).isTrue();
        assertThat(none.resolve("127.0.0.1", List.of("203.0.113.7"))).isEqualTo("127.0.0.1");
    }

    @Test
    void CIDR_형식이_틀리면_기동_시_실패한다() {
        assertThatThrownBy(() -> TrustedProxies.parse(List.of("proxy.internal")))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("TRUSTED_PROXIES");
        assertThatThrownBy(() -> TrustedProxies.parse(List.of("10.0.0.0/33")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TrustedProxies.parse(List.of("10.0.0.0/x")))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
