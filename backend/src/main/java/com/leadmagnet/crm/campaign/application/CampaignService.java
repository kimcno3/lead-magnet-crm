package com.leadmagnet.crm.campaign.application;

import com.leadmagnet.crm.campaign.domain.Campaign;
import com.leadmagnet.crm.campaign.domain.CampaignRepository;
import com.leadmagnet.crm.common.error.DomainException;
import com.leadmagnet.crm.common.error.ErrorCode;
import com.leadmagnet.crm.form.domain.Form;
import com.leadmagnet.crm.form.domain.FormRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CampaignService {

    private static final String ARCHIVED = "archived";

    private final CampaignRepository campaigns;
    private final FormRepository forms;
    private final Clock clock;

    public CampaignService(CampaignRepository campaigns, FormRepository forms, Clock clock) {
        this.campaigns = campaigns;
        this.forms = forms;
        this.clock = clock;
    }

    public Campaign create(String name, String description, Instant startsAt, Instant endsAt, long operatorId) {
        return campaigns.save(Campaign.create(name, description, startsAt, endsAt, operatorId, clock.instant()));
    }

    @Transactional(readOnly = true)
    public List<CampaignSummary> list() {
        Map<Long, Long> counts = new HashMap<>();
        forms.countByCampaign().forEach(c -> counts.put(c.getCampaignId(), c.getFormCount()));
        return campaigns.findAllByOrderByCreatedAtDescIdDesc().stream()
            .map(c -> new CampaignSummary(c, counts.getOrDefault(c.getId(), 0L)))
            .toList();
    }

    @Transactional(readOnly = true)
    public CampaignDetail get(long id) {
        Campaign campaign = find(id);
        return new CampaignDetail(campaign, forms.findAllByCampaignIdOrderByIdAsc(id));
    }

    /** null인 필드는 바꾸지 않는다. status는 "archived"만 받는다. */
    public CampaignDetail update(long id, String name, String description, Instant startsAt, Instant endsAt,
            String status) {
        Campaign campaign = find(id);
        if (status != null && !ARCHIVED.equals(status)) {
            throw DomainException.validation("status", "status는 \"archived\"만 지정할 수 있습니다");
        }
        Instant now = clock.instant();
        campaign.update(name, description, startsAt, endsAt, now);
        if (status != null) {
            campaign.archive(now);
        }
        return new CampaignDetail(campaign, forms.findAllByCampaignIdOrderByIdAsc(id));
    }

    private Campaign find(long id) {
        return campaigns.findById(id).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    }

    public record CampaignSummary(Campaign campaign, long formCount) {
    }

    public record CampaignDetail(Campaign campaign, List<Form> forms) {
    }
}
