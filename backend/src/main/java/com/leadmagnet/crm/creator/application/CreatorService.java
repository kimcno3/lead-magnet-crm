package com.leadmagnet.crm.creator.application;

import com.leadmagnet.crm.audit.application.AuditService;
import com.leadmagnet.crm.audit.domain.AuditEvent;
import com.leadmagnet.crm.common.error.DomainException;
import com.leadmagnet.crm.common.error.ErrorCode;
import com.leadmagnet.crm.common.web.AuditActor;
import com.leadmagnet.crm.creator.domain.Creator;
import com.leadmagnet.crm.creator.domain.CreatorRepository;
import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 크리에이터 등록·조회·수정·보관 (ADR 0014). */
@Service
@Transactional
public class CreatorService {

    private static final String ARCHIVED = "archived";

    private final CreatorRepository creators;
    private final AuditService audit;
    private final Clock clock;

    public CreatorService(CreatorRepository creators, AuditService audit, Clock clock) {
        this.creators = creators;
        this.audit = audit;
        this.clock = clock;
    }

    public Creator create(String name, String handle, String memo, long operatorId) {
        return creators.save(Creator.create(name, handle, memo, operatorId, clock.instant()));
    }

    /** 최신순(created_at desc, id desc). 보관된 크리에이터도 포함한다. */
    @Transactional(readOnly = true)
    public List<Creator> list() {
        return creators.findAllByOrderByCreatedAtDescIdDesc();
    }

    /** 없으면 404. */
    @Transactional(readOnly = true)
    public Creator get(long id) {
        return creators.findById(id).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    }

    /** id → 크리에이터. 없는 id는 빠진다(링크 응답에 이름을 붙일 때 쓴다). */
    @Transactional(readOnly = true)
    public Map<Long, Creator> findAllById(Collection<Long> ids) {
        return creators.findAllById(ids).stream().collect(Collectors.toMap(Creator::getId, Function.identity()));
    }

    /**
     * null인 필드는 바꾸지 않는다. status는 "archived"만 받는다. 검증을 모두 통과해야 하나라도 바꾼다. 보관은 같은 트랜잭션에서
     * creator.archive를 남긴다(기록이 실패하면 보관도 롤백, ADR 0016 변경 기록).
     */
    public Creator update(long id, String name, String handle, String memo, String status, AuditActor actor) {
        Creator creator = get(id);
        if (status != null && !ARCHIVED.equals(status)) {
            throw DomainException.validation("status", "status는 \"archived\"만 지정할 수 있습니다");
        }
        creator.update(name, handle, memo, clock.instant());
        if (status != null) {
            creator.archive(clock.instant());
            audit.recordRequired(actor, AuditEvent.creatorArchive(id));
        }
        return creator;
    }
}
