package com.leadmagnet.crm.lead.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeadRepository extends JpaRepository<Lead, Long>, JpaSpecificationExecutor<Lead> {

    /**
     * 신청 한 행을 넣되, (form_id, phone_hash)·(form_id, email_hash) 유니크 인덱스 중
     * 하나라도 걸리면 넣지 않고 0을 돌려준다 (ADR 0007, 0015). ON CONFLICT에 대상을 지정하지 않아 모든 유니크 인덱스가 대상이다.
     * 동시 제출에도 DB가 하나만 통과시킨다. 예외로 처리하지 않는 이유: 유니크 위반 예외는 DB가 낸 오류 로그에
     * 중복된 전화번호·이메일 원문(Detail: Key (...)=(...))을 싣고 트랜잭션도 못 쓰게 만든다.
     * 주의: ON CONFLICT는 유니크 충돌만 삼킨다. CHECK·FK 위반은 여전히 오류이고 DB 로그에 행 값이 실릴 수 있으므로,
     * 그런 값이 들어오지 않게 하는 것은 SubmissionValidator(연락처 필수)와 Attribution(link/channel 일관성)의 몫이다.
     * 널이 될 수 있는 값은 cast로 타입을 고정한다. UUID는 문자열로 받는다.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
        INSERT INTO leads (form_id, campaign_id, link_id, channel, visitor_id,
                           name_enc, phone_enc, email_enc, phone_hash, email_hash, payload, consent_at, created_at,
                           consent_text, consent_sha256)
        VALUES (:formId, :campaignId, cast(:linkId as bigint), :channel, cast(:visitorId as uuid),
                cast(:nameEnc as bytea), cast(:phoneEnc as bytea), cast(:emailEnc as bytea),
                cast(:phoneHash as char(64)), cast(:emailHash as char(64)), cast(:payload as jsonb), :consentAt,
                :createdAt, :consentText, cast(:consentSha256 as char(64)))
        ON CONFLICT DO NOTHING
        """)
    int insertIfAbsent(@Param("formId") long formId, @Param("campaignId") long campaignId,
            @Param("linkId") Long linkId, @Param("channel") String channel, @Param("visitorId") String visitorId,
            @Param("nameEnc") byte[] nameEnc, @Param("phoneEnc") byte[] phoneEnc, @Param("emailEnc") byte[] emailEnc,
            @Param("phoneHash") String phoneHash, @Param("emailHash") String emailHash,
            @Param("payload") String payloadJson, @Param("consentAt") Instant consentAt,
            @Param("createdAt") Instant createdAt, @Param("consentText") String consentText,
            @Param("consentSha256") String consentSha256);

    /** {@link #insertIfAbsent}를 Lead로 호출한다. 저장됐으면 true, 중복이면 false. */
    default boolean insertIfAbsent(Lead lead, String payloadJson) {
        UUID visitor = lead.getVisitorId();
        ProtectedContact contact = lead.getProtectedContact();
        return insertIfAbsent(lead.getFormId(), lead.getCampaignId(), lead.getLinkId(), lead.getChannel().value(),
            visitor == null ? null : visitor.toString(),
            contact.nameEnc(), contact.phoneEnc(), contact.emailEnc(), contact.phoneHash(), contact.emailHash(),
            payloadJson, lead.getConsentAt(), lead.getCreatedAt(), lead.getConsentText(), lead.getConsentSha256()) == 1;
    }

    /**
     * 보존 기간이 지난, 아직 익명화하지 않은 신청 id를 id 순으로 limit건(ADR 0020). keyset으로 afterId 다음부터 읽는다.
     * 기준: {@code created_at < now - retention_days × 24시간}. 정확히 경계 시각인 신청은 아직 보존한다.
     */
    @Query(nativeQuery = true, value = """
        SELECT l.id
          FROM leads l
          JOIN campaigns c ON c.id = l.campaign_id
         WHERE l.anonymized_at IS NULL
           AND l.id > :afterId
           AND l.created_at < cast(:now as timestamptz) - make_interval(hours => c.retention_days * 24)
         ORDER BY l.id
         LIMIT :limit
        """)
    List<Long> findRetentionDueIds(@Param("now") Instant now, @Param("afterId") long afterId,
            @Param("limit") int limit);

    /** {@link #findRetentionDueIds}와 같은 조건의 건수(드라이런). */
    @Query(nativeQuery = true, value = """
        SELECT count(*)
          FROM leads l
          JOIN campaigns c ON c.id = l.campaign_id
         WHERE l.anonymized_at IS NULL
           AND l.created_at < cast(:now as timestamptz) - make_interval(hours => c.retention_days * 24)
        """)
    long countRetentionDue(@Param("now") Instant now);

    /**
     * 트랜잭션 범위의 PostgreSQL advisory lock을 기다리지 않고 잡는다. 이미 다른 트랜잭션(다른 인스턴스 포함)이 잡고 있으면
     * false. 커밋·롤백 때 저절로 풀린다. 파기 배치를 인스턴스 사이에서 하나씩만 돌리는 데 쓴다(ADR 0020).
     */
    @Query(nativeQuery = true, value = "SELECT pg_try_advisory_xact_lock(:key)")
    boolean tryAdvisoryXactLock(@Param("key") long key);
}
