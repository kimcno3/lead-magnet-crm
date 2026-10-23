package com.leadmagnet.crm.common.error;

import java.net.URI;
import org.springframework.http.ProblemDetail;

/** ErrorCode로 RFC 9457 ProblemDetail을 만든다. MVC 예외 처리와 Security 핸들러가 함께 쓴다. */
public final class ProblemDetails {

    private ProblemDetails() {
    }

    public static ProblemDetail of(ErrorCode errorCode) {
        return of(errorCode, errorCode.defaultDetail());
    }

    public static ProblemDetail of(ErrorCode errorCode, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(errorCode.status(), detail);
        problem.setType(URI.create(errorCode.type()));
        problem.setTitle(errorCode.status().getReasonPhrase());
        problem.setProperty("code", errorCode.code());
        return problem;
    }
}
