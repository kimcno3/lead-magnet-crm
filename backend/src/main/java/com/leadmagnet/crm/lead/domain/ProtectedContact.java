package com.leadmagnet.crm.lead.domain;

/**
 * 신청자 연락처의 저장용 표현 (ADR 0015). 암호문은 AES-256-GCM, 해시는 중복 판정용 HMAC-SHA256 hex.
 * 값이 없는 필드는 null이다.
 */
public record ProtectedContact(byte[] nameEnc, byte[] phoneEnc, byte[] emailEnc, String phoneHash, String emailHash) {
}
