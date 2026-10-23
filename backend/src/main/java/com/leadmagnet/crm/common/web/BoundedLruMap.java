package com.leadmagnet.crm.common.web;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 키 수 상한이 있는 접근 순서 맵(LRU). 상한을 넘으면 가장 오래 안 쓴 키부터 잊는다. 인메모리 제한기({@link RateLimiter},
 * 제출·/refresh rate limit)가 메모리를 지키려고 쓴다(ADR 0019, 0021). 동기화하지 않는다.
 */
final class BoundedLruMap<K, V> extends LinkedHashMap<K, V> {

    private final int maxKeys;

    BoundedLruMap(int maxKeys) {
        super(16, 0.75f, true);
        if (maxKeys < 1) {
            throw new IllegalArgumentException("키 상한은 1 이상이어야 합니다");
        }
        this.maxKeys = maxKeys;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > maxKeys;
    }
}
