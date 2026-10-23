package com.leadmagnet.crm.link.application;

import com.leadmagnet.crm.common.error.DomainException;
import com.leadmagnet.crm.common.error.ErrorCode;
import com.leadmagnet.crm.form.application.FormService;
import com.leadmagnet.crm.form.domain.Form;
import com.leadmagnet.crm.form.domain.FormStatus;
import com.leadmagnet.crm.link.domain.Channel;
import com.leadmagnet.crm.link.domain.DistributionLink;
import com.leadmagnet.crm.link.domain.DistributionLinkRepository;
import com.leadmagnet.crm.link.domain.LinkCode;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 배포 링크 유스케이스. 코드 충돌 재시도는 시도마다 저장이 자기 트랜잭션에서 끝나야 하므로
 * 클래스에 @Transactional을 붙이지 않는다(실패한 flush 뒤에는 같은 트랜잭션을 계속 쓸 수 없다).
 */
@Service
public class LinkService {

    private static final int CODE_ATTEMPTS = 5;
    private static final String CODE_INDEX = "ux_distribution_links_code";

    private final DistributionLinkRepository links;
    private final FormService forms;
    private final Clock clock;

    public LinkService(DistributionLinkRepository links, FormService forms, Clock clock) {
        this.links = links;
        this.forms = forms;
        this.clock = clock;
    }

    /** 폼당 채널별 링크는 하나. draft·published 폼에만 만들 수 있다. */
    public DistributionLink create(long formId, String requestedChannel) {
        Form form = forms.get(formId);
        Channel channel = Channel.parse(requestedChannel);
        if (form.getStatus() == FormStatus.CLOSED) {
            throw new DomainException(ErrorCode.FORM_CLOSED);
        }
        if (links.existsByFormIdAndChannel(formId, channel)) {
            throw new DomainException(ErrorCode.LINK_EXISTS);
        }
        for (int i = 0; i < CODE_ATTEMPTS; i++) {
            DistributionLink link = DistributionLink.create(formId, channel, LinkCode.generate(), form.getSlug(),
                clock.instant());
            try {
                return links.saveAndFlush(link);
            } catch (DataIntegrityViolationException e) {
                if (!String.valueOf(e.getMostSpecificCause().getMessage()).contains(CODE_INDEX)) {
                    throw e;
                }
            }
        }
        throw new DomainException(ErrorCode.INTERNAL_ERROR);
    }

    /** 채널 순서(instagram, x, youtube, threads)로 정렬한 폼의 링크. 폼이 없으면 404. */
    public List<DistributionLink> list(long formId) {
        forms.get(formId);
        return links.findAllByFormId(formId).stream()
            .sorted(Comparator.comparing(DistributionLink::getChannel))
            .toList();
    }

    /** /l/{code} 처리: 코드의 링크가 가리키는 공개 폼 slug. 없는 코드·draft 폼은 404, closed 폼은 410. */
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
    public Optional<DistributionLink> resolveForForm(long formId, String code) {
        return findByCode(code).filter(link -> link.getFormId() == formId);
    }

    private Optional<DistributionLink> findByCode(String code) {
        if (code == null || !LinkCode.FORMAT.matcher(code).matches()) {
            return Optional.empty();
        }
        return links.findByCode(code);
    }

    public record RedirectTarget(String slug, String code) {

        /** 상대 경로. 방문은 이 경로의 폼 페이지 로드에서 한 번만 기록한다. */
        public String location() {
            return "/f/" + slug + "?lm=" + code;
        }
    }
}
