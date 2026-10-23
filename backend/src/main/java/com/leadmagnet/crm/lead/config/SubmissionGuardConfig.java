package com.leadmagnet.crm.lead.config;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 순서: 보안 헤더(-120) → OriginGuard(-110) → 제출 가드(-105) → Security(-100). */
@Configuration(proxyBeanMethods = false)
public class SubmissionGuardConfig {

    @Bean
    FilterRegistrationBean<SubmissionGuardFilter> submissionGuardFilter(
            @Value("${app.public.max-submission-bytes:65536}") long maxBytes) {
        FilterRegistrationBean<SubmissionGuardFilter> registration =
            new FilterRegistrationBean<>(new SubmissionGuardFilter(maxBytes));
        registration.setOrder(SecurityProperties.DEFAULT_FILTER_ORDER - 5);
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        return registration;
    }
}
