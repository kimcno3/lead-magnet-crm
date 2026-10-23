package com.leadmagnet.crm.auth.config;

import com.leadmagnet.crm.auth.application.LoginThrottle;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 창이 지난 로그인 실패 기록을 5분마다 지운다 (ADR 0021 변경 기록). 스케줄링은 {@code common/SchedulingConfig}가 켠다.
 * 지난 기록은 한도 계산에서 이미 빠지므로 정리는 테이블 크기만 지킨다. 인스턴스마다 돌지만 같은 행을 지울 뿐이라 겹쳐도 된다.
 * {@code app.auth.login-throttle.cleanup-enabled}(기본 true)가 false면 등록하지 않는다(테스트는 끈다).
 */
@Component
@ConditionalOnProperty(prefix = "app.auth.login-throttle", name = "cleanup-enabled", havingValue = "true",
    matchIfMissing = true)
public class LoginFailureCleanup {

    private static final Logger log = LoggerFactory.getLogger(LoginFailureCleanup.class);

    private final LoginThrottle throttle;

    public LoginFailureCleanup(LoginThrottle throttle) {
        this.throttle = throttle;
    }

    @Scheduled(fixedDelay = 5, initialDelay = 5, timeUnit = TimeUnit.MINUTES)
    public void purgeExpired() {
        try {
            throttle.purgeExpired();
        } catch (RuntimeException e) {
            // DB 오류 메시지에 값이 실릴 수 있어 예외 종류만 남긴다. 다음 주기에 다시 돈다(멱등).
            log.error("로그인 실패 기록 정리 실패: 원인={}", e.getClass().getName());
        }
    }
}
