package com.leadmagnet.crm.common.error;

import org.springframework.http.HttpStatus;

/** 에러 응답의 HTTP 상태와 기계용 코드. ProblemDetail의 type과 확장 속성 code에 쓴다. */
public enum ErrorCode {

    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "malformed_request", "요청 형식이 올바르지 않습니다"),
    VALIDATION_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed", "입력값이 올바르지 않습니다"),
    INVALID_FORM_TOKEN(HttpStatus.UNPROCESSABLE_ENTITY, "invalid_form_token", "잠시 후 다시 제출해 주세요"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "unauthorized", "인증이 필요합니다"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "invalid_credentials", "이메일 또는 비밀번호가 올바르지 않습니다"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "invalid_refresh_token", "다시 로그인해야 합니다"),
    REFRESH_TOKEN_REUSED(HttpStatus.UNAUTHORIZED, "refresh_token_reused", "이미 사용된 refresh token입니다. 다시 로그인해야 합니다"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "forbidden", "접근 권한이 없습니다"),
    FORBIDDEN_ORIGIN(HttpStatus.FORBIDDEN, "forbidden_origin", "허용하지 않는 출처의 요청입니다"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "not_found", "리소스를 찾을 수 없습니다"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "method_not_allowed", "허용하지 않는 메서드입니다"),
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "not_acceptable", "요청한 응답 형식을 제공할 수 없습니다"),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "payload_too_large", "요청 본문이 너무 큽니다"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported_media_type", "지원하지 않는 Content-Type입니다"),
    TEMPLATE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "template_too_large", "템플릿 파일은 512KB 이하여야 합니다"),
    TEMPLATE_EMPTY(HttpStatus.UNPROCESSABLE_ENTITY, "template_empty", "빈 파일은 올릴 수 없습니다"),
    TEMPLATE_INVALID_EXTENSION(HttpStatus.UNPROCESSABLE_ENTITY, "template_invalid_extension", "확장자는 .html 또는 .htm이어야 합니다"),
    TEMPLATE_NOT_UTF8(HttpStatus.UNPROCESSABLE_ENTITY, "template_not_utf8", "UTF-8 텍스트 파일이어야 합니다"),
    TEMPLATE_FORM_COUNT_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "template_form_count_invalid", "문서에 form이 정확히 하나 있어야 합니다"),
    TEMPLATE_FORBIDDEN_FIELD(HttpStatus.UNPROCESSABLE_ENTITY, "template_forbidden_field", "file, password 입력은 사용할 수 없습니다"),
    TEMPLATE_FIELD_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "template_field_invalid", "필드 규칙을 위반했습니다"),
    TEMPLATE_CONTACT_FIELD_MISSING(HttpStatus.UNPROCESSABLE_ENTITY, "template_contact_field_missing", "phone 또는 email 필드가 필요합니다"),
    CAMPAIGN_ALREADY_ARCHIVED(HttpStatus.CONFLICT, "campaign_already_archived", "이미 보관된 캠페인입니다"),
    CAMPAIGN_ARCHIVED(HttpStatus.CONFLICT, "campaign_archived", "보관된 캠페인에는 폼을 만들 수 없습니다"),
    INVALID_FORM_TRANSITION(HttpStatus.CONFLICT, "invalid_form_transition", "허용하지 않는 폼 상태 전이입니다"),
    FORM_NOT_EDITABLE(HttpStatus.CONFLICT, "form_not_editable", "초안 상태의 폼만 내용을 수정할 수 있습니다"),
    SLUG_TAKEN(HttpStatus.CONFLICT, "slug_taken", "이미 사용 중인 slug입니다"),
    LINK_EXISTS(HttpStatus.CONFLICT, "link_exists", "이 폼에는 같은 채널·크리에이터의 링크가 이미 있습니다"),
    FORM_CLOSED(HttpStatus.CONFLICT, "form_closed", "마감된 폼에는 링크를 만들 수 없습니다"),
    CREATOR_ALREADY_ARCHIVED(HttpStatus.CONFLICT, "creator_already_archived", "이미 보관된 크리에이터입니다"),
    CREATOR_ARCHIVED(HttpStatus.CONFLICT, "creator_archived", "보관된 크리에이터로는 새 링크를 만들 수 없습니다"),
    DUPLICATE_SUBMISSION(HttpStatus.CONFLICT, "duplicate_submission", "이미 신청한 연락처입니다"),
    GONE(HttpStatus.GONE, "gone", "마감되어 더 이상 사용할 수 없습니다"),
    LEAD_ANONYMIZED(HttpStatus.GONE, "lead_anonymized", "보존 기간이 지나 연락처를 파기한 신청입니다"),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "too_many_requests", "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요"),
    TOO_MANY_LOGIN_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "too_many_login_attempts", "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "서버 오류가 발생했습니다");

    private static final String TYPE_PREFIX = "https://leadmagnet.example/problems/";

    private final HttpStatus status;
    private final String code;
    private final String defaultDetail;

    ErrorCode(HttpStatus status, String code, String defaultDetail) {
        this.status = status;
        this.code = code;
        this.defaultDetail = defaultDetail;
    }

    /**
     * Spring MVC 표준 예외처럼 ErrorCode 없이 상태만 있는 에러에 붙일 코드.
     * 전용 코드가 없는 상태는 4xx면 malformed_request, 5xx면 internal_error로 묶는다(상태 값은 그대로 둔다).
     */
    public static ErrorCode forStatus(int status) {
        return switch (status) {
            case 401 -> UNAUTHORIZED;
            case 403 -> FORBIDDEN;
            case 404 -> NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 406 -> NOT_ACCEPTABLE;
            case 413 -> PAYLOAD_TOO_LARGE;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            case 422 -> VALIDATION_FAILED;
            case 429 -> TOO_MANY_REQUESTS;
            default -> status >= 500 ? INTERNAL_ERROR : MALFORMED_REQUEST;
        };
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
