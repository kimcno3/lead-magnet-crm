package com.leadmagnet.crm.common.web;

/**
 * 감사 기록의 주체와 요청 출처 (ADR 0016). operatorId는 로그인 실패처럼 운영자를 특정하지 않으면 null이다.
 * ip는 {@code request.getRemoteAddr()} 값이다(프록시 헤더를 신뢰하지 않는다). 컬럼 길이에 맞춰 자르고 빈 값은 null로 둔다.
 */
public record AuditActor(Long operatorId, String ip, String userAgent) {

    public static final int MAX_IP_LENGTH = 45;
    public static final int MAX_USER_AGENT_LENGTH = 512;

    public AuditActor {
        ip = truncate(ip, MAX_IP_LENGTH);
        userAgent = truncate(userAgent, MAX_USER_AGENT_LENGTH);
    }

    public AuditActor withOperator(Long id) {
        return new AuditActor(id, ip, userAgent);
    }

    private static String truncate(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
