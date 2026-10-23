package com.leadmagnet.crm.audit.domain;

import com.leadmagnet.crm.common.PiiMasker;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 무엇을 했는지(action과 대상). detail에는 필터·페이지·건수·가린 이메일만 넣는다. 신청자 원문 개인정보는 넣지 않는다(ADR 0016).
 * detail 값의 null은 키째 빼지 않고 그대로 둔다(필터를 쓰지 않았다는 뜻). 기록 종류별 모양은 정적 팩토리가 정한다.
 */
public record AuditEvent(AuditAction action, String targetType, Long targetId, Long campaignId,
        Map<String, Object> detail) {

    public static final String TARGET_LEAD = "lead";
    public static final String TARGET_CAMPAIGN = "campaign";
    public static final String TARGET_OPERATOR = "operator";

    public AuditEvent {
        detail = detail == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(detail));
    }

    /** 명단 조회. channel은 소문자 값(없으면 null). */
    public static AuditEvent leadList(long campaignId, Long formId, String channel, int page, int size) {
        Map<String, Object> detail = filterDetail(formId, channel);
        detail.put("page", page);
        detail.put("size", size);
        return new AuditEvent(AuditAction.LEAD_LIST, TARGET_CAMPAIGN, campaignId, campaignId, detail);
    }

    /** 원문 열람 한 건. */
    public static AuditEvent contactView(long leadId, long campaignId) {
        return new AuditEvent(AuditAction.LEAD_CONTACT_VIEW, TARGET_LEAD, leadId, campaignId, Map.of());
    }

    /** CSV 다운로드. reveal이면 lead.export.reveal. count는 내보낸 행 수. */
    public static AuditEvent export(long campaignId, Long formId, String channel, boolean reveal, long count) {
        Map<String, Object> detail = filterDetail(formId, channel);
        detail.put("reveal", reveal);
        detail.put("count", count);
        return new AuditEvent(reveal ? AuditAction.LEAD_EXPORT_REVEAL : AuditAction.LEAD_EXPORT, TARGET_CAMPAIGN,
            campaignId, campaignId, detail);
    }

    public static AuditEvent loginSuccess(long operatorId) {
        return new AuditEvent(AuditAction.AUTH_LOGIN_SUCCESS, TARGET_OPERATOR, operatorId, null, Map.of());
    }

    /** 로그인 실패. 이메일은 여기서 가려서만 남긴다(운영자는 특정하지 않는다). */
    public static AuditEvent loginFailure(String rawEmail) {
        String email = rawEmail == null ? "" : rawEmail.trim();
        return new AuditEvent(AuditAction.AUTH_LOGIN_FAILURE, TARGET_OPERATOR, null, null,
            Map.of("email", PiiMasker.maskEmail(email)));
    }

    private static Map<String, Object> filterDetail(Long formId, String channel) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("formId", formId);
        detail.put("channel", channel);
        return detail;
    }
}
