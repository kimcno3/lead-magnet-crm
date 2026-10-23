package com.leadmagnet.crm.common.error;

/** 429와 다시 시도할 때까지의 초. GlobalExceptionHandler가 Retry-After 헤더로 낸다. */
public class TooManyRequestsException extends DomainException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(ErrorCode errorCode, long retryAfterSeconds) {
        super(errorCode);
        if (errorCode.status().value() != 429 || retryAfterSeconds < 1) {
            throw new IllegalArgumentException("429 코드와 1초 이상의 Retry-After가 필요합니다");
        }
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
