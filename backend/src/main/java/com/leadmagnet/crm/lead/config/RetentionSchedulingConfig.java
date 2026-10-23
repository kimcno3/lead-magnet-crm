package com.leadmagnet.crm.lead.config;

import com.leadmagnet.crm.lead.application.LeadRetentionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * app.retention.enabled(기본 true)일 때만 파기 스케줄러를 등록한다. 테스트는 false로 끈다.
 * 스케줄링 자체는 {@code common/SchedulingConfig}가 켠다(이 설정을 꺼도 다른 스케줄 작업은 그대로다).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.retention", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RetentionSchedulingConfig {

    @Bean
    RetentionScheduler retentionScheduler(LeadRetentionService retention) {
        return new RetentionScheduler(retention);
    }
}
