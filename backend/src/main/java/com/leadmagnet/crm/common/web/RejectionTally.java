package com.leadmagnet.crm.common.web;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;

/**
 * 제한에 걸린 거절을 요청마다 기록하지 않고 최대 1분에 한 줄씩 건수만 남긴다 (ADR 0016 결과 절, ADR 0019·0021).
 * 공격이 그대로 로그·감사 쓰기 폭주가 되지 않게 하려는 것이다. 키(IP·쿠키·이메일) 값은 남기지 않는다.
 * 거절이든 통과든 요청마다 {@link #flushIfDue}를 부르므로, 공격이 멈춘 뒤 남은 건수도 다음 요청 때 기록된다(스케줄러 없이).
 */
public final class RejectionTally {

    static final Duration LOG_INTERVAL = Duration.ofMinutes(1);

    private final Logger log;
    private final String message;
    private final Clock clock;
    private final AtomicLong rejected = new AtomicLong();
    private long lastLoggedAt = Long.MIN_VALUE;

    /** @param message 건수 자리 {@code {}} 하나를 가진 로그 문구 */
    public RejectionTally(Logger log, String message, Clock clock) {
        this.log = log;
        this.message = message;
        this.clock = clock;
    }

    public void reject() {
        rejected.incrementAndGet();
    }

    /** 모인 거절 건수가 있고 마지막 기록에서 1분이 지났으면 한 줄로 남긴다. */
    public void flushIfDue() {
        if (rejected.get() == 0) {
            return;
        }
        synchronized (this) {
            long now = clock.millis();
            if (lastLoggedAt == Long.MIN_VALUE || now - lastLoggedAt >= LOG_INTERVAL.toMillis()) {
                long count = rejected.getAndSet(0);
                if (count > 0) {
                    lastLoggedAt = now;
                    log.info(message, count);
                }
            }
        }
    }
}
