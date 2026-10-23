package com.leadmagnet.crm.common.error;

import org.springframework.http.HttpStatus;

/** 에러 응답의 HTTP 상태와 기계용 코드. ProblemDetail의 type과 확장 속성 code에 쓴다. */
public enum ErrorCode {

    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "malformed_request", "요청 형식이 올바르지 않습니다"),
    VALIDATION_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed", "입력값이 올바르지 않습니다"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "unauthorized", "인증이 필요합니다"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "invalid_credentials", "이메일 또는 비밀번호가 올바르지 않습니다"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "invalid_refresh_token", "다시 로그인해야 합니다"),
    REFRESH_TOKEN_REUSED(HttpStatus.UNAUTHORIZED, "refresh_token_reused", "이미 사용된 refresh token입니다. 다시 로그인해야 합니다"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "forbidden", "접근 권한이 없습니다");

    private static final String TYPE_PREFIX = "https://leadmagnet.example/problems/";

    private final HttpStatus status;
    private final String code;
    private final String defaultDetail;

    ErrorCode(HttpStatus status, String code, String defaultDetail) {
        this.status = status;
        this.code = code;
        this.defaultDetail = defaultDetail;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String defaultDetail() {
        return defaultDetail;
    }

    public String type() {
        return TYPE_PREFIX + code;
    }
}
