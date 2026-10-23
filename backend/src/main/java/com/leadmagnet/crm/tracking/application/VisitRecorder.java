package com.leadmagnet.crm.tracking.application;

import com.leadmagnet.crm.link.application.LinkService;
import com.leadmagnet.crm.link.domain.Attribution;
import com.leadmagnet.crm.tracking.domain.Visit;
import com.leadmagnet.crm.tracking.domain.VisitRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 공개 폼 페이지 로드마다 방문 한 행을 남긴다. 채널은 lm이 이 폼의 링크 code일 때만 그 링크, 아니면 direct (ADR 0006). */
@Service
public class VisitRecorder {

    private final VisitRepository visits;
    private final LinkService links;
    private final Clock clock;

    public VisitRecorder(VisitRepository visits, LinkService links, Clock clock) {
        this.visits = visits;
        this.links = links;
        this.clock = clock;
    }

    @Transactional
    public Visit record(long formId, String lm, UUID visitorId, String referrer, String userAgent) {
        Attribution attribution = links.attribute(formId, lm);
        return visits.save(Visit.record(formId, attribution.linkId(), attribution.channel(), visitorId,
            referrer, userAgent, clock.instant()));
    }
}
