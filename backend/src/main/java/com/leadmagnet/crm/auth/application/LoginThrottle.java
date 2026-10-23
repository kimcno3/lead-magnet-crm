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
 * 로그인 시도 제한 (ADR 0021). 정규화한 이메일별·클라이언트 IP별 실패 수를 슬라이딩 윈도로 센다(인메모리, 인스턴스별).
 * <ul>
 *   <li>계정이 있든 없든 같은 키(정규화한 이메일)·같은 규칙이다. 그래서 429 여부로 계정 존재를 알 수 없다</li>
 *   <li>한도에 닿으면 비밀번호를 비교하지 않고 429 too_many_login_attempts + Retry-After다. 거절은 실패로 세지 않는다</li>
 *   <li>성공하면 그 이메일의 기록을 지운다. IP 기록은 남긴다(성공한 시도 자체는 IP 실패로 세지 않는다)</li>
 *   <li>거절은 요청마다 기록하지 않고 1분 단위 건수 한 줄만 남긴다({@link RejectionTally}). 이메일·IP·비밀번호는 로그에 없다</li>
 * </ul>
 * 시도마다 {@link #begin}으로 자리를 먼저 잡고 결과에 따라 {@link Attempt}의 메서드 하나를 부른다.
 */
@Component
public class LoginThrottle {

    public static final String SCOPE_EMAIL = "email";
    public static final String SCOPE_IP = "ip";

    private static final Logger log = LoggerFactory.getLogger(LoginThrottle.class);

    private final SlidingWindowLimiter byEmail;
    private final SlidingWindowLimiter byIp;
    private final RejectionTally rejections;
    private final long windowMinutes;

    public LoginThrottle(LoginThrottleProperties properties, Clock clock) {
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
     * 시도를 시작한다. IP 한도를 먼저 보고 이메일 한도를 본다.
     *
     * @throws TooManyRequestsException 둘 중 하나라도 창이 꽉 찼으면 429 too_many_login_attempts
     */
    public Attempt begin(String email, String ip) {
        String emailKey = Operator.normalizeEmail(email);
        String ipKey = ip == null || ip.isBlank() ? "unknown" : ip;
        SlidingWindowLimiter.Slot ipSlot = byIp.tryAcquire(ipKey);
        if (!ipSlot.allowed()) {
            throw reject(ipSlot.retryAfterSeconds());
        }
        SlidingWindowLimiter.Slot emailSlot = byEmail.tryAcquire(emailKey);
        if (!emailSlot.allowed()) {
            byIp.release(ipKey, ipSlot);
            throw reject(emailSlot.retryAfterSeconds());
        }
        rejections.flushIfDue();
        return new Attempt(emailKey, emailSlot, ipKey, ipSlot);
    }

    private TooManyRequestsException reject(long retryAfterSeconds) {
        rejections.reject();
        rejections.flushIfDue();
        return new TooManyRequestsException(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS, retryAfterSeconds);
    }

    /** 자리를 잡은 시도 하나. 결과 메서드는 한 번만 부른다. */
    public final class Attempt {

        private final String emailKey;
        private final SlidingWindowLimiter.Slot emailSlot;
        private final String ipKey;
        private final SlidingWindowLimiter.Slot ipSlot;

        private Attempt(String emailKey, SlidingWindowLimiter.Slot emailSlot, String ipKey,
                SlidingWindowLimiter.Slot ipSlot) {
            this.emailKey = emailKey;
            this.emailSlot = emailSlot;
            this.ipKey = ipKey;
            this.ipSlot = ipSlot;
        }

        /** 로그인 성공: 이메일 기록을 지우고 IP 자리는 돌려준다. */
        public void succeeded() {
            byEmail.reset(emailKey);
            byIp.release(ipKey, ipSlot);
        }

        /**
         * 자격 증명 실패: 잡은 자리가 실패로 남는다.
         *
         * @return 이 실패로 잠금이 시작된 범위({@link #SCOPE_EMAIL}, {@link #SCOPE_IP}). 없으면 빈 목록
         */
        public List<String> failed() {
            List<String> locked = new ArrayList<>(2);
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

        /** 자격 증명과 무관한 이유(서버 오류 등)로 끝남: 두 자리 모두 돌려준다. */
        public void abandoned() {
            byEmail.release(emailKey, emailSlot);
            byIp.release(ipKey, ipSlot);
        }
    }
}
