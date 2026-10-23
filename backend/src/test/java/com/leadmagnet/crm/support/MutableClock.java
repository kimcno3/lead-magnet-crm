package com.leadmagnet.crm.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * 테스트용 Clock. 기본은 시스템 시각이고 advance로 앞으로 옮길 수 있다. freezeAt이면 그 시각에 멈춘다(advance는 그 위에 더한다).
 * 컨텍스트를 공유하므로 옮긴 테스트는 끝날 때 reset해야 한다.
 */
public class MutableClock extends Clock {

    private volatile Duration offset = Duration.ZERO;
    private volatile Instant frozen;

    /** 시스템 시각 대신 이 시각에 멈춘다. 경계 시각을 정확히 맞춰야 하는 테스트용. reset이 풀어 준다. */
    public void freezeAt(Instant instant) {
        frozen = instant;
    }

    public void advance(Duration duration) {
        offset = offset.plus(duration);
    }

    public void reset() {
        offset = Duration.ZERO;
        frozen = null;
    }

    @Override
    public Instant instant() {
        Instant base = frozen;
        return (base != null ? base : Instant.now()).plus(offset);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException();
    }
}
