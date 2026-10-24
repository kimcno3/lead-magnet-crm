package com.leadmagnet.crm.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 로컬 체험용 예시 데이터 (ADR 0023).
 *
 * @param enabled true면 기동 때 DB에 캠페인이 하나도 없을 때만 예시 데이터를 한 번 넣는다. 기본 false. 운영에서는 켜지 않는다
 */
@ConfigurationProperties(prefix = "app.demo")
public record DemoProperties(Boolean enabled) {

    public DemoProperties {
        enabled = enabled != null && enabled;
    }
}
