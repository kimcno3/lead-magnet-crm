package com.leadmagnet.crm.common.web;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

/**
 * 키별 인메모리 슬라이딩 윈도 (ADR 0021). 창 안의 기록이 한도에 닿으면 가장 오래된 기록이 창을 벗어날 때까지 자리를 주지 않는다.
 * 토큰 버킷({@link RateLimiter})은 조금씩 다시 채워 "15분에 5번 틀리면 잠금"을 표현하지 못해 따로 두고, 키 상한(LRU)은
 * {@link BoundedLruMap}을 함께 쓴다. 키당 기록은 한도 개를 넘지 않으므로 메모리는 한도 × 키 상한으로 묶인다.
 *
 * <p>자리는 시도 전에 미리 잡는다({@link #tryAcquire}). 동시에 들어온 시도가 모두 검사를 통과한 뒤 실패를 기록해 한도를
 * 넘는 일을 막기 위해서다. 실패로 세지 않을 시도(성공, 중단)는 {@link #release}로 그 자리를 돌려준다.
 * 거절된 시도는 기록하지 않는다(잠금이 거절 때문에 늘어나지 않는다). 서버 인스턴스마다 따로 센다.
 */
public final class SlidingWindowLimiter {

    /**
     * 한 번의 자리 요청 결과. 거절이면 retryAfterSeconds가 1 이상이다. lastSlot이면 이 자리로 창이 꽉 찼다(다음 시도부터 거절).
     */
    public record Slot(long retryAfterSeconds, long at, boolean lastSlot) {

        public boolean allowed() {
            return retryAfterSeconds == 0;
        }
    }

    private final int limit;
    private final long windowMillis;
    private final Clock clock;
    private final Map<String, Deque<Long>> entries;

    public SlidingWindowLimiter(int limit, Duration window, int maxKeys, Clock clock) {
        if (limit < 1 || window == null || window.isNegative() || window.isZero() || maxKeys < 1) {
            throw new IllegalArgumentException("한도·창·키 상한은 양수여야 합니다");
        }
        this.limit = limit;
        this.windowMillis = window.toMillis();
        this.clock = clock;
        this.entries = new BoundedLruMap<>(maxKeys);
    }

    public synchronized Slot tryAcquire(String key) {
        long now = clock.millis();
        Deque<Long> log = entries.computeIfAbsent(key, k -> new ArrayDeque<>());
        while (!log.isEmpty() && log.peekFirst() <= now - windowMillis) {
            log.pollFirst();
        }
        if (log.size() >= limit) {
            long waitMillis = log.peekFirst() + windowMillis - now;
            return new Slot(Math.max(1, (waitMillis + 999) / 1000), now, false);
        }
        log.addLast(now);
        return new Slot(0, now, log.size() == limit);
    }

    /** 잡은 자리 하나를 돌려준다. 거절된 자리나 이미 지워진 키는 아무것도 하지 않는다. */
    public synchronized void release(String key, Slot slot) {
        if (!slot.allowed()) {
            return;
        }
        Deque<Long> log = entries.get(key);
        if (log != null) {
            log.removeLastOccurrence(slot.at());
        }
    }

    /** 키의 기록을 모두 지운다. */
    public synchronized void reset(String key) {
        entries.remove(key);
    }

    synchronized int size() {
        return entries.size();
    }
}
