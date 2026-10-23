package com.leadmagnet.crm.common.error;

import java.util.List;

/** 업무 규칙 위반. GlobalExceptionHandler가 ErrorCode에 맞는 ProblemDetail로 바꾼다. */
public class DomainException extends RuntimeException {

    /**
     * 필드 단위 검증 실패 내역. 비어 있지 않으면 응답에 errors로 붙는다.
     * code는 클라이언트가 분기할 수 있는 세부 사유(예: required, invalid_format)이고, 없으면 응답에서 뺀다.
     */
    public record FieldError(String field, String message, String code) {

        public FieldError(String field, String message) {
            this(field, message, null);
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

    /** 422 validation_failed와 errors[{field, message}] 하나를 가진 예외. */
    public static DomainException validation(String field, String message) {
        return new DomainException(ErrorCode.VALIDATION_FAILED, message, List.of(new FieldError(field, message)));
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
