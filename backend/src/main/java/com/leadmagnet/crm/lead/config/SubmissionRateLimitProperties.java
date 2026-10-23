package com.leadmagnet.crm.lead.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 신청 제출 rate limit (ADR 0019). 비우면 기본값(IP 분당 10회, 방문자 쿠키 분당 5회, 키 10,000개).
 *
 * @param ipPerMinute      원격 주소({@code getRemoteAddr()})당 분당 제출 수
 * @param visitorPerMinute 방문자 쿠키(lm_vid)당 분당 제출 수. 쿠키가 없거나 형식이 틀리면 IP 한도만 적용한다
 * @param maxKeys          한도별로 기억하는 키 수 상한(넘으면 가장 오래 안 쓴 키부터 잊는다)
 */
@ConfigurationProperties(prefix = "app.public.rate-limit")
public record SubmissionRateLimitProperties(Integer ipPerMinute, Integer visitorPerMinute, Integer maxKeys) {

    public SubmissionRateLimitProperties {
        ipPerMinute = positive(ipPerMinute, 10, "ip-per-minute");
        visitorPerMinute = positive(visitorPerMinute, 5, "visitor-per-minute");
        maxKeys = positive(maxKeys, 10_000, "max-keys");
    }

    private static Integer positive(Integer value, int fallback, String name) {
        if (value == null) {
            return fallback;
        }
        if (value < 1) {
            throw new IllegalArgumentException("app.public.rate-limit." + name + "는 1 이상이어야 합니다");
        }
        return value;
    }
}
