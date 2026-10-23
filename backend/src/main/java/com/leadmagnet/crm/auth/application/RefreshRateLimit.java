package com.leadmagnet.crm.auth.application;

import com.leadmagnet.crm.common.error.ErrorCode;
import com.leadmagnet.crm.common.error.TooManyRequestsException;
import com.leadmagnet.crm.common.web.RateLimiter;
import com.leadmagnet.crm.common.web.RejectionTally;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * {@code POST /api/admin/auth/refresh}의 클라이언트 IP별 rate limit (ADR 0021 변경 기록). 분당 {@code app.auth.refresh-rate-limit}
 * (기본 30)회의 토큰 버킷({@link RateLimiter}, 인메모리·인스턴스별)이고, 쿠키를 보기 전에 센다(성공·401 모두 한 번).
 * 넘으면 429 too_many_requests + Retry-After. IP는 {@code ClientIpFilter}가 정한 {@code getRemoteAddr()}다.
 * 거절은 1분 단위 건수 한 줄만 남긴다({@link RejectionTally}, IP는 남기지 않는다).
 */
@Component
public class RefreshRateLimit {

    static final int MAX_KEYS = 10_000;

    private static final Logger log = LoggerFactory.getLogger(RefreshRateLimit.class);

    private final RateLimiter byIp;
    private final RejectionTally rejections;

    public RefreshRateLimit(@Value("${app.auth.refresh-rate-limit:30}") int perMinute, Clock clock) {
        if (perMinute < 1) {
            throw new IllegalArgumentException("app.auth.refresh-rate-limit는 1 이상이어야 합니다");
        }
        this.byIp = new RateLimiter(perMinute, MAX_KEYS, clock);
        this.rejections = new RejectionTally(log, "refresh rate limit 거절: 지난 기록 이후 {}건", clock);
    }

    /** @throws TooManyRequestsException 이 IP의 분당 한도를 넘었으면 */
    public void check(String ip) {
        long retryAfter = byIp.tryAcquire(ip == null || ip.isBlank() ? "unknown" : ip);
        if (retryAfter > 0) {
            rejections.reject();
        }
        rejections.flushIfDue();
        if (retryAfter > 0) {
            throw new TooManyRequestsException(ErrorCode.TOO_MANY_REQUESTS, retryAfter);
        }
    }
}
