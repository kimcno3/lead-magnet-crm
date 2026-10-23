package com.leadmagnet.crm.lead.domain;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeadRepository extends JpaRepository<Lead, Long>, JpaSpecificationExecutor<Lead> {

    /**
     * 신청 한 행을 넣되, (form_id, phone)·(form_id, email)·(form_id, phone_hash)·(form_id, email_hash) 유니크 인덱스 중
     * 하나라도 걸리면 넣지 않고 0을 돌려준다 (ADR 0007, 0015). ON CONFLICT에 대상을 지정하지 않아 모든 유니크 인덱스가 대상이다.
     * 동시 제출에도 DB가 하나만 통과시킨다. 예외로 처리하지 않는 이유: 유니크 위반 예외는 DB가 낸 오류 로그에
     * 중복된 전화번호·이메일 원문(Detail: Key (...)=(...))을 싣고 트랜잭션도 못 쓰게 만든다.
     * 주의: ON CONFLICT는 유니크 충돌만 삼킨다. CHECK·FK 위반은 여전히 오류이고 DB 로그에 행 값이 실릴 수 있으므로,
     * 그런 값이 들어오지 않게 하는 것은 SubmissionValidator(연락처 필수)와 Attribution(link/channel 일관성)의 몫이다.
     * 널이 될 수 있는 값은 cast로 타입을 고정한다. UUID는 문자열로 받는다.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
        INSERT INTO leads (form_id, campaign_id, link_id, channel, visitor_id, name, phone, email,
                           name_enc, phone_enc, email_enc, phone_hash, email_hash, payload, consent_at, created_at)
        VALUES (:formId, :campaignId, cast(:linkId as bigint), :channel, cast(:visitorId as uuid), :name, :phone,
                :email, cast(:nameEnc as bytea), cast(:phoneEnc as bytea), cast(:emailEnc as bytea),
                cast(:phoneHash as char(64)), cast(:emailHash as char(64)), cast(:payload as jsonb), :consentAt,
                :createdAt)
        ON CONFLICT DO NOTHING
        """)
    int insertIfAbsent(@Param("formId") long formId, @Param("campaignId") long campaignId,
            @Param("linkId") Long linkId, @Param("channel") String channel, @Param("visitorId") String visitorId,
            @Param("name") String name, @Param("phone") String phone, @Param("email") String email,
            @Param("nameEnc") byte[] nameEnc, @Param("phoneEnc") byte[] phoneEnc, @Param("emailEnc") byte[] emailEnc,
            @Param("phoneHash") String phoneHash, @Param("emailHash") String emailHash,
            @Param("payload") String payloadJson, @Param("consentAt") Instant consentAt,
            @Param("createdAt") Instant createdAt);

    /** {@link #insertIfAbsent}를 Lead로 호출한다. 저장됐으면 true, 중복이면 false. */
    default boolean insertIfAbsent(Lead lead, String payloadJson) {
        UUID visitor = lead.getVisitorId();
        ProtectedContact contact = lead.getProtectedContact();
        return insertIfAbsent(lead.getFormId(), lead.getCampaignId(), lead.getLinkId(), lead.getChannel().value(),
            visitor == null ? null : visitor.toString(), lead.getName(), lead.getPhone(), lead.getEmail(),
            contact.nameEnc(), contact.phoneEnc(), contact.emailEnc(), contact.phoneHash(), contact.emailHash(),
            payloadJson, lead.getConsentAt(), lead.getCreatedAt()) == 1;
    }
}
