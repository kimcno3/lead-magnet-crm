package com.leadmagnet.crm.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스케줄링을 켠다. 개별 작업은 각자의 설정으로 켜고 끈다(예: 보존 기간 파기는 app.retention.enabled, ADR 0020).
 * 그래서 한 작업을 꺼도 다른 작업의 스케줄링에는 영향이 없다.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfig {
}
