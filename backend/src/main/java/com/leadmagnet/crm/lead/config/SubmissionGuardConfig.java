package com.leadmagnet.crm.lead.config;

import com.leadmagnet.crm.tracking.api.VisitorIdCookie;
import jakarta.servlet.DispatcherType;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 순서: 보안 헤더(-120) → OriginGuard(-110) → 제출 가드(-105) → Security(-100). */
@Configuration(proxyBeanMethods = false)
public class SubmissionGuardConfig {

    @Bean
    SubmissionRateLimits submissionRateLimits(SubmissionRateLimitProperties properties, VisitorIdCookie visitorCookie,
            Clock clock) {
        return new SubmissionRateLimits(properties, visitorCookie, clock);
    }

    @Bean
    FilterRegistrationBean<SubmissionGuardFilter> submissionGuardFilter(
            @Value("${app.public.max-submission-bytes:65536}") long maxBytes, SubmissionRateLimits rateLimits) {
        FilterRegistrationBean<SubmissionGuardFilter> registration =
            new FilterRegistrationBean<>(new SubmissionGuardFilter(maxBytes, rateLimits));
        registration.setOrder(SecurityProperties.DEFAULT_FILTER_ORDER - 5);
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        return registration;
    }
}
