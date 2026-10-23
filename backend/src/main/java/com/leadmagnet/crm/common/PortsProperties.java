package com.leadmagnet.crm.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 출처별 포트. admin(:8080)은 관리자 API와 화면, public(:8081)은 공개 폼과 공개 API만 제공한다.
 * public 커넥터와 OriginGuardFilter는 이슈 #7에서 이 값을 사용해 붙인다.
 */
@ConfigurationProperties(prefix = "app.ports")
public record PortsProperties(int admin, int publicPort) {
}
