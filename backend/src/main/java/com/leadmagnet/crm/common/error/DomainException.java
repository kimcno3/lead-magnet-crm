package com.leadmagnet.crm.common.error;

import java.util.List;

/** 업무 규칙 위반. GlobalExceptionHandler가 ErrorCode에 맞는 ProblemDetail로 바꾼다. */
public class DomainException extends RuntimeException {

    /** 세부 사유를 따로 정하지 않은 필드 오류의 code. */
    public static final String DEFAULT_FIELD_CODE = "invalid_value";

    /**
     * 필드 단위 검증 실패 내역. 비어 있지 않으면 응답에 errors[{field, message, code}]로 붙는다.
     * code는 클라이언트가 분기할 수 있는 세부 사유(예: required, invalid_format)이고, 주지 않으면 invalid_value다.
     */
    public record FieldError(String field, String message, String code) {

        public FieldError {
            code = code == null ? DEFAULT_FIELD_CODE : code;
        }

        public FieldError(String field, String message) {
            this(field, message, DEFAULT_FIELD_CODE);
        }
    }

    private final ErrorCode errorCode;
    private final List<FieldError> errors;

    public DomainException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultDetail());
    }

    public DomainException(ErrorCode errorCode, String detail) {
        this(errorCode, detail, List.of());
    }

    private DomainException(ErrorCode errorCode, String detail, List<FieldError> errors) {
        super(detail);
        this.errorCode = errorCode;
        this.errors = errors;
    }

    /** 422 validation_failed와 errors[{field, message, code: invalid_value}] 하나를 가진 예외. */
    public static DomainException validation(String field, String message) {
        return validation(field, message, DEFAULT_FIELD_CODE);
    }

    /** 422 validation_failed와 세부 사유 code를 지정한 필드 오류 하나를 가진 예외. */
    public static DomainException validation(String field, String message, String code) {
        return new DomainException(ErrorCode.VALIDATION_FAILED, message, List.of(new FieldError(field, message, code)));
    }

    /** 422 validation_failed와 여러 필드 오류를 한 번에 담은 예외. */
    public static DomainException validation(List<FieldError> errors) {
        return new DomainException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultDetail(),
            List.copyOf(errors));
    }

    public List<FieldError> getErrors() {
        return errors;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
