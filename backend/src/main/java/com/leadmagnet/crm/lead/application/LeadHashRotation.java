package com.leadmagnet.crm.lead.application;

import com.leadmagnet.crm.common.crypto.PiiCryptoException;
import com.leadmagnet.crm.common.crypto.PiiCryptoProperties;
import com.leadmagnet.crm.common.crypto.PiiHashRotation;
import com.leadmagnet.crm.common.crypto.PiiHasher;
import com.leadmagnet.crm.common.crypto.PiiKeyRotationException;
import com.leadmagnet.crm.lead.application.LeadKeyRotationRows.Row;
import com.leadmagnet.crm.lead.domain.ProtectedContact;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 기동 시 HMAC 키 교체에서 신청의 {@code phone_hash}·{@code email_hash}를 새 키로 다시 계산한다 (ADR 0022).
 * {@link com.leadmagnet.crm.common.crypto.PiiKeyCheck}가 웹 서버가 요청을 받기 전에 부른다.
 * <ul>
 *   <li>해시가 있는 신청을 id keyset으로 배치 크기만큼 읽고, 배치마다 자기 트랜잭션에서 커밋한다.</li>
 *   <li>행마다 암호문을 풀어 이전 키·새 키 해시를 계산하고, 저장된 해시가 이전 키 해시인 것만 바꾼다. 이미 새 키 해시면
 *       건너뛴다. 그래서 중간에 실패해도 다시 기동하면 남은 행만 이어서 바꾼다(표식은 PiiKeyCheck가 마지막에 바꾼다).</li>
 *   <li>같은 폼에서 새 해시가 이미 다른 신청에 있으면(이론상 생기지 않는다) 유니크 위반 전에 멈추고 두 신청 id를 알린다.</li>
 *   <li>여러 인스턴스가 함께 기동해도 배치마다 {@code pg_advisory_xact_lock}(파기·재암호화와 다른 키)을 기다려 잡으므로
 *       한 번에 한 배치만 돈다. 잠금 뒤에 읽으므로 다른 인스턴스가 바꾼 행은 새 키 해시로 보여 건너뛴다.</li>
 * </ul>
 * 로그와 예외 메시지에는 신청 id와 건수만 남긴다.
 */
@Component
class LeadHashRotation implements PiiHashRotation {

    /** advisory lock 키. HMAC 교체만 쓴다("LMREHASH"의 ASCII). */
    static final long LOCK_KEY = 0x4C4D_5245_4841_5348L;

    private static final Logger log = LoggerFactory.getLogger(LeadHashRotation.class);

    private final LeadKeyRotationRows rows;
    private final ContactProtector protector;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final int batchSize;

    LeadHashRotation(LeadKeyRotationRows rows, ContactProtector protector, JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager, PiiCryptoProperties properties) {
        this.rows = rows;
        this.protector = protector;
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactionManager);
        this.batchSize = properties.batchSize();
    }

    @Override
    public long rotate(PiiHasher previous, PiiHasher current) {
        long total = 0;
        long afterId = 0;
        while (true) {
            long after = afterId;
            Batch batch = Objects.requireNonNull(transaction.execute(status -> rotateBatch(after, previous, current)));
            total += batch.changed();
            if (batch.scanned() < batchSize) {
                break;
            }
            afterId = batch.lastId();
            log.info("HMAC 키 교체 진행: 신청 id {}까지, 바꾼 행 {}건", afterId, total);
        }
        return total;
    }

    private Batch rotateBatch(long afterId, PiiHasher previous, PiiHasher current) {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(?)", Object.class, LOCK_KEY);
        List<Row> batch = rows.findHashed(afterId, batchSize);
        int changed = 0;
        for (Row row : batch) {
            ProtectedContact rehashed = rehash(row, previous, current);
            if (Objects.equals(rehashed.phoneHash(), row.contact().phoneHash())
                    && Objects.equals(rehashed.emailHash(), row.contact().emailHash())) {
                continue;
            }
            requireNoHolder(row, "phone_hash", rehashed.phoneHash(), row.contact().phoneHash());
            requireNoHolder(row, "email_hash", rehashed.emailHash(), row.contact().emailHash());
            if (!rows.updateHashes(row, rehashed)) {
                throw new PiiKeyRotationException("HMAC 키 교체 중 신청이 바뀌었습니다: 신청 id=" + row.id());
            }
            changed++;
        }
        return new Batch(batch.size(), changed, batch.isEmpty() ? afterId : batch.getLast().id());
    }

    private ProtectedContact rehash(Row row, PiiHasher previous, PiiHasher current) {
        try {
            return protector.rehash(row.contact(), previous, current);
        } catch (PiiKeyRotationException | PiiCryptoException e) {
            throw new PiiKeyRotationException("HMAC 키 교체를 멈췄습니다: 신청 id=" + row.id() + ", " + e.getMessage(), e);
        }
    }

    private void requireNoHolder(Row row, String column, String newHash, String oldHash) {
        if (Objects.equals(newHash, oldHash)) {
            return;
        }
        Long holder = rows.holderOf(column, row.formId(), newHash, row.id());
        if (holder != null) {
            throw new PiiKeyRotationException("HMAC 키 교체를 멈췄습니다: 같은 폼에서 새 해시가 겹칩니다(" + column
                + "). 신청 id=" + row.id() + ", 겹치는 신청 id=" + holder);
        }
    }

    private record Batch(int scanned, int changed, long lastId) {
    }
}
