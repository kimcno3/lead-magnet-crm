package com.leadmagnet.crm.lead.config;

import com.leadmagnet.crm.common.web.AuditActor;
import com.leadmagnet.crm.lead.application.LeadRetentionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 매일 03:00(Asia/Seoul) 보존 기간 파기 (ADR 0020). {@link RetentionSchedulingConfig}가 app.retention.enabled일 때만 만든다.
 * 인스턴스마다 돌지만 배치는 advisory lock으로 한 번에 하나씩만 처리된다.
 */
public class RetentionScheduler {

    static final String CRON = "0 0 3 * * *";
    static final String ZONE = "Asia/Seoul";

    private static final Logger log = LoggerFactory.getLogger(RetentionScheduler.class);
    /** 시스템 실행이라 운영자·IP가 없다. */
    private static final AuditActor SYSTEM = new AuditActor(null, null, null);

    private final LeadRetentionService retention;

    public RetentionScheduler(LeadRetentionService retention) {
        this.retention = retention;
    }

    @Scheduled(cron = CRON, zone = ZONE)
    public void runDaily() {
        try {
            retention.run(LeadRetentionService.TRIGGER_SCHEDULED, SYSTEM);
        } catch (RuntimeException e) {
            // DB 오류 메시지에 행 값이 실릴 수 있어 예외 종류만 남긴다. 다음 날 다시 돈다(멱등).
            log.error("보존 기간 파기 실패: 원인={}", e.getClass().getName());
        }
    }
}
