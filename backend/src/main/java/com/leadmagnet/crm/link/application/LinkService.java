package com.leadmagnet.crm.link.application;

import com.leadmagnet.crm.common.error.DomainException;
import com.leadmagnet.crm.common.error.ErrorCode;
import com.leadmagnet.crm.creator.application.CreatorService;
import com.leadmagnet.crm.creator.domain.Creator;
import com.leadmagnet.crm.form.application.FormService;
import com.leadmagnet.crm.form.domain.Form;
import com.leadmagnet.crm.form.domain.FormStatus;
import com.leadmagnet.crm.link.domain.Attribution;
import com.leadmagnet.crm.link.domain.Channel;
import com.leadmagnet.crm.link.domain.DistributionLink;
import com.leadmagnet.crm.link.domain.DistributionLinkRepository;
import com.leadmagnet.crm.link.domain.LinkCode;
import com.leadmagnet.crm.link.domain.LinkCodeGenerator;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 배포 링크 유스케이스. 코드 충돌 재시도는 시도마다 저장이 자기 트랜잭션에서 끝나야 하므로
 * create는 NOT_SUPPORTED로 호출자의 트랜잭션에서 벗어난다(실패한 flush 뒤에는 같은 트랜잭션을 계속 쓸 수 없다).
 */
@Service
public class LinkService {

    private static final int CODE_ATTEMPTS = 5;
    private static final String CODE_INDEX = "ux_distribution_links_code";

    private final DistributionLinkRepository links;
    private final FormService forms;
    private final CreatorService creators;
    private final LinkCodeGenerator codes;
    private final Clock clock;

    public LinkService(DistributionLinkRepository links, FormService forms, CreatorService creators,
            LinkCodeGenerator codes, Clock clock) {
        this.links = links;
        this.forms = forms;
        this.creators = creators;
        this.codes = codes;
        this.clock = clock;
    }

    /**
     * 폼 × 채널 × 크리에이터(없음 포함)당 링크는 하나(ADR 0006·0014). draft·published 폼에만 만들 수 있고,
     * creatorId가 있으면 그 크리에이터가 있어야 하며(없으면 404) 보관되지 않아야 한다(409 creator_archived).
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public LinkDetail create(long formId, String requestedChannel, Long creatorId) {
        Form form = forms.get(formId);
        Channel channel = Channel.parse(requestedChannel);
        Creator creator = creatorId == null ? null : creators.get(creatorId);
        if (form.getStatus() == FormStatus.CLOSED) {
            throw new DomainException(ErrorCode.FORM_CLOSED);
        }
        if (creator != null && creator.isArchived()) {
            throw new DomainException(ErrorCode.CREATOR_ARCHIVED);
        }
        boolean exists = creatorId == null
            ? links.existsByFormIdAndChannelAndCreatorIdIsNull(formId, channel)
            : links.existsByFormIdAndChannelAndCreatorId(formId, channel, creatorId);
        if (exists) {
            throw new DomainException(ErrorCode.LINK_EXISTS);
        }
        for (int i = 0; i < CODE_ATTEMPTS; i++) {
            DistributionLink link = DistributionLink.create(formId, channel, creatorId, codes.next(), form.getSlug(),
                clock.instant());
            try {
                return new LinkDetail(links.saveAndFlush(link), creator);
            } catch (DataIntegrityViolationException e) {
                if (!String.valueOf(e.getMostSpecificCause().getMessage()).contains(CODE_INDEX)) {
                    throw e;
                }
            }
        }
        throw new DomainException(ErrorCode.INTERNAL_ERROR);
    }

    /**
     * 폼의 링크와 크리에이터. 크리에이터 없는 링크가 먼저, 그다음 크리에이터 id(등록) 순이고,
     * 같은 크리에이터 안에서는 채널 순서(instagram, x, youtube, threads)다. 폼이 없으면 404.
     */
    @Transactional(readOnly = true)
    public List<LinkDetail> list(long formId) {
        forms.get(formId);
        List<DistributionLink> found = links.findAllByFormId(formId);
        Map<Long, Creator> byId = creators.findAllById(
            found.stream().map(DistributionLink::getCreatorId).filter(Objects::nonNull).distinct().toList());
        return found.stream()
            .sorted(Comparator.comparing(DistributionLink::getCreatorId, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(DistributionLink::getChannel))
            .map(l -> new LinkDetail(l, l.getCreatorId() == null ? null : byId.get(l.getCreatorId())))
            .toList();
    }

    /** /l/{code} 처리: 코드의 링크가 가리키는 공개 폼 slug. 없는 코드·draft 폼은 404, closed 폼은 410. */
    @Transactional(readOnly = true)
    public RedirectTarget resolveRedirect(String code) {
        DistributionLink link = findByCode(code).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        Form form = forms.get(link.getFormId());
        return switch (form.getStatus()) {
            case DRAFT -> throw new DomainException(ErrorCode.NOT_FOUND);
            case CLOSED -> throw new DomainException(ErrorCode.GONE);
            case PUBLISHED -> new RedirectTarget(form.getSlug(), link.getCode());
        };
    }

    /**
     * 방문·신청의 채널 귀속(ADR 0006): 코드가 이 폼의 링크일 때만 링크를 돌려준다.
     * 형식이 틀리거나 없는 코드, 다른 폼의 코드는 비어 있으며 호출자는 direct로 귀속한다.
     */
    @Transactional(readOnly = true)
    public Optional<DistributionLink> resolveForForm(long formId, String code) {
        return findByCode(code).filter(link -> link.getFormId() == formId);
    }

    /** resolveForForm 결과를 기록용 귀속으로 바꾼다. 링크가 없으면 direct. */
    @Transactional(readOnly = true)
    public Attribution attribute(long formId, String code) {
        return resolveForForm(formId, code).map(Attribution::of).orElseGet(Attribution::direct);
    }

    private Optional<DistributionLink> findByCode(String code) {
        if (code == null || !LinkCode.FORMAT.matcher(code).matches()) {
            return Optional.empty();
        }
        return links.findByCode(code);
    }

    /** 링크와 그 크리에이터(크리에이터 없는 링크면 null). */
    public record LinkDetail(DistributionLink link, Creator creator) {
    }

    public record RedirectTarget(String slug, String code) {

        /** 상대 경로. 방문은 이 경로의 폼 페이지 로드에서 한 번만 기록한다. */
        public String location() {
            return "/f/" + slug + "?lm=" + code;
        }
    }
}
