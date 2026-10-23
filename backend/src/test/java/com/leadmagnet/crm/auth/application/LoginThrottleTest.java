package com.leadmagnet.crm.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.leadmagnet.crm.auth.config.LoginThrottleProperties;
import com.leadmagnet.crm.common.error.ErrorCode;
import com.leadmagnet.crm.common.error.TooManyRequestsException;
import com.leadmagnet.crm.support.TestClock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** 한도: (이메일, IP) 쌍 3, 이메일 전체 6, IP 5, 창 15분. */
class LoginThrottleTest {

    final TestClock clock = new TestClock(Instant.parse("2026-10-24T00:00:00Z"));
    final LoginThrottle throttle =
        new LoginThrottle(new LoginThrottleProperties(3, 6, 5, Duration.ofMinutes(15), 100), clock);

    void fail(String email, String ip) {
        throttle.begin(email, ip).failed();
    }

    @Test
    void 같은_이메일과_IP의_실패가_한도에_닿으면_그_쌍의_다음_시도는_429와_Retry_After다() {
        fail("a@example.com", "1.1.1.1");
        fail("a@example.com", "1.1.1.1");
        assertThat(throttle.begin("a@example.com", "1.1.1.1").failed()).containsExactly(LoginThrottle.SCOPE_EMAIL_IP);

        assertThatThrownBy(() -> throttle.begin(" A@Example.com ", "1.1.1.1"))
            .isInstanceOfSatisfying(TooManyRequestsException.class, e -> {
                assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS);
                assertThat(e.retryAfterSeconds()).isEqualTo(900);
            });
    }

    @Test
    void 공격자가_한_IP에서_잠가도_운영자는_다른_IP에서_로그인할_수_있다() {
        for (int i = 0; i < 3; i++) {
            fail("ops@example.com", "6.6.6.6");
        }
        assertThatThrownBy(() -> throttle.begin("ops@example.com", "6.6.6.6"))
            .isInstanceOf(TooManyRequestsException.class);

        throttle.begin("ops@example.com", "10.0.0.5").succeeded();
    }

    @Test
    void 여러_IP에_나눠_대입하면_이메일_전체_상한에서_막힌다() {
        for (int i = 0; i < 5; i++) {
            fail("ops@example.com", "7.7.7." + i);
        }
        assertThat(throttle.begin("ops@example.com", "7.7.7.5").failed()).containsExactly(LoginThrottle.SCOPE_EMAIL);

        assertThatThrownBy(() -> throttle.begin("ops@example.com", "7.7.7.99"))
            .isInstanceOf(TooManyRequestsException.class);
        // 다른 이메일은 영향이 없다
        throttle.begin("other@example.com", "7.7.7.99").succeeded();
    }

    @Test
    void 성공하면_그_쌍의_실패_기록을_지운다() {
        fail("a@example.com", "1.1.1.1");
        fail("a@example.com", "1.1.1.1");
        throttle.begin("a@example.com", "1.1.1.1").succeeded();

        fail("a@example.com", "1.1.1.1");
        fail("a@example.com", "1.1.1.1");
        // 초기화되지 않았다면 실패 4번째라 쌍 한도(3)에 걸려 429다
        throttle.begin("a@example.com", "1.1.1.1").succeeded();
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
    void 여러_한도에_걸리면_Retry_After는_가장_긴_대기다() {
        // IP 한도(5)를 먼저 0분에 4번 채우고, 10분 뒤 같은 쌍으로 쌍 한도(3)와 IP 한도를 함께 채운다
        for (int i = 0; i < 2; i++) {
            fail("u" + i + "@example.com", "9.9.9.9");
        }
        clock.advance(Duration.ofMinutes(10));
        for (int i = 0; i < 3; i++) {
            fail("a@example.com", "9.9.9.9");
        }

        // IP 기록은 0분에 시작해 5분 뒤 풀리지만, 쌍 기록은 10분에 시작해 15분 뒤 풀린다
        assertThatThrownBy(() -> throttle.begin("a@example.com", "9.9.9.9"))
            .isInstanceOfSatisfying(TooManyRequestsException.class, e -> assertThat(e.retryAfterSeconds()).isEqualTo(900));
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
        // IP 한도 5 중 3만 썼다: 거절·성공·중단은 IP 실패로 남지 않는다
        throttle.begin("b@example.com", "1.1.1.1").succeeded();
        throttle.begin("c@example.com", "1.1.1.1").abandoned();
        fail("d@example.com", "1.1.1.1");
        assertThat(throttle.begin("e@example.com", "1.1.1.1").failed()).containsExactly(LoginThrottle.SCOPE_IP);
    }

    @Test
    void 여러_한도가_동시에_닿으면_모두_알린다() {
        LoginThrottle tight = new LoginThrottle(new LoginThrottleProperties(1, 1, 1, Duration.ofMinutes(15), 100), clock);

        assertThat(tight.begin("a@example.com", "1.1.1.1").failed())
            .containsExactly(LoginThrottle.SCOPE_EMAIL_IP, LoginThrottle.SCOPE_EMAIL, LoginThrottle.SCOPE_IP);
        assertThat(tight.windowMinutes()).isEqualTo(15);
    }

    @Test
    void 동시에_들어온_시도도_한도보다_많이_비밀번호_비교로_가지_않는다() throws Exception {
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                start.await();
                try {
                    throttle.begin("a@example.com", "1.1.1.1").failed();
                    return true;
                } catch (TooManyRequestsException e) {
                    return false;
                }
            }));
        }
        start.countDown();
        int admitted = 0;
        for (Future<Boolean> result : results) {
            admitted += result.get(10, TimeUnit.SECONDS) ? 1 : 0;
        }
        pool.shutdown();

        assertThat(admitted).isEqualTo(3);
    }
}
