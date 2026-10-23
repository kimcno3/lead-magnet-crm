package com.leadmagnet.crm.auth.application;

/**
 * 로그인·refresh 결과. accessToken은 응답 바디로, refreshToken 원문은 쿠키로만 내보낸다.
 *
 * @param operatorId 토큰 주인(감사 기록용)
 * @param expiresIn access token 유효 시간(초)
 */
public record IssuedTokens(Long operatorId, String accessToken, long expiresIn, String refreshToken) {

    @Override
    public String toString() {
        return "IssuedTokens[operatorId=" + operatorId + ", expiresIn=" + expiresIn + "]";
    }
}
