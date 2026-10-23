package com.leadmagnet.crm.form.application;

import com.leadmagnet.crm.common.error.DomainException;
import com.leadmagnet.crm.common.error.ErrorCode;
import com.leadmagnet.crm.form.domain.FormToken;
import com.leadmagnet.crm.form.domain.FormToken.Check;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 공개 폼 작성 시간 토큰의 발급·확인 (ADR 0019). 현재 시각은 Clock 빈으로 얻는다. */
@Service
public class FormTokenService {

    private static final Logger log = LoggerFactory.getLogger(FormTokenService.class);

    private final FormToken tokens;
    private final Clock clock;

    public FormTokenService(FormToken tokens, Clock clock) {
        this.tokens = tokens;
        this.clock = clock;
    }

    public String issue(String slug) {
        return tokens.issue(slug, clock.instant());
    }

    /** 유효하지 않으면 422 invalid_form_token. 사유는 응답에 싣지 않고 DEBUG 로그에만 남긴다(토큰 값은 남기지 않는다). */
    public void verify(String token, String slug) {
        Check check = tokens.check(token, slug, clock.instant());
        if (check != Check.VALID) {
            log.debug("폼 토큰 거절: slug={}, 사유={}", slug, check);
            throw new DomainException(ErrorCode.INVALID_FORM_TOKEN);
        }
    }
}
