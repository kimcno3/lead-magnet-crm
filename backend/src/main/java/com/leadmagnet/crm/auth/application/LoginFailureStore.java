package com.leadmagnet.crm.auth.application;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 로그인 실패 기록 테이블({@code login_failures}, V13) 접근 (ADR 0021 변경 기록). 키는 이미 HMAC한 hex 값만 받는다.
 * 같은 키의 "세기 → 자리 넣기"는 {@link #lock}이 잡는 트랜잭션 범위 advisory lock 안에서 해야 동시 시도가 한도를 넘지 않는다.
 */
final class LoginFailureStore {

    /** advisory lock 두 정수 형식의 첫 값. 이 애플리케이션에서 로그인 시도 제한만 쓴다("LMLT"의 ASCII). */
    static final int LOCK_CLASS = 0x4C4D_4C54;

    private final JdbcTemplate jdbc;

    LoginFailureStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 키마다 {@code pg_advisory_xact_lock(LOCK_CLASS, 키 앞 32비트)}를 잡는다(커밋·롤백 때 풀린다). 여러 키를 늘 같은 순서
     * (잠금 값 오름차순)로 잡아 두 트랜잭션이 서로를 기다리는 교착을 막는다. 키는 HMAC 출력이라 앞 32비트가 고르게 퍼진다.
     * 다른 키가 같은 잠금 값을 가지면 줄만 함께 설 뿐 결과는 같다.
     */
    void lock(List<String> keyHashes) {
        keyHashes.stream().map(LoginFailureStore::lockId).distinct().sorted(Comparator.naturalOrder())
            .forEach(id -> jdbc.query("SELECT pg_advisory_xact_lock(?, ?)", rs -> { }, LOCK_CLASS, id));
    }

    /** 이 트랜잭션에서만 잠금 대기 상한을 둔다({@code SET LOCAL lock_timeout}). 넘으면 55P03 → CannotAcquireLockException. */
    void setLockTimeout(String timeout) {
        jdbc.query("SELECT set_config('lock_timeout', ?, true)", rs -> { }, timeout);
    }

    static int lockId(String keyHash) {
        return (int) Long.parseLong(keyHash.substring(0, 8), 16);
    }

    /** 창 안(since 이후)의 기록 시각을 오래된 순으로. */
    List<Instant> recent(String keyHash, Instant since) {
        return jdbc.query(
            "SELECT failed_at FROM login_failures WHERE key_hash = ? AND failed_at > ? ORDER BY failed_at",
            (rs, i) -> rs.getObject(1, OffsetDateTime.class).toInstant(), keyHash, utc(since));
    }

    long insert(String keyHash, Instant at) {
        return jdbc.queryForObject("INSERT INTO login_failures (key_hash, failed_at) VALUES (?, ?) RETURNING id",
            Long.class, keyHash, utc(at));
    }

    void delete(long id) {
        jdbc.update("DELETE FROM login_failures WHERE id = ?", id);
    }

    void deleteKey(String keyHash) {
        jdbc.update("DELETE FROM login_failures WHERE key_hash = ?", keyHash);
    }

    int deleteUpTo(Instant cutoff) {
        return jdbc.update("DELETE FROM login_failures WHERE failed_at <= ?", utc(cutoff));
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
