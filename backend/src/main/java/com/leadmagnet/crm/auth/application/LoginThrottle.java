package com.leadmagnet.crm.auth.application;

import com.leadmagnet.crm.auth.config.LoginThrottleProperties;
import com.leadmagnet.crm.auth.domain.Operator;
import com.leadmagnet.crm.common.error.ErrorCode;
import com.leadmagnet.crm.common.error.TooManyRequestsException;
import com.leadmagnet.crm.common.web.RejectionTally;
import com.leadmagnet.crm.common.web.SlidingWindowLimiter;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 로그인 시도 제한 (ADR 0021). 실패 수를 세 가지 키의 슬라이딩 윈도로 센다(인메모리, 인스턴스별).
 * <ul>
 *   <li>(정규화한 이메일, 클라이언트 IP) 쌍: 기본 5회. 보통 여기서 잠긴다. 공격자가 운영자 이메일로 틀려도 그 공격자 IP에서만
 *       잠기므로 운영자는 다른 곳에서 로그인할 수 있다(이메일만으로 잠그면 이메일을 아는 사람 누구나 운영자를 막을 수 있다)</li>
 *   <li>이메일 전체(모든 IP 합산): 기본 50회. 여러 IP로 나눠 대입할 때만 닿는 상한</li>
 *   <li>IP: 기본 20회(이메일 무관)</li>
 * </ul>
 * 계정이 있든 없든 같은 키·같은 규칙이라 429 여부로 계정 존재를 알 수 없다. 하나라도 꽉 찼으면 비밀번호를 비교하지 않고
 * 429 too_many_login_attempts + Retry-After(걸린 한도 중 가장 긴 대기)다. 거절은 실패로 세지 않는다.
 * 성공하면 그 쌍의 기록을 지우고, 이메일 전체·IP에서는 그 시도의 자리만 돌려준다(성공으로 분산 대입 기록을 지우지 않는다).
 * 거절은 요청마다 기록하지 않고 1분 단위 건수 한 줄만 남긴다({@link RejectionTally}). 이메일·IP·비밀번호는 로그에 없다.
 * 시도마다 {@link #begin}으로 자리를 먼저 잡고 결과에 따라 {@link Attempt}의 메서드 하나를 부른다.
 */
@Component
public class LoginThrottle {

    public static final String SCOPE_EMAIL_IP = "email_ip";
    public static final String SCOPE_EMAIL = "email";
    public static final String SCOPE_IP = "ip";

    private static final Logger log = LoggerFactory.getLogger(LoginThrottle.class);

    private final SlidingWindowLimiter byEmailIp;
    private final SlidingWindowLimiter byEmail;
    private final SlidingWindowLimiter byIp;
    private final RejectionTally rejections;
    private final long windowMinutes;

    public LoginThrottle(LoginThrottleProperties properties, Clock clock) {
        this.byEmailIp = new SlidingWindowLimiter(properties.emailIpMaxFailures(), properties.window(),
            properties.maxKeys(), clock);
        this.byEmail = new SlidingWindowLimiter(properties.emailMaxFailures(), properties.window(), properties.maxKeys(),
            clock);
        this.byIp = new SlidingWindowLimiter(properties.ipMaxFailures(), properties.window(), properties.maxKeys(), clock);
        this.rejections = new RejectionTally(log, "로그인 시도 제한 거절: 지난 기록 이후 {}건", clock);
        this.windowMinutes = properties.window().toMinutes();
    }

    public long windowMinutes() {
        return windowMinutes;
    }

    /**
     * 시도를 시작한다. 세 한도 모두에서 자리를 잡는다.
     *
     * @throws TooManyRequestsException 하나라도 창이 꽉 찼으면 429 too_many_login_attempts(잡은 자리는 돌려준다)
     */
    public Attempt begin(String email, String ip) {
        String emailKey = Operator.normalizeEmail(email);
        String ipKey = ip == null || ip.isBlank() ? "unknown" : ip;
        String pairKey = emailKey + "|" + ipKey;
        SlidingWindowLimiter.Slot pairSlot = byEmailIp.tryAcquire(pairKey);
        SlidingWindowLimiter.Slot emailSlot = byEmail.tryAcquire(emailKey);
        SlidingWindowLimiter.Slot ipSlot = byIp.tryAcquire(ipKey);
        long retryAfter = Math.max(pairSlot.retryAfterSeconds(),
            Math.max(emailSlot.retryAfterSeconds(), ipSlot.retryAfterSeconds()));
        if (retryAfter > 0) {
            byEmailIp.release(pairKey, pairSlot);
            byEmail.release(emailKey, emailSlot);
            byIp.release(ipKey, ipSlot);
            rejections.reject();
            rejections.flushIfDue();
            throw new TooManyRequestsException(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS, retryAfter);
        }
        rejections.flushIfDue();
        return new Attempt(pairKey, pairSlot, emailKey, emailSlot, ipKey, ipSlot);
    }

    /** 자리를 잡은 시도 하나. 결과 메서드는 한 번만 부른다. */
    public final class Attempt {

        private final String pairKey;
        private final SlidingWindowLimiter.Slot pairSlot;
        private final String emailKey;
        private final SlidingWindowLimiter.Slot emailSlot;
        private final String ipKey;
        private final SlidingWindowLimiter.Slot ipSlot;

        private Attempt(String pairKey, SlidingWindowLimiter.Slot pairSlot, String emailKey,
                SlidingWindowLimiter.Slot emailSlot, String ipKey, SlidingWindowLimiter.Slot ipSlot) {
            this.pairKey = pairKey;
            this.pairSlot = pairSlot;
            this.emailKey = emailKey;
            this.emailSlot = emailSlot;
            this.ipKey = ipKey;
            this.ipSlot = ipSlot;
        }

        /** 로그인 성공: 그 쌍의 기록을 지우고, 이메일 전체·IP 자리는 돌려준다. */
        public void succeeded() {
            byEmailIp.reset(pairKey);
            byEmail.release(emailKey, emailSlot);
            byIp.release(ipKey, ipSlot);
        }

        /**
         * 자격 증명 실패: 잡은 자리가 실패로 남는다.
         *
         * @return 이 실패로 잠금이 시작된 범위({@link #SCOPE_EMAIL_IP}, {@link #SCOPE_EMAIL}, {@link #SCOPE_IP} 순). 없으면 빈 목록
         */
        public List<String> failed() {
            List<String> locked = new ArrayList<>(3);
            if (pairSlot.lastSlot()) {
                locked.add(SCOPE_EMAIL_IP);
            }
            if (emailSlot.lastSlot()) {
                locked.add(SCOPE_EMAIL);
            }
            if (ipSlot.lastSlot()) {
                locked.add(SCOPE_IP);
            }
            if (!locked.isEmpty()) {
                log.warn("로그인 시도 제한 시작: 범위={}, 창={}분", locked, windowMinutes);
            }
            return List.copyOf(locked);
        }

        /** 자격 증명과 무관한 이유(서버 오류 등)로 끝남: 모든 자리를 돌려준다. */
        public void abandoned() {
            byEmailIp.release(pairKey, pairSlot);
            byEmail.release(emailKey, emailSlot);
            byIp.release(ipKey, ipSlot);
        }
    }
}
