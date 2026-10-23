package com.leadmagnet.crm.form.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FormRepository extends JpaRepository<Form, Long> {

    boolean existsBySlug(String slug);

    List<Form> findAllByCampaignIdOrderByIdAsc(Long campaignId);

    /** 캠페인별 폼 수(상태 무관). 폼이 없는 캠페인은 결과에 없다. */
    @Query("select f.campaignId as campaignId, count(f) as formCount from Form f group by f.campaignId")
    List<CampaignFormCount> countByCampaign();

    interface CampaignFormCount {
        Long getCampaignId();

        long getFormCount();
    }
}
