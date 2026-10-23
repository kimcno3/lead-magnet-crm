package com.leadmagnet.crm.audit.application;

import com.leadmagnet.crm.audit.domain.AuditAction;
import com.leadmagnet.crm.audit.domain.AuditActor;
import com.leadmagnet.crm.audit.domain.AuditEvent;
import com.leadmagnet.crm.audit.domain.AuditLog;
import com.leadmagnet.crm.audit.domain.AuditLogRepository;
import com.leadmagnet.crm.common.DateRange;
import com.leadmagnet.crm.common.error.DomainException;
import com.leadmagnet.crm.common.error.DomainException.FieldError;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 감사 로그 쓰기와 조회 (ADR 0016). 기록 실패 정책은 호출하는 쪽이 고른다.
 * <ul>
 *   <li>{@link #recordRequired}: 원문을 내주는 유스케이스(원문 열람, CSV 다운로드). 호출자의 트랜잭션 안에서 쓰고
 *       실패하면 예외가 그대로 올라가 요청도 실패한다.</li>
 *   <li>{@link #recordBestEffort}: 명단 조회·로그인. 유스케이스 트랜잭션이 끝난 뒤(컨트롤러에서) 자기 트랜잭션으로 쓰고,
 *       실패하면 로그만 남기고 계속한다. 트랜잭션 안에서 부르면 커넥션 두 개를 잡게 되므로 막는다.</li>
 * </ul>
 */
@Service
public class AuditService {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final AuditLogRepository auditLogs;
    private final Clock clock;
    private final TransactionTemplate ownTransaction;

    public AuditService(AuditLogRepository auditLogs, Clock clock, PlatformTransactionManager transactionManager) {
        this.auditLogs = auditLogs;
        this.clock = clock;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 호출자의 (쓰기) 트랜잭션 안에서 기록한다. 트랜잭션이 없으면 예외다. flush해 실패를 바로 드러낸다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordRequired(AuditActor actor, AuditEvent event) {
        auditLogs.saveAndFlush(AuditLog.record(actor, event, clock.instant()));
    }

    /**
     * 진행 중인 트랜잭션 밖에서만 부른다(유스케이스가 커밋·롤백된 뒤). 자기 트랜잭션으로 기록하고, 실패해도 예외를 던지지 않는다.
     * 로그에는 action과 예외 종류만 남긴다(DB 오류 메시지에 행 값이 실릴 수 있어 메시지는 남기지 않는다).
     *
     * @throws IllegalStateException 트랜잭션 안에서 불렀을 때(커넥션을 두 개 잡지 않게 하는 프로그래밍 오류 검사)
     */
    public void recordBestEffort(AuditActor actor, AuditEvent event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("recordBestEffort는 트랜잭션 밖에서 불러야 합니다");
        }
        try {
            ownTransaction.executeWithoutResult(
                status -> auditLogs.saveAndFlush(AuditLog.record(actor, event, clock.instant())));
        } catch (RuntimeException e) {
            log.warn("감사 로그 기록 실패(요청은 계속 처리): action={}, 원인={}", event.action().value(),
                e.getClass().getName());
        }
    }

    /**
     * 최신순. 모르는 action, 범위를 벗어난 page·size, 잘못된 기간은 422. 기간은 Asia/Seoul 날짜다.
     * 운영자 이메일은 붙이지 않는다(api 계층이 {@link OperatorDirectory}로 붙인다).
     */
    @Transactional(readOnly = true)
    public AuditLogPage search(AuditSearch search) {
        List<FieldError> errors = new ArrayList<>();
        AuditAction action = null;
        if (search.action() != null) {
            action = AuditAction.fromValue(search.action()).orElse(null);
            if (action == null) {
                errors.add(new FieldError("action", "모르는 action입니다", "invalid_value"));
            }
        }
        if (search.page() < 0) {
            errors.add(new FieldError("page", "page는 0 이상이어야 합니다", "invalid_value"));
        }
        if (search.size() < 1 || search.size() > MAX_SIZE) {
            errors.add(new FieldError("size", "size는 1 이상 " + MAX_SIZE + " 이하여야 합니다", "invalid_value"));
        }
        DateRange range = null;
        try {
            range = DateRange.parse(search.from(), search.to());
        } catch (DomainException e) {
            errors.addAll(e.getErrors());
        }
        if (!errors.isEmpty()) {
            throw DomainException.validation(errors);
        }

        Page<AuditLog> result = auditLogs.findAll(filter(action, search.operatorId(), search.campaignId(), range),
            PageRequest.of(search.page(), search.size(), NEWEST_FIRST));
        return new AuditLogPage(result.stream().map(AuditService::toView).toList(), search.page(), search.size(),
            result.getTotalElements(), result.getTotalPages());
    }

    private static Specification<AuditLog> filter(AuditAction action, Long operatorId, Long campaignId,
            DateRange range) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (action != null) {
                predicates.add(cb.equal(root.get("action"), action.value()));
            }
            if (operatorId != null) {
                predicates.add(cb.equal(root.get("operatorId"), operatorId));
            }
            if (campaignId != null) {
                predicates.add(cb.equal(root.get("campaignId"), campaignId));
            }
            if (range.fromInstant() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), range.fromInstant()));
            }
            if (range.toExclusiveInstant() != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), range.toExclusiveInstant()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static AuditLogView toView(AuditLog l) {
        return new AuditLogView(l.getId(), l.getOperatorId(), l.getAction(), l.getTargetType(),
            l.getTargetId(), l.getCampaignId(), l.getDetail(), l.getIp(), l.getUserAgent(), l.getCreatedAt());
    }

    /** 조회 조건. null인 필터는 쓰지 않는다. from·to는 yyyy-MM-dd. */
    public record AuditSearch(String action, Long operatorId, Long campaignId, String from, String to, int page,
            int size) {
    }

    /** 감사 기록 한 줄(읽기용). operatorId는 운영자를 특정하지 않은 기록이면 null이다. */
    public record AuditLogView(Long id, Long operatorId, String action, String targetType,
            Long targetId, Long campaignId, Map<String, Object> detail, String ip, String userAgent,
            Instant createdAt) {
    }

    public record AuditLogPage(List<AuditLogView> items, int page, int size, long totalElements, int totalPages) {
    }
}
