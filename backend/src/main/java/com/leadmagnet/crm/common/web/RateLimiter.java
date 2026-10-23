package com.leadmagnet.crm.common.web;

import java.time.Clock;
import java.util.Map;

/**
 * 키별 인메모리 토큰 버킷 (ADR 0019). 버킷 크기는 분당 한도와 같고, 60초에 한도만큼 고르게 다시 채운다.
 * 키 수는 {@code maxKeys}를 넘지 않는다: {@link BoundedLruMap}으로 가장 오래 안 쓴 키부터 잊는다(LRU).
 * 잊힌 키는 다음 요청 때 가득 찬 버킷으로 다시 시작한다. 그래서 키를 상한보다 많이 바꿔 가며 보내는 공격은
 * 이 제한만으로 막지 못한다(메모리를 지키는 쪽을 택했다). 서버 인스턴스마다 따로 센다.
 * 현재 시각은 Clock으로 얻어 테스트가 시간을 옮길 수 있다. 시계가 뒤로 가면 채우지 않는다.
 */
public final class RateLimiter {

    private static final double WINDOW_MILLIS = 60_000d;

    /** 부동소수 오차로 Retry-After가 1초 늘어나지 않게 올림 전에 빼는 값. */
    private static final double EPSILON = 1e-9;

    private final int permitsPerMinute;
    private final Clock clock;
    private final Map<String, Bucket> buckets;

    public RateLimiter(int permitsPerMinute, int maxKeys, Clock clock) {
        if (permitsPerMinute < 1 || maxKeys < 1) {
            throw new IllegalArgumentException("분당 한도와 키 상한은 1 이상이어야 합니다");
        }
        this.permitsPerMinute = permitsPerMinute;
        this.clock = clock;
        this.buckets = new BoundedLruMap<>(maxKeys);
    }

    /** 토큰 하나를 쓴다. 통과면 0, 아니면 다음 토큰이 채워질 때까지의 초(1 이상, Retry-After 값). */
    public synchronized long tryAcquire(String key) {
        long now = clock.millis();
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(permitsPerMinute, now));
        if (now > bucket.updatedAt) {
            bucket.tokens = Math.min(permitsPerMinute, bucket.tokens + (now - bucket.updatedAt) * permitsPerMinute / WINDOW_MILLIS);
            bucket.updatedAt = now;
        }
        if (bucket.tokens >= 1) {
            bucket.tokens -= 1;
            return 0;
        }
        double waitSeconds = (1 - bucket.tokens) * WINDOW_MILLIS / permitsPerMinute / 1000d;
        return Math.max(1, (long) Math.ceil(waitSeconds - EPSILON));
    }

    synchronized int size() {
        return buckets.size();
    }

    private static final class Bucket {

        double tokens;
        long updatedAt;

        Bucket(double tokens, long updatedAt) {
            this.tokens = tokens;
            this.updatedAt = updatedAt;
        }
    }
}
