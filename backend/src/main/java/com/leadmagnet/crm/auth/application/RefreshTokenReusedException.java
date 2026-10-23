package com.leadmagnet.crm.auth.application;

import com.leadmagnet.crm.common.error.DomainException;
import com.leadmagnet.crm.common.error.ErrorCode;

/**
 * 폐기된 refresh token의 재사용 감지(401 refresh_token_reused). 패밀리 폐기는 이미 커밋되었고, 컨트롤러가 운영자 id로
 * 감사 기록(auth.refresh.reused)을 트랜잭션 밖에서 남긴다(ADR 0016 변경 기록).
 */
public class RefreshTokenReusedException extends DomainException {

    private final long operatorId;

    public RefreshTokenReusedException(long operatorId) {
        super(ErrorCode.REFRESH_TOKEN_REUSED);
        this.operatorId = operatorId;
    }

    public long getOperatorId() {
        return operatorId;
    }
}
