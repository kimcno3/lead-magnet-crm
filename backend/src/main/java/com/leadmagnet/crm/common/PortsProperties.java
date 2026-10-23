package com.leadmagnet.crm.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 출처별 포트. admin(:8080)은 관리자 API와 화면, public(:8081)은 공개 폼과 공개 API만 제공한다 (ADR 0010).
 * 기본 커넥터는 admin 포트, public 커넥터는 {@code common/web/PublicPort}가 붙이고 OriginGuardFilter가 경로를 나눈다.
 */
@ConfigurationProperties(prefix = "app.ports")
public record PortsProperties(int admin, int publicPort) {
}
