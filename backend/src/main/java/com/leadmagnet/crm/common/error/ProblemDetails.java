package com.leadmagnet.crm.common.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;

/** ErrorCode로 RFC 9457 ProblemDetail을 만든다. MVC 예외 처리와 Security 핸들러·서블릿 필터가 함께 쓴다. */
public final class ProblemDetails {

    /** MVC 응답과 같은 모양(확장 속성 code·errors를 최상위로)으로 쓰도록 Spring의 ProblemDetail mixin을 붙인다. */
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class);

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

    /**
     * MVC 밖(서블릿 필터, Security 핸들러)에서 ProblemDetail을 응답에 직접 쓴다.
     * instance는 요청 경로이고, URI로 옮길 수 없는 원문 경로면 비워 둔다.
     */
    public static void write(HttpServletRequest request, HttpServletResponse response, ErrorCode errorCode)
            throws IOException {
        ProblemDetail problem = of(errorCode);
        try {
            problem.setInstance(URI.create(request.getRequestURI()));
        } catch (IllegalArgumentException ignored) {
            // instance 없이 낸다.
        }
        response.setStatus(errorCode.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        MAPPER.writeValue(response.getOutputStream(), problem);
    }
}
