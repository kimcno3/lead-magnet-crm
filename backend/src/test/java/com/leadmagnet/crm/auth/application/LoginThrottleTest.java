package com.leadmagnet.crm.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.leadmagnet.crm.auth.config.LoginThrottleProperties;
import com.leadmagnet.crm.common.crypto.PiiHasher;
import com.leadmagnet.crm.common.error.ErrorCode;
import com.leadmagnet.crm.common.error.TooManyRequestsException;
import com.leadmagnet.crm.support.DatabaseCleaner;
import com.leadmagnet.crm.support.IntegrationTest;
import com.leadmagnet.crm.support.TestClock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 로그인 시도 제한의 DB 저장소 (ADR 0021, 2026-10-24 v0.4.0 변경). 한도: (이메일, IP) 쌍 3, 이메일 전체 6, IP 5, 창 15분.
 * 인스턴스는 테스트가 직접 만든다(같은 DB를 쓰는 서버 여러 대·재시작을 흉내 낸다). 시계는 실제 시각보다 하루 뒤에서 시작해
 * 공유 컨텍스트의 정리 스케줄러(실제 시각 기준)가 이 테스트의 행을 지우지 않게 한다.
 */
@IntegrationTest
class LoginThrottleTest {

    static final LoginThrottleProperties LIMITS = new LoginThrottleProperties(3, 6, 5, Duration.ofMinutes(15));

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    PiiHasher hasher;

