package com.leadmagnet.crm.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 로그인 시도 제한 (ADR 0021). 비우면 기본값(이메일·IP 쌍 5회, 이메일 50회, IP 20회, 창 15분, 키 10,000개).
 *
 * @param emailIpMaxFailures 창 안에서 (정규화한 이메일, 클라이언트 IP) 쌍당 허용하는 실패 수. 보통 여기서 잠긴다
 * @param emailMaxFailures   창 안에서 이메일당 모든 IP를 합친 실패 수 상한. 여러 IP에서 나눠 대입할 때만 닿는다
 * @param ipMaxFailures      창 안에서 클라이언트 IP당 허용하는 실패 수(이메일 무관)
 * @param window             슬라이딩 윈도 길이
 * @param maxKeys            한도별로 기억하는 키 수 상한(넘으면 가장 오래 안 쓴 키부터 잊는다)
 */
@ConfigurationProperties(prefix = "app.auth.login-throttle")
public record LoginThrottleProperties(Integer emailIpMaxFailures, Integer emailMaxFailures, Integer ipMaxFailures,
        Duration window, Integer maxKeys) {

    public LoginThrottleProperties {
        emailIpMaxFailures = positive(emailIpMaxFailures, 5, "email-ip-max-failures");
        emailMaxFailures = positive(emailMaxFailures, 50, "email-max-failures");
        ipMaxFailures = positive(ipMaxFailures, 20, "ip-max-failures");
        maxKeys = positive(maxKeys, 10_000, "max-keys");
        if (window == null) {
            window = Duration.ofMinutes(15);
        } else if (window.compareTo(Duration.ofMinutes(1)) < 0) {
            throw new IllegalArgumentException("app.auth.login-throttle.window는 1분 이상이어야 합니다");
        }
    }

    private static Integer positive(Integer value, int fallback, String name) {
        if (value == null) {
            return fallback;
        }
        if (value < 1) {
            throw new IllegalArgumentException("app.auth.login-throttle." + name + "는 1 이상이어야 합니다");
        }
        return value;
    }
}
