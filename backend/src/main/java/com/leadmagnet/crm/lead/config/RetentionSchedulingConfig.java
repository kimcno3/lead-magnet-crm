package com.leadmagnet.crm.lead.config;

import com.leadmagnet.crm.lead.application.LeadRetentionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** app.retention.enabled(기본 true)일 때만 스케줄링을 켜고 파기 스케줄러를 등록한다. 테스트는 false로 끈다. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "app.retention", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RetentionSchedulingConfig {

    @Bean
    RetentionScheduler retentionScheduler(LeadRetentionService retention) {
        return new RetentionScheduler(retention);
    }
}
