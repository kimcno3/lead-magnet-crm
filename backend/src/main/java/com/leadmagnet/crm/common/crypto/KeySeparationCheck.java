package com.leadmagnet.crm.common.crypto;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

/**
 * 한 비밀이 두 용도에 쓰이면 한쪽이 새는 순간 다른 쪽도 뚫리므로, {@link SecretKeyMaterial#mustDifferFromOthers()}인 키
 * (JWT_SECRET)가 다른 키(개인정보 암호화 키·HMAC 키·폼 토큰 키)와 같으면 기동을 멈춘다(ADR 0015, 0019).
 * 각 설정이 자기 키를 {@link SecretKeyMaterial}로 노출하므로 이 클래스는 도메인 설정을 모른다. 같은 값인지는 후보 바이트
 * (JWT_SECRET이면 문자열 그대로의 UTF-8 바이트와 base64 해독 바이트)로 본다. 오류 메시지에는 어떤 값도 싣지 않는다.
 */
@Component
class KeySeparationCheck {

    /** 검사 대상에서 빠지면(빈 목록 주입 등) 검사가 조용히 통과하지 않도록 반드시 있어야 하는 키 이름. */
    static final Set<String> REQUIRED = Set.of("JWT_SECRET", "LEAD_ENC_KEY·LEAD_HMAC_KEY", "FORM_TOKEN_KEY");

    KeySeparationCheck(List<SecretKeyMaterial> keys) {
        Set<String> missing = new TreeSet<>(REQUIRED);
        keys.forEach(key -> missing.remove(key.name()));
        if (!missing.isEmpty()) {
            throw new IllegalStateException("키 분리 검사 대상이 빠졌습니다: " + missing);
        }
        for (SecretKeyMaterial exclusive : keys) {
            if (!exclusive.mustDifferFromOthers()) {
                continue;
            }
            for (SecretKeyMaterial other : keys) {
                if (other != exclusive && overlaps(exclusive, other)) {
                    throw new IllegalArgumentException(
                        "JWT_SECRET은 LEAD_ENC_KEY·LEAD_HMAC_KEY·FORM_TOKEN_KEY와 달라야 합니다");
                }
            }
        }
    }

    private static boolean overlaps(SecretKeyMaterial a, SecretKeyMaterial b) {
        for (byte[] x : a.candidates()) {
            for (byte[] y : b.candidates()) {
                if (Arrays.equals(x, y)) {
                    return true;
                }
            }
        }
        return false;
    }
}
