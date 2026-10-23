package com.leadmagnet.crm.lead.application;

import com.leadmagnet.crm.campaign.domain.CampaignRepository;
import com.leadmagnet.crm.common.crypto.PiiCryptoException;
import com.leadmagnet.crm.common.error.DomainException;
import com.leadmagnet.crm.common.error.DomainException.FieldError;
import com.leadmagnet.crm.common.error.ErrorCode;
import com.leadmagnet.crm.form.domain.Form;
import com.leadmagnet.crm.form.domain.FormRepository;
import com.leadmagnet.crm.lead.domain.Lead;
import com.leadmagnet.crm.lead.domain.LeadRepository;
import com.leadmagnet.crm.link.domain.AttributionChannel;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 캠페인별 신청 명단 조회. 최신순(created_at desc, id desc), 폼·채널 필터, 페이징.
 * 이름·전화번호·이메일은 복호화한 뒤 가린 값만 돌려준다. 원문은 {@link LeadContactService} (ADR 0015).
 */
@Service
@Transactional(readOnly = true)
public class LeadQueryService {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private static final Logger log = LoggerFactory.getLogger(LeadQueryService.class);

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final LeadRepository leads;
    private final CampaignRepository campaigns;
    private final FormRepository forms;
    private final ContactProtector protector;

    public LeadQueryService(LeadRepository leads, CampaignRepository campaigns, FormRepository forms,
            ContactProtector protector) {
        this.leads = leads;
        this.campaigns = campaigns;
        this.forms = forms;
        this.protector = protector;
    }

    /**
     * 범위를 벗어난 page·size와 모르는 channel은 422, 없는 캠페인은 404. 폼 제목은 페이지 전체를 한 번에 조회한다.
     * 감사 기록(lead.list)은 이 트랜잭션이 끝난 뒤 컨트롤러가 남긴다(ADR 0016).
     */
    public LeadPage list(long campaignId, int page, int size, Long formId, String channel) {
        List<FieldError> errors = new ArrayList<>();
        if (page < 0) {
            errors.add(new FieldError("page", "page는 0 이상이어야 합니다", "invalid_value"));
        }
        if (size < 1 || size > MAX_SIZE) {
            errors.add(new FieldError("size", "size는 1 이상 " + MAX_SIZE + " 이하여야 합니다", "invalid_value"));
        }
        AttributionChannel channelFilter = LeadFilter.parseChannel(channel, errors);
        if (!errors.isEmpty()) {
            throw DomainException.validation(errors);
        }
        if (!campaigns.existsById(campaignId)) {
            throw new DomainException(ErrorCode.NOT_FOUND);
        }

        LeadFilter filter = new LeadFilter(campaignId, formId, channelFilter);
        Page<Lead> result = leads.findAll(filter.toSpecification(), PageRequest.of(page, size, NEWEST_FIRST));
        Map<Long, String> titles = forms.findAllById(result.stream().map(Lead::getFormId).distinct().toList()).stream()
            .collect(Collectors.toMap(Form::getId, Form::getTitle, (a, b) -> a));
        return new LeadPage(result.stream().map(l -> toView(l, titles.get(l.getFormId()))).toList(),
            page, size, result.getTotalElements(), result.getTotalPages());
    }

    /**
     * 한 행의 암호문이 손상돼도(변조, 다른 키) 명단 전체가 500이 되지 않게 그 행만 연락처를 비우고
     * contactUnavailable로 표시한다. 로그에는 신청 id만 남긴다. 원문 조회({@link LeadContactService})는 그대로 실패한다.
     */
    private LeadView toView(Lead l, String formTitle) {
        LeadContact masked;
        boolean unavailable = false;
        try {
            masked = protector.reveal(l).masked();
        } catch (PiiCryptoException e) {
            log.warn("신청 연락처를 복호화하지 못해 명단에서 비움: leadId={}", l.getId());
            masked = new LeadContact(null, null, null);
            unavailable = true;
        }
        return new LeadView(l.getId(), l.getFormId(), formTitle, l.getChannel().value(), masked.name(), masked.phone(),
            masked.email(), unavailable, l.getPayload(), l.getCreatedAt());
    }

    /**
     * 명단 한 줄. 엔티티를 api 계층에 넘기지 않으려는 읽기용 값이다. name·phone·email은 가린 값이다.
     * contactUnavailable이면 연락처를 복호화하지 못해 셋 다 null이다.
     */
    public record LeadView(Long id, Long formId, String formTitle, String channel, String name, String phone,
            String email, boolean contactUnavailable, Map<String, Object> payload, Instant createdAt) {
    }

    public record LeadPage(List<LeadView> items, int page, int size, long totalElements, int totalPages) {
    }
}
