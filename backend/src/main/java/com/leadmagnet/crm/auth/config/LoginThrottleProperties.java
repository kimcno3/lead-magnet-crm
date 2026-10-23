package com.leadmagnet.crm.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 로그인 시도 제한 (ADR 0021). 비우면 기본값(이메일·IP 쌍 5회, 이메일 50회, IP 20회, 창 15분, 잠금 대기 2초). 실패 기록은 DB
 * (`login_failures`)에 두어 서버 여러 대·재시작에도 유지된다.
 *
 * @param emailIpMaxFailures 창 안에서 (정규화한 이메일, 클라이언트 IP) 쌍당 허용하는 실패 수. 보통 여기서 잠긴다
 * @param emailMaxFailures   창 안에서 이메일당 모든 IP를 합친 실패 수 상한. 여러 IP에서 나눠 대입할 때만 닿는다
 * @param ipMaxFailures      창 안에서 클라이언트 IP당 허용하는 실패 수(이메일 무관)
 * @param window             슬라이딩 윈도 길이
 * @param lockTimeout        자리 잡기 트랜잭션이 키 잠금(advisory lock)을 기다리는 최대 시간. 넘으면 429(DB 연결을 오래 붙잡지 않는다)
 */
@ConfigurationProperties(prefix = "app.auth.login-throttle")
public record LoginThrottleProperties(Integer emailIpMaxFailures, Integer emailMaxFailures, Integer ipMaxFailures,
        Duration window, Duration lockTimeout) {

    public LoginThrottleProperties {
        emailIpMaxFailures = positive(emailIpMaxFailures, 5, "email-ip-max-failures");
        emailMaxFailures = positive(emailMaxFailures, 50, "email-max-failures");
        ipMaxFailures = positive(ipMaxFailures, 20, "ip-max-failures");
        if (window == null) {
            window = Duration.ofMinutes(15);
        } else if (window.compareTo(Duration.ofMinutes(1)) < 0) {
            throw new IllegalArgumentException("app.auth.login-throttle.window는 1분 이상이어야 합니다");
        }
        if (lockTimeout == null) {
            lockTimeout = Duration.ofSeconds(2);
        } else if (lockTimeout.toMillis() < 1) {
            throw new IllegalArgumentException("app.auth.login-throttle.lock-timeout은 1ms 이상이어야 합니다");
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
