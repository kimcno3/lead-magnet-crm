package com.leadmagnet.crm.lead.application;

import com.leadmagnet.crm.campaign.domain.CampaignRepository;
import com.leadmagnet.crm.common.error.DomainException;
import com.leadmagnet.crm.common.error.DomainException.FieldError;
import com.leadmagnet.crm.common.error.ErrorCode;
import com.leadmagnet.crm.form.domain.Form;
import com.leadmagnet.crm.form.domain.FormRepository;
import com.leadmagnet.crm.lead.domain.Lead;
import com.leadmagnet.crm.lead.domain.LeadRepository;
import com.leadmagnet.crm.link.domain.AttributionChannel;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 캠페인별 신청 명단 조회. 최신순(created_at desc, id desc), 폼·채널 필터, 페이징. */
@Service
@Transactional(readOnly = true)
public class LeadQueryService {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final LeadRepository leads;
    private final CampaignRepository campaigns;
    private final FormRepository forms;

    public LeadQueryService(LeadRepository leads, CampaignRepository campaigns, FormRepository forms) {
        this.leads = leads;
        this.campaigns = campaigns;
        this.forms = forms;
    }

    /** 범위를 벗어난 page·size와 모르는 channel은 422, 없는 캠페인은 404. 폼 제목은 페이지 전체를 한 번에 조회한다. */
    public LeadPage list(long campaignId, int page, int size, Long formId, String channel) {
        List<FieldError> errors = new ArrayList<>();
        if (page < 0) {
            errors.add(new FieldError("page", "page는 0 이상이어야 합니다", "invalid_value"));
        }
        if (size < 1 || size > MAX_SIZE) {
            errors.add(new FieldError("size", "size는 1 이상 " + MAX_SIZE + " 이하여야 합니다", "invalid_value"));
        }
        AttributionChannel channelFilter = null;
        if (channel != null) {
            channelFilter = Arrays.stream(AttributionChannel.values()).filter(c -> c.value().equals(channel))
                .findFirst().orElse(null);
            if (channelFilter == null) {
                errors.add(new FieldError("channel",
                    "channel은 instagram, x, youtube, threads, direct 중 하나여야 합니다", "invalid_value"));
            }
        }
        if (!errors.isEmpty()) {
            throw DomainException.validation(errors);
        }
        if (!campaigns.existsById(campaignId)) {
            throw new DomainException(ErrorCode.NOT_FOUND);
        }

        Page<Lead> result = leads.findAll(filter(campaignId, formId, channelFilter),
            PageRequest.of(page, size, NEWEST_FIRST));
        Map<Long, String> titles = forms.findAllById(result.stream().map(Lead::getFormId).distinct().toList()).stream()
            .collect(Collectors.toMap(Form::getId, Form::getTitle, (a, b) -> a));
        return new LeadPage(result.stream().map(l -> toView(l, titles.get(l.getFormId()))).toList(),
            page, size, result.getTotalElements(), result.getTotalPages());
    }

    private static Specification<Lead> filter(long campaignId, Long formId, AttributionChannel channel) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("campaignId"), campaignId));
            if (formId != null) {
                predicates.add(cb.equal(root.get("formId"), formId));
            }
            if (channel != null) {
                predicates.add(cb.equal(root.get("channel"), channel));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static LeadView toView(Lead l, String formTitle) {
        return new LeadView(l.getId(), l.getFormId(), formTitle, l.getChannel().value(), l.getName(), l.getPhone(),
            l.getEmail(), l.getPayload(), l.getCreatedAt());
    }

    /** 명단 한 줄. 엔티티를 api 계층에 넘기지 않으려는 읽기용 값이다. */
    public record LeadView(Long id, Long formId, String formTitle, String channel, String name, String phone,
            String email, Map<String, Object> payload, Instant createdAt) {
    }

    public record LeadPage(List<LeadView> items, int page, int size, long totalElements, int totalPages) {
    }
}
