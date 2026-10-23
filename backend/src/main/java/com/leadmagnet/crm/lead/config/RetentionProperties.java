package com.leadmagnet.crm.lead.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 보존 기간 파기 (ADR 0020).
 *
 * @param enabled   매일 03:00(Asia/Seoul) 스케줄러를 켤지. 기본 true. 수동 실행 API는 이 값과 상관없이 쓸 수 있다
 * @param batchSize 한 트랜잭션(advisory lock 한 번)에 익명화할 최대 신청 수. 기본 500
 */
@ConfigurationProperties(prefix = "app.retention")
public record RetentionProperties(Boolean enabled, Integer batchSize) {

    public RetentionProperties {
        enabled = enabled == null || enabled;
        batchSize = batchSize == null ? 500 : batchSize;
        if (batchSize < 1) {
            throw new IllegalArgumentException("app.retention.batch-size는 1 이상이어야 합니다");
        }
    }
}
