package com.leadmagnet.crm.form.domain;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface FormRepository extends JpaRepository<Form, Long> {

    boolean existsBySlug(String slug);

    Optional<Form> findBySlug(String slug);

    List<Form> findAllByCampaignIdOrderByIdAsc(Long campaignId);

    /** 폼 엔티티를 읽지 않고 템플릿 id만 얻는다. 템플릿 행을 먼저 잠그는 순서를 지키기 위해서다 (ADR 0018). */
    @Query("select f.templateId from Form f where f.id = :id")
    Optional<Long> findTemplateIdById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Form f where f.id = :id")
    Optional<Form> findByIdForUpdate(Long id);

    /** 새 버전 업로드가 다시 스냅샷할 draft 폼. 행을 잠가 동시에 고치는 수정과 겹치지 않게 한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Form> findAllByTemplateIdAndStatusOrderByIdAsc(Long templateId, FormStatus status);

    /** 캠페인별 폼 수(상태 무관). 폼이 없는 캠페인은 결과에 없다. */
    @Query("select f.campaignId as campaignId, count(f) as formCount from Form f group by f.campaignId")
    List<CampaignFormCount> countByCampaign();

    interface CampaignFormCount {
        Long getCampaignId();

        long getFormCount();
    }
}
