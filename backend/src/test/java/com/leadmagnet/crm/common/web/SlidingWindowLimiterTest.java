package com.leadmagnet.crm.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.leadmagnet.crm.support.TestClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SlidingWindowLimiterTest {

    final TestClock clock = new TestClock(Instant.parse("2026-10-24T00:00:00Z"));

    SlidingWindowLimiter limiter(int limit, int maxKeys) {
        return new SlidingWindowLimiter(limit, Duration.ofMinutes(15), maxKeys, clock);
    }

    @Test
    void 창_안에서_한도까지_자리를_주고_마지막_자리를_표시한다() {
        SlidingWindowLimiter limiter = limiter(3, 100);

        assertThat(limiter.tryAcquire("a").lastSlot()).isFalse();
        assertThat(limiter.tryAcquire("a").lastSlot()).isFalse();
        SlidingWindowLimiter.Slot third = limiter.tryAcquire("a");
        assertThat(third.allowed()).isTrue();
        assertThat(third.lastSlot()).isTrue();
    }

    @Test
    void 한도를_넘으면_가장_오래된_기록이_창을_벗어날_때까지의_초를_돌려준다() {
        SlidingWindowLimiter limiter = limiter(2, 100);
        limiter.tryAcquire("a");
        clock.advance(Duration.ofMinutes(5));
        limiter.tryAcquire("a");

        SlidingWindowLimiter.Slot rejected = limiter.tryAcquire("a");
        assertThat(rejected.allowed()).isFalse();
        // 첫 기록은 0분, 지금은 5분 → 10분 뒤에 창을 벗어난다
        assertThat(rejected.retryAfterSeconds()).isEqualTo(600);

        clock.advance(Duration.ofMinutes(10));
        assertThat(limiter.tryAcquire("a").allowed()).isTrue();
        // 두 번째 기록(5분)은 아직 창 안이라 다시 꽉 찼다
        assertThat(limiter.tryAcquire("a").allowed()).isFalse();
    }

    @Test
    void 거절은_기록으로_세지_않는다() {
        SlidingWindowLimiter limiter = limiter(1, 100);
        limiter.tryAcquire("a");
        for (int i = 0; i < 5; i++) {
            clock.advance(Duration.ofMinutes(1));
            assertThat(limiter.tryAcquire("a").allowed()).isFalse();
        }

        clock.advance(Duration.ofMinutes(10));
        assertThat(limiter.tryAcquire("a").allowed()).isTrue();
    }

    @Test
    void release는_그_자리만_돌려주고_reset은_키를_비운다() {
        SlidingWindowLimiter limiter = limiter(2, 100);
        SlidingWindowLimiter.Slot first = limiter.tryAcquire("a");
        limiter.tryAcquire("a");
        assertThat(limiter.tryAcquire("a").allowed()).isFalse();

        limiter.release("a", first);
        assertThat(limiter.tryAcquire("a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("a").allowed()).isFalse();

        limiter.reset("a");
        assertThat(limiter.tryAcquire("a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("a").allowed()).isTrue();
    }

    @Test
    void 거절된_자리를_release해도_다른_기록을_지우지_않는다() {
        SlidingWindowLimiter limiter = limiter(1, 100);
        limiter.tryAcquire("a");
        SlidingWindowLimiter.Slot rejected = limiter.tryAcquire("a");

        limiter.release("a", rejected);
        assertThat(limiter.tryAcquire("a").allowed()).isFalse();
    }

    @Test
    void 키마다_따로_세고_상한을_넘으면_가장_오래_안_쓴_키부터_잊는다() {
        SlidingWindowLimiter limiter = limiter(1, 2);
        limiter.tryAcquire("a");
        limiter.tryAcquire("b");
        limiter.tryAcquire("a");
        limiter.tryAcquire("c");

        assertThat(limiter.size()).isEqualTo(2);
        assertThat(limiter.tryAcquire("a").allowed()).isFalse();
        assertThat(limiter.tryAcquire("b").allowed()).isTrue();
    }

    @Test
    void 한도와_창과_키_상한은_양수여야_한다() {
        assertThatThrownBy(() -> new SlidingWindowLimiter(0, Duration.ofMinutes(1), 1, clock))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SlidingWindowLimiter(1, Duration.ZERO, 1, clock))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SlidingWindowLimiter(1, Duration.ofMinutes(1), 0, clock))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
