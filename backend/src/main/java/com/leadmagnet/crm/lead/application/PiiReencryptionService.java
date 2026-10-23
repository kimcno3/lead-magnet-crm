package com.leadmagnet.crm.lead.application;

import com.leadmagnet.crm.audit.application.AuditService;
import com.leadmagnet.crm.audit.domain.AuditActor;
import com.leadmagnet.crm.audit.domain.AuditEvent;
import com.leadmagnet.crm.common.crypto.PiiCipher;
import com.leadmagnet.crm.common.crypto.PiiCryptoException;
import com.leadmagnet.crm.common.crypto.PiiCryptoProperties;
import com.leadmagnet.crm.common.crypto.PiiKeyCheck;
import com.leadmagnet.crm.lead.application.LeadKeyRotationRows.Row;
import com.leadmagnet.crm.lead.domain.LeadRepository;
import com.leadmagnet.crm.lead.domain.ProtectedContact;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 암호화 키(LEAD_ENC_KEY) 교체의 온라인 재암호화 (ADR 0022). 현재 키 id가 아닌 암호문을 현재 키로 다시 쓴다. 해시는 그대로다.
 * <ul>
 *   <li>배치: {@code app.pii.batch-size}건씩 id keyset으로 읽고(행 잠금), 배치마다 자기 트랜잭션에서 커밋한다.</li>
 *   <li>여러 인스턴스: 배치 트랜잭션마다 {@code pg_try_advisory_xact_lock}(파기와 다른 키)을 먼저 잡고, 못 잡으면 이 실행은
 *       멈추고 interrupted를 남긴다.</li>
 *   <li>익명화된 행(암호문 NULL)은 대상이 아니고, 읽은 뒤 익명화되면 바꾸지 않는다(원문을 되살리지 않는다).</li>
 *   <li>어느 키로도 풀 수 없는 행은 건너뛰고 unreadable로 센다(남은 건수에 남는다). 로그에는 신청 id만.</li>
 *   <li>표식 행({@code pii_key_check})은 신청이 하나도 남지 않았을 때만 마지막에 현재 키로 바꾼다. 그래서 기동 확인이
 *       현재 키만으로 통과하면 이전 키로 남은 신청이 없다.</li>
 *   <li>감사: 실행마다 {@code pii.reencrypt} 한 줄(건수만)을 {@code recordRequired}로 따로 남긴다. 배치가 실패하면 그때까지의
 *       건수로 completed=false를 먼저 기록하고 예외를 올린다. 드라이런은 바꾸는 것이 없어 기록하지 않는다.</li>
 * </ul>
 */
@Service
public class PiiReencryptionService {

    /** advisory lock 키. 재암호화만 쓴다("LMREENCR"의 ASCII). 파기(LMRETAIN)·HMAC 교체(LMREHASH)와 다르다. */
    public static final long LOCK_KEY = 0x4C4D_5245_454E_4352L;

    private static final Logger log = LoggerFactory.getLogger(PiiReencryptionService.class);

    private final LeadKeyRotationRows rows;
    private final LeadRepository leads;
    private final ContactProtector protector;
    private final PiiCipher cipher;
    private final PiiKeyCheck keyCheck;
    private final AuditService audit;
    private final TransactionTemplate transaction;
    private final int batchSize;

    public PiiReencryptionService(LeadKeyRotationRows rows, LeadRepository leads, ContactProtector protector,
            PiiCipher cipher, PiiKeyCheck keyCheck, AuditService audit, PlatformTransactionManager transactionManager,
            PiiCryptoProperties properties) {
        this.rows = rows;
        this.leads = leads;
        this.protector = protector;
        this.cipher = cipher;
        this.keyCheck = keyCheck;
        this.audit = audit;
        this.transaction = new TransactionTemplate(transactionManager);
        this.batchSize = properties.batchSize();
    }

    public int currentKeyId() {
        return cipher.currentKeyId();
    }

    /** 이전 키로 남은 행 수(신청 + 표식). 0이면 LEAD_ENC_PREVIOUS_KEYS를 지워도 된다. 아무것도 바꾸지 않는다. */
    @Transactional(readOnly = true)
    public long remaining() {
        return rows.countNotOnKey(cipher.currentKeyId()) + (keyCheck.markerOnCurrentKey() ? 0 : 1);
    }

    /** 트랜잭션 밖에서 부른다. */
    public ReencryptRun run(AuditActor actor) {
        long total = 0;
        long unreadable = 0;
        boolean interrupted = false;
        try {
            long afterId = 0;
            while (true) {
                long after = afterId;
                Batch batch = transaction.execute(status -> reencryptBatch(after));
                if (batch == null || batch.locked()) {
                    interrupted = true;
                    break;
                }
                total += batch.count();
                unreadable += batch.unreadable();
                if (batch.scanned() < batchSize) {
                    break;
                }
                afterId = batch.lastId();
            }
            if (!interrupted) {
                Boolean done = transaction.execute(status -> reencryptMarker());
                interrupted = done == null || !done;
            }
        } catch (RuntimeException e) {
            long count = total;
            long skipped = unreadable;
            try {
                record(actor, AuditEvent.piiReencrypt(count, skipped, remaining(), false, false, e.getClass().getName()));
            } catch (RuntimeException auditFailure) {
                e.addSuppressed(auditFailure);
            }
            throw e;
        }
        long left = remaining();
        record(actor, AuditEvent.piiReencrypt(total, unreadable, left, true, interrupted, null));
        log.info("개인정보 재암호화: 다시 암호화={}, 풀 수 없음={}, 남은={}, 중단={}", total, unreadable, left, interrupted);
        return new ReencryptRun(total, unreadable, left, interrupted);
    }

    private void record(AuditActor actor, AuditEvent event) {
        transaction.executeWithoutResult(status -> audit.recordRequired(actor, event));
    }

    private Batch reencryptBatch(long afterId) {
        if (!leads.tryAdvisoryXactLock(LOCK_KEY)) {
            return Batch.LOCKED;
        }
        List<Row> batch = rows.findNotOnKey(cipher.currentKeyId(), afterId, batchSize);
        int count = 0;
        int unreadable = 0;
        for (Row row : batch) {
            ProtectedContact again;
            try {
                again = protector.reencrypt(row.contact());
            } catch (PiiCryptoException e) {
                unreadable++;
                log.warn("재암호화하지 못한 신청(어느 키로도 풀 수 없음): id={}", row.id());
                continue;
            }
            if (rows.updateCiphertexts(row.id(), again)) {
                count++;
            }
        }
        return new Batch(false, batch.size(), count, unreadable, batch.isEmpty() ? afterId : batch.getLast().id());
    }

    /** 신청이 모두 현재 키일 때만 표식을 바꾼다. 잠금을 못 잡으면 false(다른 실행이 처리 중). */
    private boolean reencryptMarker() {
        if (!leads.tryAdvisoryXactLock(LOCK_KEY)) {
            return false;
        }
        if (rows.countNotOnKey(cipher.currentKeyId()) == 0) {
            keyCheck.reencryptMarker();
        }
        return true;
    }

    /**
     * 실행 결과. remaining은 실행 뒤 이전 키로 남은 행 수(신청 + 표식). interrupted면 다른 실행이 처리 중이어서 멈췄다.
     */
    public record ReencryptRun(long reencrypted, long unreadable, long remaining, boolean interrupted) {
    }

    private record Batch(boolean locked, int scanned, int count, int unreadable, long lastId) {
        static final Batch LOCKED = new Batch(true, 0, 0, 0, 0);
    }
}
