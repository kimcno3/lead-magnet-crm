package com.leadmagnet.crm.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.leadmagnet.crm.support.TestClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    final TestClock clock = new TestClock(Instant.parse("2026-10-24T00:00:00Z"));

    @Test
    void 분당_한도까지는_통과하고_넘으면_다음_토큰까지의_초를_돌려준다() {
        RateLimiter limiter = new RateLimiter(5, 100, clock);

        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire("a")).isZero();
        }
        // 분당 5개 = 12초마다 1개 채워진다
        assertThat(limiter.tryAcquire("a")).isEqualTo(12);
    }

    @Test
    void 시간이_지나면_그만큼_다시_채워지고_한도를_넘게_쌓이지_않는다() {
        RateLimiter limiter = new RateLimiter(5, 100, clock);
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire("a");
        }

        clock.advance(Duration.ofSeconds(7));
        assertThat(limiter.tryAcquire("a")).isEqualTo(5);
        clock.advance(Duration.ofSeconds(6));
        assertThat(limiter.tryAcquire("a")).isZero();

        clock.advance(Duration.ofHours(1));
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire("a")).isZero();
        }
        assertThat(limiter.tryAcquire("a")).isPositive();
    }

    @Test
    void 키마다_따로_센다() {
        RateLimiter limiter = new RateLimiter(1, 100, clock);

        assertThat(limiter.tryAcquire("a")).isZero();
        assertThat(limiter.tryAcquire("a")).isEqualTo(60);
        assertThat(limiter.tryAcquire("b")).isZero();
    }

    @Test
    void 키_수가_상한을_넘으면_가장_오래_안_쓴_키부터_잊는다() {
        RateLimiter limiter = new RateLimiter(1, 2, clock);
        limiter.tryAcquire("a");
        limiter.tryAcquire("b");
        limiter.tryAcquire("a");
        limiter.tryAcquire("c");

        assertThat(limiter.size()).isEqualTo(2);
        // b가 가장 오래 안 쓴 키라 잊혔고, a는 남아 있다
        assertThat(limiter.tryAcquire("a")).isPositive();
        assertThat(limiter.tryAcquire("b")).isZero();
    }

    @Test
    void 시계가_뒤로_가도_토큰을_더_주거나_예외를_내지_않는다() {
        RateLimiter limiter = new RateLimiter(1, 100, clock);
        limiter.tryAcquire("a");

        clock.advance(Duration.ofSeconds(-30));
        assertThat(limiter.tryAcquire("a")).isPositive();
    }

    @Test
    void 한도와_키_상한은_1_이상이어야_한다() {
        assertThatThrownBy(() -> new RateLimiter(0, 10, clock)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimiter(1, 0, clock)).isInstanceOf(IllegalArgumentException.class);
    }
}
