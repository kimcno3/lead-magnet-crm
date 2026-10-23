package com.leadmagnet.crm.lead.config;

import com.leadmagnet.crm.common.web.RateLimiter;
import com.leadmagnet.crm.tracking.api.VisitorIdCookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 신청 제출의 IP·방문자 쿠키 rate limit (ADR 0019). IP는 {@code getRemoteAddr()}만 쓴다(X-Forwarded-For는 읽지 않는다,
 * ADR 0016과 같은 이유). 거절은 요청마다 기록하지 않는다(ADR 0016 결과 절): 감사 로그에 쓰지 않고, 애플리케이션 로그에도
 * 1분에 한 줄씩 그동안의 거절 건수만 남긴다(IP·쿠키 값은 남기지 않는다).
 */
public class SubmissionRateLimits {

    private static final Logger log = LoggerFactory.getLogger(SubmissionRateLimits.class);
    static final Duration LOG_INTERVAL = Duration.ofMinutes(1);

    private final RateLimiter byIp;
    private final RateLimiter byVisitor;
    private final VisitorIdCookie visitorCookie;
    private final Clock clock;
    private final AtomicLong rejected = new AtomicLong();
    private long lastLoggedAt = Long.MIN_VALUE;

    public SubmissionRateLimits(SubmissionRateLimitProperties properties, VisitorIdCookie visitorCookie, Clock clock) {
        this.byIp = new RateLimiter(properties.ipPerMinute(), properties.maxKeys(), clock);
        this.byVisitor = new RateLimiter(properties.visitorPerMinute(), properties.maxKeys(), clock);
        this.visitorCookie = visitorCookie;
        this.clock = clock;
    }

    /** 통과면 0, 거절이면 Retry-After 초. IP 한도를 먼저 보고, 통과하면 방문자 쿠키 한도를 본다. */
    public long check(HttpServletRequest request) {
        long retryAfter = byIp.tryAcquire(request.getRemoteAddr());
        if (retryAfter == 0) {
            Optional<UUID> visitor = visitorCookie.read(request);
            if (visitor.isPresent()) {
                retryAfter = byVisitor.tryAcquire(visitor.get().toString());
            }
        }
        if (retryAfter > 0) {
            countRejection();
        }
        return retryAfter;
    }

    /** 거절 건수를 모으고, 마지막 기록에서 1분이 지났으면 그동안의 건수를 한 줄로 남긴다. */
    private synchronized void countRejection() {
        rejected.incrementAndGet();
        long now = clock.millis();
        if (lastLoggedAt == Long.MIN_VALUE || now - lastLoggedAt >= LOG_INTERVAL.toMillis()) {
            lastLoggedAt = now;
            log.info("신청 제출 rate limit 거절: 지난 기록 이후 {}건", rejected.getAndSet(0));
        }
    }
}
