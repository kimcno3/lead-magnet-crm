package com.leadmagnet.crm.common.crypto;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 기동 시 현재 LEAD_ENC_KEY·LEAD_HMAC_KEY가 기존 데이터를 만든 키와 같은지 확인한다 (ADR 0015).
 * 처음 기동하면 정해진 표식 값을 현재 키로 암호화·HMAC해 {@code pii_key_check}에 한 행을 남기고,
 * 다음 기동부터는 그 행을 현재 키로 확인해 다르면 기동을 멈춘다. 키가 섞인 채 데이터를 쓰지 않게 하려는 것이다.
 * 오류 메시지와 로그에는 키 값을 싣지 않는다.
 *
 * <p>실행 시점: 모든 싱글턴 빈이 만들어진 직후({@link SmartInitializingSingleton}), 웹 서버 커넥터가 요청을 받기 시작하기
 * (컨텍스트 refresh 마지막 단계) 전이다. 그래서 틀린 키로 신청이 저장되는 순간이 없다. Flyway 마이그레이션은 Spring Boot의
 * Flyway 초기화 빈(JPA EntityManagerFactory가 의존)에서 이미 끝났으므로 {@code pii_key_check} 테이블이 있다.
 * 키가 다르면 예외로 컨텍스트 refresh가 실패해 기동이 멈춘다.
 */
@Component
public class PiiKeyCheck implements SmartInitializingSingleton {

    static final String MARKER = "lead-magnet-crm:pii-key-check";
    static final String COLUMN = "pii_key_check";

    private static final Logger log = LoggerFactory.getLogger(PiiKeyCheck.class);
    private static final String ROTATION_HINT = " 키를 되돌리거나 키 교체 절차(ROADMAP)를 따르세요";

    private final JdbcTemplate jdbc;
    private final PiiCipher cipher;
    private final PiiHasher hasher;

    public PiiKeyCheck(JdbcTemplate jdbc, PiiCipher cipher, PiiHasher hasher) {
        this.jdbc = jdbc;
        this.cipher = cipher;
        this.hasher = hasher;
    }

    @Override
    public void afterSingletonsInstantiated() {
        verify();
    }

    public Result verify() {
        // 여러 인스턴스가 동시에 처음 기동해도 한 행만 남고, 나머지는 그 행으로 확인한다.
        int created = jdbc.update("INSERT INTO pii_key_check (id, enc_check, hmac_check) VALUES (1, ?, ?) "
            + "ON CONFLICT (id) DO NOTHING", cipher.encrypt(MARKER, COLUMN), hasher.hash(MARKER));
        if (created == 1) {
            log.info("개인정보 키 확인 행을 만들었습니다");
            return Result.CREATED;
        }
        List<Stored> rows = jdbc.query("SELECT enc_check, hmac_check FROM pii_key_check WHERE id = 1",
            (rs, i) -> new Stored(rs.getBytes("enc_check"), rs.getString("hmac_check")));
        Stored stored = rows.getFirst();
        if (!encKeyMatches(stored.enc())) {
            throw new KeyMismatchException("LEAD_ENC_KEY가 기존 데이터를 암호화한 키와 다릅니다." + ROTATION_HINT);
        }
        if (!hasher.hash(MARKER).equals(stored.hmac())) {
            throw new KeyMismatchException(
                "LEAD_HMAC_KEY가 기존 데이터의 중복 판정 해시를 만든 키와 다릅니다." + ROTATION_HINT);
        }
        log.info("개인정보 키 확인: 기존 데이터와 같은 키");
        return Result.VERIFIED;
    }

    private boolean encKeyMatches(byte[] enc) {
        try {
            return MARKER.equals(cipher.decrypt(enc, COLUMN));
        } catch (PiiCryptoException e) {
            return false;
        }
    }

    public enum Result {
        CREATED, VERIFIED
    }

    private record Stored(byte[] enc, String hmac) {
    }

    /** 현재 키가 기존 데이터의 키와 다르다. 기동을 멈춘다. */
    public static class KeyMismatchException extends IllegalStateException {
        public KeyMismatchException(String message) {
            super(message);
        }
    }
}
