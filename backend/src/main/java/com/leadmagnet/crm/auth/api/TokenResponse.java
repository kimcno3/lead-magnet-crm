package com.leadmagnet.crm.auth.api;

import com.leadmagnet.crm.auth.application.IssuedTokens;

/** refresh token은 바디에 넣지 않는다. 쿠키로만 보낸다. */
public record TokenResponse(String accessToken, String tokenType, long expiresIn) {

    static TokenResponse from(IssuedTokens tokens) {
        return new TokenResponse(tokens.accessToken(), "Bearer", tokens.expiresIn());
    }
}