    final TestClock clock = new TestClock(Instant.now().plus(Duration.ofDays(1)).truncatedTo(ChronoUnit.SECONDS));
    LoginThrottle throttle;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.truncateAll(jdbc);
        throttle = newInstance(LIMITS);
    }

    LoginThrottle newInstance(LoginThrottleProperties limits) {
        return new LoginThrottle(limits, clock, jdbc, transactionManager, hasher);
    }

    void fail(String email, String ip) {
        throttle.begin(email, ip).failed();
    }

    int rows() {
        return jdbc.queryForObject("SELECT count(*) FROM login_failures", Integer.class);
    }

    @Test
    void 같은_이메일과_IP의_실패가_한도에_닿으면_그_쌍의_다음_시도는_429와_Retry_After다() {
        fail("a@example.com", "1.1.1.1");
        fail("a@example.com", "1.1.1.1");
        assertThat(throttle.begin("a@example.com", "1.1.1.1").failed()).containsExactly(LoginThrottle.SCOPE_EMAIL_IP);

        assertThatThrownBy(() -> throttle.begin(" A@Example.com ", "1.1.1.1"))
            .isInstanceOfSatisfying(TooManyRequestsException.class, e -> {
                assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TOO_MANY_LOGIN_ATTEMPTS);
                assertThat(e.retryAfterSeconds()).isEqualTo(900);
            });
    }

    @Test
    void 공격자가_한_IP에서_잠가도_운영자는_다른_IP에서_로그인할_수_있다() {
        for (int i = 0; i < 3; i++) {
            fail("ops@example.com", "6.6.6.6");
        }
        assertThatThrownBy(() -> throttle.begin("ops@example.com", "6.6.6.6"))
            .isInstanceOf(TooManyRequestsException.class);

        throttle.begin("ops@example.com", "10.0.0.5").succeeded();
    }

    @Test
    void 여러_IP에_나눠_대입하면_이메일_전체_상한에서_막힌다() {
        for (int i = 0; i < 5; i++) {
            fail("ops@example.com", "7.7.7." + i);
        }
        assertThat(throttle.begin("ops@example.com", "7.7.7.5").failed()).containsExactly(LoginThrottle.SCOPE_EMAIL);

        assertThatThrownBy(() -> throttle.begin("ops@example.com", "7.7.7.99"))
            .isInstanceOf(TooManyRequestsException.class);
        // 다른 이메일은 영향이 없다
        throttle.begin("other@example.com", "7.7.7.99").succeeded();
    }

    @Test
    void 성공하면_그_쌍의_실패_기록을_지운다() {
        fail("a@example.com", "1.1.1.1");
        fail("a@example.com", "1.1.1.1");
        throttle.begin("a@example.com", "1.1.1.1").succeeded();

        fail("a@example.com", "1.1.1.1");
        fail("a@example.com", "1.1.1.1");
        // 초기화되지 않았다면 실패 4번째라 쌍 한도(3)에 걸려 429다
        throttle.begin("a@example.com", "1.1.1.1").succeeded();
    }

    @Test
    void IP별_실패가_한도에_닿으면_이메일과_무관하게_429다() {
        for (int i = 0; i < 4; i++) {
            fail("u" + i + "@example.com", "9.9.9.9");
        }
        assertThat(throttle.begin("u4@example.com", "9.9.9.9").failed()).containsExactly(LoginThrottle.SCOPE_IP);

        assertThatThrownBy(() -> throttle.begin("new@example.com", "9.9.9.9")).isInstanceOf(TooManyRequestsException.class);
        throttle.begin("new@example.com", "8.8.8.8").succeeded();
    }

    @Test
    void 여러_한도에_걸리면_Retry_After는_가장_긴_대기다() {
        // IP 한도(5)를 0분에 2번 채우고, 10분 뒤 같은 IP의 쌍으로 쌍 한도(3)와 IP 한도를 함께 채운다
        for (int i = 0; i < 2; i++) {
            fail("u" + i + "@example.com", "9.9.9.9");
        }
        clock.advance(Duration.ofMinutes(10));
        for (int i = 0; i < 3; i++) {
            fail("a@example.com", "9.9.9.9");
        }

        // IP 기록은 0분에 시작해 5분 뒤 풀리지만, 쌍 기록은 10분에 시작해 15분 뒤 풀린다
        assertThatThrownBy(() -> throttle.begin("a@example.com", "9.9.9.9"))
            .isInstanceOfSatisfying(TooManyRequestsException.class, e -> assertThat(e.retryAfterSeconds()).isEqualTo(900));
    }

    @Test
    void 창이_지나면_다시_시도할_수_있다() {
        for (int i = 0; i < 3; i++) {
            fail("a@example.com", "1.1.1.1");
        }
        clock.advance(Duration.ofMinutes(15));

        throttle.begin("a@example.com", "1.1.1.1").succeeded();
    }

    @Test
    void 거절된_시도와_성공과_중단은_실패로_세지_않는다() {
        for (int i = 0; i < 3; i++) {
            fail("a@example.com", "1.1.1.1");
        }
        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> throttle.begin("a@example.com", "1.1.1.1"))
                .isInstanceOf(TooManyRequestsException.class);
        }
        // IP 한도 5 중 3만 썼다: 거절·성공·중단은 IP 실패로 남지 않는다
        throttle.begin("b@example.com", "1.1.1.1").succeeded();
        throttle.begin("c@example.com", "1.1.1.1").abandoned();
        fail("d@example.com", "1.1.1.1");
        assertThat(throttle.begin("e@example.com", "1.1.1.1").failed()).containsExactly(LoginThrottle.SCOPE_IP);
    }

    @Test
    void 여러_한도가_동시에_닿으면_모두_알린다() {
        LoginThrottle tight = newInstance(new LoginThrottleProperties(1, 1, 1, Duration.ofMinutes(15)));

        assertThat(tight.begin("a@example.com", "1.1.1.1").failed())
            .containsExactly(LoginThrottle.SCOPE_EMAIL_IP, LoginThrottle.SCOPE_EMAIL, LoginThrottle.SCOPE_IP);
        assertThat(tight.windowMinutes()).isEqualTo(15);
    }

    @Test
    void 같은_DB를_쓰는_두_인스턴스는_실패_수를_함께_센다() {
        LoginThrottle other = newInstance(LIMITS);

        throttle.begin("a@example.com", "1.1.1.1").failed();
        other.begin("a@example.com", "1.1.1.1").failed();
        assertThat(throttle.begin("a@example.com", "1.1.1.1").failed()).containsExactly(LoginThrottle.SCOPE_EMAIL_IP);

        assertThatThrownBy(() -> other.begin("a@example.com", "1.1.1.1")).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void 재시작해도_잠금이_유지된다() {
        for (int i = 0; i < 3; i++) {
            fail("a@example.com", "1.1.1.1");
        }
        throttle = null;

        LoginThrottle restarted = newInstance(LIMITS);
        assertThatThrownBy(() -> restarted.begin("a@example.com", "1.1.1.1"))
            .isInstanceOfSatisfying(TooManyRequestsException.class, e -> assertThat(e.retryAfterSeconds()).isEqualTo(900));
    }

    @Test
    void 테이블에는_이메일과_IP_원문이_없고_키는_HMAC_hex다() {
        fail("Secret.Person@Example.com", "203.0.113.45");
        fail("secret.person@example.com", "2001:db8::45");

        List<String> keys = jdbc.queryForList("SELECT key_hash FROM login_failures", String.class);
        assertThat(keys).hasSize(6).allMatch(k -> k.matches("[0-9a-f]{64}"));
        // 이메일 전체 키 하나 + 쌍 두 개 + IP 두 개
        assertThat(keys.stream().distinct()).hasSize(5);
        String dump = jdbc.queryForList("SELECT login_failures::text FROM login_failures", String.class).toString();
        assertThat(dump).doesNotContain("secret", "Secret", "example.com", "203.0.113", "2001:db8");
        // 신청 중복 판정 해시(같은 HMAC 키, 접두어 없음)와도 다르다
        assertThat(keys).doesNotContain(hasher.hashEmail("secret.person@example.com"), hasher.hash("203.0.113.45"));
    }

    @Test
    void 정리는_창이_지난_행만_지운다() {
        fail("a@example.com", "1.1.1.1");
        clock.advance(Duration.ofMinutes(10));
        fail("b@example.com", "2.2.2.2");
        clock.advance(Duration.ofMinutes(5));

        // 첫 실패(3행)는 정확히 창(15분)이 지나 지우고, 두 번째(3행)는 남긴다
        assertThat(throttle.purgeExpired()).isEqualTo(3);
        assertThat(rows()).isEqualTo(3);
        assertThat(throttle.purgeExpired()).isZero();
    }

    @Test
    void 동시에_들어온_실패도_한도보다_많이_비밀번호_비교로_가지_않는다() throws Exception {
        // 두 인스턴스에 나눠 보낸다(서버 두 대). 같은 키의 자리 잡기가 DB 잠금으로 줄을 선다.
        LoginThrottle other = newInstance(LIMITS);
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            LoginThrottle instance = i % 2 == 0 ? throttle : other;
            results.add(pool.submit(() -> {
                start.await();
                try {
                    instance.begin("a@example.com", "1.1.1.1").failed();
                    return true;
                } catch (TooManyRequestsException e) {
                    return false;
                }
            }));
        }
        start.countDown();
        int admitted = 0;
        for (Future<Boolean> result : results) {
            admitted += result.get(30, TimeUnit.SECONDS) ? 1 : 0;
        }
        pool.shutdown();

        assertThat(admitted).isEqualTo(3);
        assertThat(rows()).isEqualTo(9);
    }
}
