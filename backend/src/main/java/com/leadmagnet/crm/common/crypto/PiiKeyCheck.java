package com.leadmagnet.crm.common.crypto;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 기동 시 현재 LEAD_ENC_KEY·LEAD_HMAC_KEY가 기존 데이터를 만든 키와 맞는지 확인하고, HMAC 키 교체를 마친다 (ADR 0015, 0022).
 * 처음 기동하면 정해진 표식 값을 현재 키로 암호화·HMAC해 {@code pii_key_check}에 한 행을 남기고,
 * 다음 기동부터는 그 행으로 확인해 맞지 않으면 기동을 멈춘다. 키가 섞인 채 데이터를 쓰지 않게 하려는 것이다.
 * <ul>
 *   <li>암호화 키: 표식이 알려진 키(현재 또는 LEAD_ENC_PREVIOUS_KEYS) 중 하나로 풀리면 통과한다. 표식은 재암호화 배치가
 *       신청을 모두 다시 암호화한 뒤에 마지막으로 현재 키로 바꾼다({@link #reencryptMarker()}). 그래서 이전 키를 지운 뒤에도
 *       기동한다면 이전 키로 남은 신청이 없다는 뜻이다.</li>
 *   <li>HMAC 키: 표식이 현재 키 해시면 통과. LEAD_HMAC_PREVIOUS_KEY가 있고 표식이 이전 키 해시면 {@link PiiHashRotation}들로
 *       모든 해시를 바꾼 뒤 표식을 마지막에 바꾼다. 중간에 실패하면 표식이 이전 키로 남아 다음 기동이 이어서 끝낸다.</li>
 * </ul>
 * 오류 메시지와 로그에는 키 값을 싣지 않는다.
 *
 * <p>실행 시점: 모든 싱글턴 빈이 만들어진 직후({@link SmartInitializingSingleton}), 웹 서버 커넥터가 요청을 받기 시작하기
 * (컨텍스트 refresh 마지막 단계) 전이다. 그래서 틀린 키나 교체 중인 해시로 신청이 저장되는 순간이 없다. Flyway 마이그레이션은
 * Spring Boot의 Flyway 초기화 빈(JPA EntityManagerFactory가 의존)에서 이미 끝났으므로 {@code pii_key_check} 테이블이 있다.
 * 키가 다르거나 교체가 실패하면 예외로 컨텍스트 refresh가 실패해 기동이 멈춘다.
 */
@Component
public class PiiKeyCheck implements SmartInitializingSingleton {

    static final String MARKER = "lead-magnet-crm:pii-key-check";
    static final String COLUMN = "pii_key_check";

    private static final Logger log = LoggerFactory.getLogger(PiiKeyCheck.class);
    private static final String ROTATION_HINT = " 키를 되돌리거나 키 교체 절차(docs/GUIDE.md)를 따르세요";

    private final JdbcTemplate jdbc;
    private final PiiCipher cipher;
    private final PiiHasher hasher;
    private final PiiHasher previousHasher;
    private final List<PiiHashRotation> rotations;

    @Autowired
    public PiiKeyCheck(JdbcTemplate jdbc, PiiCipher cipher, PiiHasher hasher, PiiCryptoProperties properties,
            List<PiiHashRotation> rotations) {
        this(jdbc, cipher, hasher, properties.hmacPreviousKeyBytes().map(PiiHasher::new).orElse(null), rotations);
    }

    /** 이전 HMAC 키 없이 확인만 한다. */
    public PiiKeyCheck(JdbcTemplate jdbc, PiiCipher cipher, PiiHasher hasher) {
        this(jdbc, cipher, hasher, (PiiHasher) null, List.of());
    }

    /** previousHasher가 null이면 HMAC 키 교체를 하지 않는다. */
    public PiiKeyCheck(JdbcTemplate jdbc, PiiCipher cipher, PiiHasher hasher, PiiHasher previousHasher,
            List<PiiHashRotation> rotations) {
        this.jdbc = jdbc;
        this.cipher = cipher;
        this.hasher = hasher;
        this.previousHasher = previousHasher;
        this.rotations = List.copyOf(rotations);
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
        Stored stored = read();
        if (!encKeyMatches(stored.enc())) {
            throw new KeyMismatchException("LEAD_ENC_KEY가 기존 데이터를 암호화한 키와 다릅니다"
                + "(LEAD_ENC_KEY_ID·LEAD_ENC_PREVIOUS_KEYS 포함 어느 키로도 확인 행을 풀지 못함)." + ROTATION_HINT);
        }
        if (!cipher.isCurrent(stored.enc())) {
            log.info("개인정보 키 확인: 이전 암호화 키로 남은 데이터가 있습니다. 재암호화(POST /api/admin/pii/reencrypt)를 실행하세요");
        }
        String current = hasher.hash(MARKER);
        if (current.equals(stored.hmac())) {
            if (previousHasher != null) {
                log.info("HMAC 키 교체는 이미 끝났습니다. LEAD_HMAC_PREVIOUS_KEY를 지우고 다시 기동하세요");
            }
            log.info("개인정보 키 확인: 기존 데이터와 맞는 키");
            return Result.VERIFIED;
        }
        String previous = previousHasher == null ? null : previousHasher.hash(MARKER);
        if (previous != null && previous.equals(stored.hmac())) {
            rotateHmac(previous, current);
            return Result.HMAC_ROTATED;
        }
        throw new KeyMismatchException("LEAD_HMAC_KEY가 기존 데이터의 중복 판정 해시를 만든 키와 다릅니다. "
            + "HMAC 키를 교체하는 중이면 이전 키를 LEAD_HMAC_PREVIOUS_KEY에 두고 기동하세요." + ROTATION_HINT);
    }

    /** 표식이 현재 암호화 키로 만든 값인가(재암호화가 끝났는가). */
    public boolean markerOnCurrentKey() {
        return cipher.isCurrent(read().enc());
    }

    /**
     * 표식을 현재 암호화 키로 다시 암호화한다. 이미 현재 키면 false. 재암호화 배치가 신청을 모두 바꾼 뒤에만 부른다
     * (그래야 "표식이 현재 키 = 이전 키 데이터 없음"이 성립한다). 호출자 트랜잭션에서 돈다.
     */
    public boolean reencryptMarker() {
        byte[] enc = read().enc();
        if (cipher.isCurrent(enc)) {
            return false;
        }
        byte[] again = cipher.reencrypt(enc, COLUMN);
        return jdbc.update("UPDATE pii_key_check SET enc_check = ? WHERE id = 1 AND enc_check = ?", again, enc) == 1;
    }

    private void rotateHmac(String previousMarker, String currentMarker) {
        log.info("HMAC 키 교체 시작: 중복 판정 해시를 새 키로 다시 계산합니다");
        long total = 0;
        for (PiiHashRotation rotation : rotations) {
            total += rotation.rotate(previousHasher, hasher);
        }
        // 표식은 마지막에 바꾼다. 여기까지 오지 못하면 다음 기동이 이전 키 해시인 행만 골라 이어서 바꾼다.
        jdbc.update("UPDATE pii_key_check SET hmac_check = ? WHERE id = 1 AND hmac_check = ?", currentMarker,
            previousMarker);
        log.info("HMAC 키 교체 완료: 해시를 바꾼 행 {}건. LEAD_HMAC_PREVIOUS_KEY를 지우고 다시 기동하세요", total);
    }

    private Stored read() {
        List<Stored> rows = jdbc.query("SELECT enc_check, hmac_check FROM pii_key_check WHERE id = 1",
            (rs, i) -> new Stored(rs.getBytes("enc_check"), rs.getString("hmac_check")));
        if (rows.isEmpty()) {
            throw new IllegalStateException("개인정보 키 확인 행이 없습니다");
        }
        return rows.getFirst();
    }

    private boolean encKeyMatches(byte[] enc) {
        try {
            return MARKER.equals(cipher.decrypt(enc, COLUMN));
        } catch (PiiCryptoException e) {
            return false;
        }
    }

    public enum Result {
        CREATED, VERIFIED, HMAC_ROTATED
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
