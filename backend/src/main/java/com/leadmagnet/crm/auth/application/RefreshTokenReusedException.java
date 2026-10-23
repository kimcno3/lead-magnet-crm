package com.leadmagnet.crm.auth.application;

import com.leadmagnet.crm.common.error.DomainException;
import com.leadmagnet.crm.common.error.ErrorCode;

/**
 * 폐기된 refresh token의 재사용 감지(401 refresh_token_reused). 패밀리 폐기는 이미 커밋되었고, 컨트롤러가 운영자 id로
 * 감사 기록(auth.refresh.reused)을 트랜잭션 밖에서 남긴다(ADR 0016 변경 기록). 이미 폐기된 패밀리의 토큰을 다시 보내는
 * 반복 재생은 폐기한 토큰이 없으므로({@link #revokedLiveTokens()} false) 기록하지 않는다(행이 무한히 쌓이지 않게).
 */
public class RefreshTokenReusedException extends DomainException {

    private final long operatorId;
    private final boolean revokedLiveTokens;

    public RefreshTokenReusedException(long operatorId, boolean revokedLiveTokens) {
        super(ErrorCode.REFRESH_TOKEN_REUSED);
        this.operatorId = operatorId;
        this.revokedLiveTokens = revokedLiveTokens;
    }

    public boolean revokedLiveTokens() {
        return revokedLiveTokens;
    }

    public long getOperatorId() {
        return operatorId;
    }
}
