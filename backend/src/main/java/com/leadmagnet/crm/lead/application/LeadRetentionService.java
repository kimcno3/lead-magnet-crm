package com.leadmagnet.crm.lead.application;

import com.leadmagnet.crm.audit.application.AuditService;
import com.leadmagnet.crm.audit.domain.AuditEvent;
import com.leadmagnet.crm.common.web.AuditActor;
import com.leadmagnet.crm.lead.config.RetentionProperties;
import com.leadmagnet.crm.lead.domain.Lead;
import com.leadmagnet.crm.lead.domain.LeadRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 보존 기간 파기 (ADR 0020). 신청 시각에서 캠페인의 retention_days × 24시간이 지난 신청을 익명화한다({@link Lead#anonymize}).
 * 행은 지우지 않으므로 지표는 그대로다.
 * <ul>
 *   <li>배치: {@link RetentionProperties#batchSize()}건씩 id keyset으로 읽고, 배치마다 자기 트랜잭션에서 커밋한다.</li>
 *   <li>여러 인스턴스: 배치 트랜잭션마다 {@code pg_try_advisory_xact_lock}을 먼저 잡는다. 못 잡으면 다른 실행이 배치를
 *       처리 중이라는 뜻이므로 이 실행은 거기서 멈춘다(남은 행은 잠금을 쥔 실행이 끝까지 처리한다). 잠금 안에서 다시
 *       {@code anonymized_at IS NULL}로 고르므로 같은 행을 두 번 처리하지 않는다.</li>
 *   <li>멱등: 이미 익명화된 행은 대상이 아니다. 다시 돌리면 0건이다.</li>
 *   <li>감사: 실행마다 {@code retention.anonymize} 한 줄(건수)을 마지막에 따로 남긴다. 익명화는 되돌릴 수 없어 배치는 이미
 *       커밋됐으므로, 기록이 실패하면 예외를 올린다(수동 실행은 500, 스케줄러는 ERROR 로그).</li>
 * </ul>
 * 로그에는 건수만 남긴다(신청 id·연락처 없음).
 */
@Service
public class LeadRetentionService {

    /** advisory lock 키. 이 애플리케이션에서 파기 배치만 쓴다("LMRETAIN"의 ASCII). */
    static final long LOCK_KEY = 0x4C4D_5245_5441_494EL;

    public static final String TRIGGER_SCHEDULED = "scheduled";
    public static final String TRIGGER_MANUAL = "manual";

    private static final Logger log = LoggerFactory.getLogger(LeadRetentionService.class);

    private final LeadRepository leads;
    private final AuditService audit;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final int batchSize;

    public LeadRetentionService(LeadRepository leads, AuditService audit, Clock clock,
            PlatformTransactionManager transactionManager, RetentionProperties properties) {
        this.leads = leads;
        this.audit = audit;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
        this.batchSize = properties.batchSize();
    }

    /**
     * 지금 기준으로 파기 대상을 모두 익명화하고 이번 실행이 익명화한 건수를 돌려준다. 트랜잭션 밖에서 부른다.
     *
     * @param trigger {@link #TRIGGER_SCHEDULED} 또는 {@link #TRIGGER_MANUAL}
     */
    public long run(String trigger, AuditActor actor) {
        Instant now = now();
        long total = 0;
        long afterId = 0;
        while (true) {
            long after = afterId;
            Batch batch = transaction.execute(status -> anonymizeBatch(now, after));
            if (batch == null || batch.locked()) {
                log.info("다른 실행이 파기 배치를 처리 중이라 이 실행을 멈춤: trigger={}, 이번 실행 건수={}", trigger, total);
                break;
            }
            total += batch.count();
            if (batch.scanned() < batchSize) {
                break;
            }
            afterId = batch.lastId();
        }
        long count = total;
        transaction.executeWithoutResult(status -> audit.recordRequired(actor,
            AuditEvent.retentionAnonymize(trigger, count)));
        log.info("보존 기간 파기 완료: trigger={}, 익명화={}", trigger, count);
        return count;
    }

    /** 지금 실행하면 익명화될 건수. 아무것도 바꾸지 않고 기록도 남기지 않는다. */
    @Transactional(readOnly = true)
    public long countDue() {
        return leads.countRetentionDue(now());
    }

    private Batch anonymizeBatch(Instant now, long afterId) {
        if (!leads.tryAdvisoryXactLock(LOCK_KEY)) {
            return Batch.LOCKED;
        }
        List<Long> ids = leads.findRetentionDueIds(now, afterId, batchSize);
        if (ids.isEmpty()) {
            return new Batch(false, 0, 0, afterId);
        }
        int count = 0;
        for (Lead lead : leads.findAllById(ids)) {
            if (lead.anonymize(now)) {
                count++;
            }
        }
        leads.flush();
        return new Batch(false, ids.size(), count, ids.get(ids.size() - 1));
    }

    /** DB(timestamptz)는 마이크로초까지라 경계 비교가 어긋나지 않게 맞춘다. */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private record Batch(boolean locked, int scanned, int count, long lastId) {
        static final Batch LOCKED = new Batch(true, 0, 0, 0);
    }
}
