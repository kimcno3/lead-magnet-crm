package com.leadmagnet.crm.lead.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 신청자가 동의한 문구 원문과 그 sha256(UTF-8 바이트, 소문자 hex) (ADR 0020). 신청 시점의 폼 문구를 신청 행에 복사해 두므로
 * 나중에 폼이나 템플릿이 바뀌어도 "그때 무엇에 동의했는지"를 보일 수 있다. 신청자 개인정보가 아니다.
 */
public record ConsentEvidence(String text, String sha256) {

    public static ConsentEvidence of(String text) {
        return new ConsentEvidence(text, sha256Hex(text));
    }

    public static String sha256Hex(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 쓸 수 없습니다", e);
        }
    }
}
