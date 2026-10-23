package com.leadmagnet.crm.common.web;

import jakarta.servlet.DispatcherType;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * 출처 필터를 Spring Security 필터 체인(DEFAULT_FILTER_ORDER)보다 앞에 둔다.
 * 순서: 클라이언트 IP(가장 먼저) → 보안 헤더 → OriginGuard → Security.
 */
@Configuration(proxyBeanMethods = false)
public class OriginFilterConfig {

    static final int CLIENT_IP_ORDER = Ordered.HIGHEST_PRECEDENCE;
    static final int HEADERS_ORDER = SecurityProperties.DEFAULT_FILTER_ORDER - 20;
    static final int GUARD_ORDER = SecurityProperties.DEFAULT_FILTER_ORDER - 10;

    /** admin 포트의 신뢰 프록시 뒤 실제 클라이언트 IP (ADR 0021). 비우면 아무도 신뢰하지 않는다. */
    @Bean
    FilterRegistrationBean<ClientIpFilter> clientIpFilter(PublicPort publicPort,
            @Value("${app.trusted-proxies:}") List<String> trustedProxies) {
        FilterRegistrationBean<ClientIpFilter> registration =
            new FilterRegistrationBean<>(new ClientIpFilter(publicPort, TrustedProxies.parse(trustedProxies)));
        registration.setOrder(CLIENT_IP_ORDER);
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        return registration;
    }

    @Bean
    FilterRegistrationBean<PublicSecurityHeadersFilter> publicSecurityHeadersFilter(PublicPort publicPort) {
        FilterRegistrationBean<PublicSecurityHeadersFilter> registration =
            new FilterRegistrationBean<>(new PublicSecurityHeadersFilter(publicPort));
        registration.setOrder(HEADERS_ORDER);
        registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ERROR, DispatcherType.ASYNC);
        return registration;
    }

    /** REQUEST 디스패치에만 건다. 클라이언트가 직접 보낸 /error는 public 포트에서 404가 되고, 내부 오류 디스패치는 막지 않는다. */
    @Bean
    FilterRegistrationBean<OriginGuardFilter> originGuardFilter(PublicPort publicPort) {
        FilterRegistrationBean<OriginGuardFilter> registration =
            new FilterRegistrationBean<>(new OriginGuardFilter(publicPort));
        registration.setOrder(GUARD_ORDER);
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        return registration;
    }
}
