package com.leadmagnet.crm.tracking.application;

import com.leadmagnet.crm.link.application.LinkService;
import com.leadmagnet.crm.link.domain.Attribution;
import com.leadmagnet.crm.tracking.domain.BotUserAgents;
import com.leadmagnet.crm.tracking.domain.Visit;
import com.leadmagnet.crm.tracking.domain.VisitRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공개 폼 페이지 로드마다 방문 한 행을 남긴다. 채널은 lm이 이 폼의 링크 code일 때만 그 링크, 아니면 direct (ADR 0006).
 * User-Agent가 봇 패턴이면 is_bot으로 표시한다(ADR 0019). 봇 방문도 행은 남기고 지표 SQL이 뺀다.
 */
@Service
public class VisitRecorder {

    private final VisitRepository visits;
    private final LinkService links;
    private final BotUserAgents bots;
    private final Clock clock;

    public VisitRecorder(VisitRepository visits, LinkService links, BotUserAgents bots, Clock clock) {
        this.visits = visits;
        this.links = links;
        this.bots = bots;
        this.clock = clock;
    }

    @Transactional
    public Visit record(long formId, String lm, UUID visitorId, String referrer, String userAgent) {
        Attribution attribution = links.attribute(formId, lm);
        return visits.save(Visit.record(formId, attribution.linkId(), attribution.channel(), visitorId,
            referrer, userAgent, bots.isBot(userAgent), clock.instant()));
    }
}
