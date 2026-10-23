package com.leadmagnet.crm.lead.application;

import com.leadmagnet.crm.common.error.DomainException.FieldError;
import com.leadmagnet.crm.lead.domain.Lead;
import com.leadmagnet.crm.link.domain.AttributionChannel;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** 명단 조회·다운로드가 함께 쓰는 필터(캠페인, 선택 폼·채널). */
record LeadFilter(long campaignId, Long formId, AttributionChannel channel) {

    /** 모르는 channel이면 errors에 더하고 null을 돌려준다. null 입력은 필터 없음이다. */
    static AttributionChannel parseChannel(String channel, List<FieldError> errors) {
        if (channel == null) {
            return null;
        }
        AttributionChannel parsed = Arrays.stream(AttributionChannel.values()).filter(c -> c.value().equals(channel))
            .findFirst().orElse(null);
        if (parsed == null) {
            errors.add(new FieldError("channel", "channel은 instagram, x, youtube, threads, direct 중 하나여야 합니다",
                "invalid_value"));
        }
        return parsed;
    }

    Specification<Lead> toSpecification() {
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
}
