package com.leadmagnet.crm.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.leadmagnet.crm.auth.config.LoginThrottleProperties;
import com.leadmagnet.crm.common.error.ErrorCode;
import com.leadmagnet.crm.common.error.TooManyRequestsException;
import com.leadmagnet.crm.support.TestClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LoginThrottleTest {

    final TestClock clock = new TestClock(Instant.parse("2026-10-24T00:00:00Z"));
    final LoginThrottle throttle = new LoginThrottle(new LoginThrottleProperties(3, 5, Duration.ofMinutes(15), 100), clock);

    void fail(String email, String ip) {
        throttle.begin(email, ip).failed();
    }

    @Test
    void 이메일별_실패가_한도에_닿으면_다음_시도는_429와_Retry_After다() {
        fail("a@example.com", "1.1.1.1");
        fail("a@example.com", "1.1.1.2");
        LoginThrottle.Attempt third = throttle.begin("a@example.com", "1.1.1.3");
        assertThat(third.failed()).containsExactly(LoginThrottle.SCOPE_EMAIL);

        assertThatThrownBy(() -> throttle.begin(" A@Example.com ", "1.1.1.4"))
            .isInstanceOfSatisfying(TooManyRequestsException.class, e -> {
                assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS);
                assertThat(e.retryAfterSeconds()).isEqualTo(900);
            });
        // 다른 이메일은 영향이 없다
        throttle.begin("b@example.com", "1.1.1.4").succeeded();
    }

    @Test
    void 성공하면_그_이메일의_실패_기록을_지운다() {
        fail("a@example.com", "1.1.1.1");
        fail("a@example.com", "1.1.1.2");
        throttle.begin("a@example.com", "1.1.1.3").succeeded();

        fail("a@example.com", "1.1.1.4");
        fail("a@example.com", "1.1.1.5");
        assertThat(throttle.begin("a@example.com", "1.1.1.6").failed()).containsExactly(LoginThrottle.SCOPE_EMAIL);
    }

    @Test
    void IP별_실패가_한도에_닿으면_이메일과_무관하게_429다() {
        for (int i = 0; i < 4; i++) {
            fail("u" + i + "@example.com", "9.9.9.9");
        }
        assertThat(throttle.begin("u4@example.com", "9.9.9.9").failed()).containsExactly(LoginThrottle.SCOPE_IP);

        assertThatThrownBy(() -> throttle.begin("new@example.com", "9.9.9.9")).isInstanceOf(TooManyRequestsException.class);
        throttle.begin("new@example.com", "8.8.8.8").succeeded();
    }

    @Test
    void 창이_지나면_다시_시도할_수_있다() {
        for (int i = 0; i < 3; i++) {
            fail("a@example.com", "1.1.1.1");
        }
        clock.advance(Duration.ofMinutes(15));

        throttle.begin("a@example.com", "1.1.1.1").succeeded();
    }

    @Test
    void 거절된_시도와_성공과_중단은_실패로_세지_않는다() {
        for (int i = 0; i < 3; i++) {
            fail("a@example.com", "1.1.1.1");
        }
        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> throttle.begin("a@example.com", "1.1.1.1"))
                .isInstanceOf(TooManyRequestsException.class);
        }
        // IP 한도 5 중 3만 썼다: 성공과 중단(서버 오류 등)은 IP 실패로 남지 않는다
        throttle.begin("b@example.com", "1.1.1.1").succeeded();
        throttle.begin("c@example.com", "1.1.1.1").abandoned();
        fail("d@example.com", "1.1.1.1");
        assertThat(throttle.begin("e@example.com", "1.1.1.1").failed()).containsExactly(LoginThrottle.SCOPE_IP);
    }

    @Test
    void 이메일과_IP가_동시에_한도에_닿으면_둘_다_알린다() {
        LoginThrottle tight = new LoginThrottle(new LoginThrottleProperties(1, 1, Duration.ofMinutes(15), 100), clock);

        assertThat(tight.begin("a@example.com", "1.1.1.1").failed())
            .containsExactly(LoginThrottle.SCOPE_EMAIL, LoginThrottle.SCOPE_IP);
        assertThat(tight.windowMinutes()).isEqualTo(15);
    }
}
