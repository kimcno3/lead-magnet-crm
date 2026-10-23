package com.leadmagnet.crm.audit.domain;

import com.leadmagnet.crm.common.PiiMasker;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
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
    public static final String TARGET_FORM = "form";
    public static final String TARGET_TEMPLATE = "template";
    public static final String TARGET_CREATOR = "creator";

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

    /**
     * 로그인 시도 제한이 걸린 시점(ADR 0021). 잠금이 시작될 때 한 줄만 남기고 그 뒤의 거절(429)은 감사 로그에 쓰지 않는다
     * (ADR 0016 결과 절). scopes는 한도에 닿은 범위(email_ip, email, ip), windowMinutes는 창 길이. 이메일은 가린 값만
     * 남기고, IP 한도만 닿았으면(이메일과 무관한 잠금) 이메일을 넣지 않는다.
     */
    public static AuditEvent loginLocked(String rawEmail, List<String> scopes, long windowMinutes) {
        String email = rawEmail == null ? "" : rawEmail.trim();
        Map<String, Object> detail = new LinkedHashMap<>();
        if (!scopes.equals(List.of("ip"))) {
            detail.put("email", PiiMasker.maskEmail(email));
        }
        detail.put("scopes", List.copyOf(scopes));
        detail.put("windowMinutes", windowMinutes);
        return new AuditEvent(AuditAction.AUTH_LOGIN_LOCKED, TARGET_OPERATOR, null, null, detail);
    }

    /**
     * 보존 기간 파기 한 번(ADR 0020). 실행 단위로 한 줄만 남긴다. trigger는 scheduled 또는 manual, count는 이번 실행이
     * 익명화해 커밋한 신청 수다(0일 수 있다). completed는 오류 없이 끝났는지, interrupted는 다른 실행이 잠금을 쥐고 있어
     * 이번 실행이 멈췄는지, error는 실패했을 때 예외 클래스 이름(메시지는 넣지 않는다). 신청 id는 남기지 않는다.
     */
    public static AuditEvent retentionAnonymize(String trigger, long count, boolean completed, boolean interrupted,
            String error) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("trigger", trigger);
        detail.put("count", count);
        detail.put("completed", completed);
        detail.put("interrupted", interrupted);
        if (error != null) {
            detail.put("error", error);
        }
        return new AuditEvent(AuditAction.RETENTION_ANONYMIZE, TARGET_LEAD, null, null, detail);
    }

    /**
     * 개인정보 재암호화 한 번(ADR 0022). 실행 단위로 한 줄. count는 이번 실행이 현재 키로 다시 암호화해 커밋한 신청 수,
     * unreadable은 어느 키로도 풀지 못해 건너뛴 신청 수, remaining은 실행 뒤 이전 키로 남은 행 수(표식 행 포함, 0이면 이전
     * 키를 지워도 된다). completed·interrupted·error는 {@link #retentionAnonymize}와 같은 뜻이다. 신청 id는 남기지 않는다.
     */
    public static AuditEvent piiReencrypt(long count, long unreadable, long remaining, boolean completed,
            boolean interrupted, String error) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("count", count);
        detail.put("unreadable", unreadable);
        detail.put("remaining", remaining);
        detail.put("completed", completed);
        detail.put("interrupted", interrupted);
        if (error != null) {
            detail.put("error", error);
        }
        return new AuditEvent(AuditAction.PII_REENCRYPT, TARGET_LEAD, null, null, detail);
    }

    /** 캠페인 보존 기간 변경(ADR 0020). 줄이면 다음 파기 실행에서 바로 파기되므로 누가 언제 바꿨는지 남긴다. */
    public static AuditEvent campaignRetentionUpdate(long campaignId, int oldRetentionDays, int newRetentionDays) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("oldRetentionDays", oldRetentionDays);
        detail.put("newRetentionDays", newRetentionDays);
        return new AuditEvent(AuditAction.CAMPAIGN_RETENTION_UPDATE, TARGET_CAMPAIGN, campaignId, campaignId, detail);
    }

    /** refresh token 재사용 감지로 패밀리를 폐기한 일(ADR 0016 변경 기록). 토큰 값은 남기지 않는다. */
    public static AuditEvent refreshReused(long operatorId) {
        return new AuditEvent(AuditAction.AUTH_REFRESH_REUSED, TARGET_OPERATOR, operatorId, null, Map.of());
    }

    /** 로그아웃. refresh 쿠키가 운영자로 확인될 때만 남긴다. */
    public static AuditEvent logout(long operatorId) {
        return new AuditEvent(AuditAction.AUTH_LOGOUT, TARGET_OPERATOR, operatorId, null, Map.of());
    }

    /** 폼 게시·마감. detail은 폼 id와 slug(공개 주소의 일부이며 신청자 개인정보가 아니다). */
    public static AuditEvent formStatusChange(boolean publish, long formId, long campaignId, String slug) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("formId", formId);
        detail.put("slug", slug);
        return new AuditEvent(publish ? AuditAction.FORM_PUBLISH : AuditAction.FORM_CLOSE, TARGET_FORM, formId,
            campaignId, detail);
    }

    /** 템플릿 새 버전 업로드. version은 올린 뒤의 버전, affectedDraftForms는 다시 스냅샷한 draft 폼 수. */
    public static AuditEvent templateVersionUpload(long templateId, int version, int affectedDraftForms) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("templateId", templateId);
        detail.put("version", version);
        detail.put("affectedDraftForms", affectedDraftForms);
        return new AuditEvent(AuditAction.TEMPLATE_VERSION_UPLOAD, TARGET_TEMPLATE, templateId, null, detail);
    }

    public static AuditEvent creatorArchive(long creatorId) {
        return new AuditEvent(AuditAction.CREATOR_ARCHIVE, TARGET_CREATOR, creatorId, null,
            Map.of("creatorId", creatorId));
    }

    private static Map<String, Object> filterDetail(Long formId, String channel) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("formId", formId);
        detail.put("channel", channel);
        return detail;
    }
}
