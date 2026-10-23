package com.leadmagnet.crm.auth.application;

import com.leadmagnet.crm.auth.config.LoginThrottleProperties;
import com.leadmagnet.crm.auth.domain.Operator;
import com.leadmagnet.crm.common.crypto.PiiHasher;
import com.leadmagnet.crm.common.error.ErrorCode;
import com.leadmagnet.crm.common.error.TooManyRequestsException;
import com.leadmagnet.crm.common.web.BoundedLruMap;
import com.leadmagnet.crm.common.web.RejectionTally;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 로그인 시도 제한 (ADR 0021). 실패 수를 세 가지 키의 슬라이딩 윈도로 센다. 기록은 PostgreSQL {@code login_failures}(V13)에
 * 두므로 같은 DB를 쓰는 서버 여러 대가 한 카운터를 나눠 쓰고 재시작해도 잠금이 남는다.
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
 *
 * <p>테이블에는 원문 대신 {@code HMAC(LEAD_HMAC_KEY, "login-throttle:{범위}:{값}")}만 둔다. 접두어로 신청 중복 판정 해시와
 * 값이 겹치지 않게 하고, 키 있는 해시라 DB만 유출돼서는 IP 공간(약 43억 개)을 전수 대입해 되찾을 수 없다.
 * 동시성: 자리 잡기(세 키를 세고 한 행씩 넣기)는 한 트랜잭션에서 세 키의 advisory lock을 잡은 뒤 한다
 * ({@link LoginFailureStore#lock}). 비밀번호 비교는 잠금 밖이다(자리를 이미 잡았으므로). 잠금은 {@code lock-timeout}(기본 2초)
 * 안에 못 잡으면 429다(멈춘 잠금 보유자가 연결 풀을 붙잡지 않게).
 *
 * <p>빠른 거절: DB가 어떤 키를 잠겼다고 판정하면 그 키의 풀리는 시각을 인스턴스 메모리({@link BoundedLruMap}, 키 10,000)에
 * 기억하고, 그때까지 그 키의 시도는 DB에 닿지 않고 남은 초로 429다. 잠긴 키(창이 꽉 참)는 시간이 지나야만 풀리므로 결과가
 * 같다(잠긴 쌍은 성공할 수 없다). 그래서 잠긴 뒤의 대입 폭주가 트랜잭션·잠금 대기로 공유 연결 풀을 묶지 못한다.
 * 기억은 거절 판정만 줄 뿐 통과를 주지 않으므로, 밀려나거나 다른 인스턴스에 없어도 DB 판정으로 돌아갈 뿐이다.
 */
@Component
public class LoginThrottle {

    public static final String SCOPE_EMAIL_IP = "email_ip";
    public static final String SCOPE_EMAIL = "email";
    public static final String SCOPE_IP = "ip";

    private static final String KEY_PREFIX = "login-throttle:";

    /** 빠른 거절에 기억하는 잠긴 키 수 상한. 넘으면 가장 오래 안 쓴 키부터 잊는다(잊으면 DB가 다시 판정한다). */
    static final int MAX_LOCKED_KEYS = 10_000;

    private static final Logger log = LoggerFactory.getLogger(LoginThrottle.class);

    private final int emailIpLimit;
    private final int emailLimit;
    private final int ipLimit;
    private final Duration window;
    private final String lockTimeout;
    private final Clock clock;
    private final LoginFailureStore store;
    private final TransactionTemplate transaction;
    private final PiiHasher hasher;
    private final RejectionTally rejections;
    /** 키 해시 → DB가 판정한 잠금이 풀리는 시각(epoch ms). */
    private final Map<String, Long> lockedUntil = new BoundedLruMap<>(MAX_LOCKED_KEYS);

    public LoginThrottle(LoginThrottleProperties properties, Clock clock, JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager, PiiHasher hasher) {
        this.emailIpLimit = properties.emailIpMaxFailures();
        this.emailLimit = properties.emailMaxFailures();
        this.ipLimit = properties.ipMaxFailures();
        this.window = properties.window();
        this.lockTimeout = properties.lockTimeout().toMillis() + "ms";
        this.clock = clock;
        this.store = new LoginFailureStore(jdbc);
        this.transaction = new TransactionTemplate(transactionManager);
        // READ COMMITTED: 잠금을 기다린 뒤의 SELECT가 앞선 보유자의 커밋을 봐야 한다. REPEATABLE READ 이상이면 스냅샷이 첫 문장
        // (잠금 SELECT, 대기 전)에 잡혀 오래된 수로 한도를 넘겨 통과시킨다. REQUIRES_NEW: 바깥 트랜잭션이 있어도 잠금을 이
        // 트랜잭션 안(세고 넣는 동안)에서만 쥐고 바로 풀어, 비밀번호 비교(BCrypt) 동안 잡고 있지 않게 한다.
        this.transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.hasher = hasher;
        this.rejections = new RejectionTally(log, "로그인 시도 제한 거절: 지난 기록 이후 {}건", clock);
    }

    public long windowMinutes() {
        return window.toMinutes();
    }

    /**
     * 시도를 시작한다. 세 한도 모두에서 자리를 잡는다.
     *
     * @throws TooManyRequestsException 하나라도 창이 꽉 찼으면 429 too_many_login_attempts(자리를 잡지 않는다)
     */
    public Attempt begin(String email, String ip) {
        String emailKey = Operator.normalizeEmail(email);
        String ipKey = ip == null || ip.isBlank() ? "unknown" : ip;
        String pairHash = key(SCOPE_EMAIL_IP, emailKey + "|" + ipKey);
        String emailHash = key(SCOPE_EMAIL, emailKey);
        String ipHash = key(SCOPE_IP, ipKey);
        long remembered = rememberedWait(List.of(pairHash, emailHash, ipHash), clock.millis());
        if (remembered > 0) {
            throw reject(remembered);
        }
        Attempt attempt;
        try {
            attempt = transaction.execute(status -> {
                store.setLockTimeout(lockTimeout);
                store.lock(List.of(pairHash, emailHash, ipHash));
                Instant now = clock.instant();
                Window pair = count(pairHash, emailIpLimit, now);
                Window byEmail = count(emailHash, emailLimit, now);
                Window byIp = count(ipHash, ipLimit, now);
                long retryAfter = Math.max(pair.retryAfterSeconds, Math.max(byEmail.retryAfterSeconds, byIp.retryAfterSeconds));
                if (retryAfter > 0) {
                    remember(pairHash, pair);
                    remember(emailHash, byEmail);
                    remember(ipHash, byIp);
                    return new Attempt(retryAfter);
                }
                return new Attempt(pairHash, store.insert(pairHash, now), pair.lastSlot,
                    store.insert(emailHash, now), byEmail.lastSlot, store.insert(ipHash, now), byIp.lastSlot);
            });
        } catch (DataAccessException e) {
            if (!isLockTimeout(e)) {
                throw e;
            }
            // 잠금을 lock-timeout 안에 못 잡았다: 같은 키로 시도가 몰리고 있다. 잠금 판정은 아니므로 기억하지 않고 짧게 거절한다.
            throw reject(1);
        }
        if (attempt.retryAfterSeconds > 0) {
            throw reject(attempt.retryAfterSeconds);
        }
        rejections.flushIfDue();
        return attempt;
    }

    /**
     * PostgreSQL lock_timeout(SQLSTATE 55P03 lock_not_available)인가. Spring의 PostgreSQL 오류 코드 번역은 이 상태를
     * 분류하지 않아(UncategorizedSQLException) 원인 SQLException의 상태로 본다.
     */
    static boolean isLockTimeout(DataAccessException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && "55P03".equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    private TooManyRequestsException reject(long retryAfterSeconds) {
        rejections.reject();
        rejections.flushIfDue();
        return new TooManyRequestsException(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS, retryAfterSeconds);
    }

    /** 기억한 잠금 중 가장 긴 남은 대기(초, 1 이상). 없으면 0. 지난 기억은 지운다. */
    private long rememberedWait(List<String> keyHashes, long nowMillis) {
        long until = 0;
        synchronized (lockedUntil) {
            for (String keyHash : keyHashes) {
                Long at = lockedUntil.get(keyHash);
                if (at == null) {
                    continue;
                }
                if (at <= nowMillis) {
                    lockedUntil.remove(keyHash);
                } else {
                    until = Math.max(until, at);
                }
            }
        }
        return until == 0 ? 0 : Math.max(1, (until - nowMillis + 999) / 1000);
    }

    private void remember(String keyHash, Window window) {
        if (window.retryAfterSeconds > 0) {
            synchronized (lockedUntil) {
                lockedUntil.put(keyHash, window.freesAtMillis);
            }
        }
    }

    /** 창이 지난 기록을 지운다(정리 스케줄러). 지운 행 수. */
    public int purgeExpired() {
        return store.deleteUpTo(clock.instant().minus(window));
    }

    private String key(String scope, String value) {
        return hasher.hash(KEY_PREFIX + scope + ":" + value);
    }

    /**
     * 창 안의 기록 수로 한도를 본다. 꽉 찼으면 대기 초: 창 안 기록이 한도 아래로 내려갈 때(오래된 쪽에서 count - limit + 1번째
     * 기록이 창을 벗어날 때)까지, 1 이상. 아니면 이번 자리로 꽉 차는지(lastSlot).
     */
    private Window count(String keyHash, int limit, Instant now) {
        List<Instant> recent = store.recent(keyHash, now.minus(window));
        if (recent.size() >= limit) {
            Instant freesAt = recent.get(recent.size() - limit).plus(window);
            long waitMillis = freesAt.toEpochMilli() - now.toEpochMilli();
            return new Window(Math.max(1, (waitMillis + 999) / 1000), false, freesAt.toEpochMilli());
        }
        return new Window(0, recent.size() + 1 == limit, 0);
    }

    /** freesAtMillis: 잠겼으면(retryAfterSeconds > 0) 풀리는 시각. */
    private record Window(long retryAfterSeconds, boolean lastSlot, long freesAtMillis) {
    }

    /** 자리를 잡은 시도 하나. 결과 메서드는 한 번만 부른다. */
    public final class Attempt {

        private final long retryAfterSeconds;
        private final String pairHash;
        private final long pairId;
        private final boolean pairLast;
        private final long emailId;
        private final boolean emailLast;
        private final long ipId;
        private final boolean ipLast;

        private Attempt(long retryAfterSeconds) {
            this(retryAfterSeconds, null, 0, false, 0, false, 0, false);
        }

        private Attempt(String pairHash, long pairId, boolean pairLast, long emailId, boolean emailLast, long ipId,
                boolean ipLast) {
            this(0, pairHash, pairId, pairLast, emailId, emailLast, ipId, ipLast);
        }

        private Attempt(long retryAfterSeconds, String pairHash, long pairId, boolean pairLast, long emailId,
                boolean emailLast, long ipId, boolean ipLast) {
            this.retryAfterSeconds = retryAfterSeconds;
            this.pairHash = pairHash;
            this.pairId = pairId;
            this.pairLast = pairLast;
            this.emailId = emailId;
            this.emailLast = emailLast;
            this.ipId = ipId;
            this.ipLast = ipLast;
        }

        /** 로그인 성공: 그 쌍의 기록을 지우고, 이메일 전체·IP 자리는 돌려준다. */
        public void succeeded() {
            release(() -> {
                store.deleteKey(pairHash);
                store.delete(emailId);
                store.delete(ipId);
            });
        }

        /**
         * 자격 증명 실패: 잡은 자리가 실패로 남는다.
         *
         * @return 이 실패로 잠금이 시작된 범위({@link #SCOPE_EMAIL_IP}, {@link #SCOPE_EMAIL}, {@link #SCOPE_IP} 순). 없으면 빈 목록
         */
        public List<String> failed() {
            List<String> locked = new ArrayList<>(3);
            if (pairLast) {
                locked.add(SCOPE_EMAIL_IP);
            }
            if (emailLast) {
                locked.add(SCOPE_EMAIL);
            }
            if (ipLast) {
                locked.add(SCOPE_IP);
            }
            if (!locked.isEmpty()) {
                log.warn("로그인 시도 제한 시작: 범위={}, 창={}분", locked, windowMinutes());
            }
            return List.copyOf(locked);
        }

        /** 자격 증명과 무관한 이유(서버 오류 등)로 끝남: 모든 자리를 돌려준다. */
        public void abandoned() {
            release(() -> {
                store.delete(pairId);
                store.delete(emailId);
                store.delete(ipId);
            });
        }

        /**
         * 자리 반환은 잠금 없이 한 트랜잭션으로 지운다(지우기는 수를 줄이기만 하므로 한도를 넘기지 않는다). 실패해도 로그인 결과를
         * 바꾸지 않는다: 자리가 실패로 남는 보수적인 쪽이고, 창이 지나면 정리된다.
         */
        private void release(Runnable deletes) {
            try {
                transaction.executeWithoutResult(status -> deletes.run());
            } catch (DataAccessException e) {
                log.warn("로그인 시도 제한 자리 반환 실패: 원인={}", e.getClass().getName());
            }
        }
    }
}
