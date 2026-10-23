package com.leadmagnet.crm.common.error;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 모든 에러를 RFC 9457 ProblemDetail로 낸다.
 * <ul>
 *   <li>DomainException: 예외가 가진 ErrorCode</li>
 *   <li>검증 실패(본문·파라미터): 422 {@code validation_failed}와 {@code errors: [{field, message}]}</li>
 *   <li>읽을 수 없는 본문: 400 {@code malformed_request}</li>
 *   <li>그 밖의 Spring MVC 표준 예외(404, 405, 415, 413 등): 상위 클래스가 정한 상태에 {@link ErrorCode#forStatus}로 type·code를 채운다</li>
 *   <li>처리하지 못한 예외: 500 {@code internal_error}. 메시지에 개인정보가 섞일 수 있어 응답과 로그에 메시지를 남기지 않는다</li>
 * </ul>
 * {@code instance}는 Spring MVC가 ProblemDetail을 쓸 때 요청 경로로 채운다(Security 핸들러도 같은 값을 넣는다).
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ProblemDetail> handleDomain(DomainException e) {
        ErrorCode code = e.getErrorCode();
        ProblemDetail problem = ProblemDetails.of(code, e.getMessage());
        if (!e.getErrors().isEmpty()) {
            problem.setProperty("errors", e.getErrors().stream()
                .map(error -> Map.of("field", error.field(), "message", error.message()))
                .toList());
        }
        return ResponseEntity.status(code.status()).body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception e, HttpServletRequest request) {
        StackTraceElement origin = e.getStackTrace().length > 0 ? e.getStackTrace()[0] : null;
        log.error("처리하지 못한 예외: {} {} → {} at {}",
            request.getMethod(), request.getRequestURI(), e.getClass().getName(), origin);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status()).body(ProblemDetails.of(ErrorCode.INTERNAL_ERROR));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = e.getBindingResult().getFieldErrors().stream()
            .map(GlobalExceptionHandler::fieldError)
            .toList();
        return validationFailed(errors, headers);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException e,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = new ArrayList<>();
        for (ParameterValidationResult result : e.getParameterValidationResults()) {
            if (result instanceof ParameterErrors parameterErrors) {
                parameterErrors.getFieldErrors().forEach(error -> errors.add(fieldError(error)));
            } else {
                String field = result.getMethodParameter().getParameterName();
                result.getResolvableErrors().forEach(error ->
                    errors.add(Map.of("field", String.valueOf(field),
                        "message", String.valueOf(error.getDefaultMessage()))));
            }
        }
        return validationFailed(errors, headers);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException e,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.status(ErrorCode.MALFORMED_REQUEST.status()).headers(headers)
            .body(ProblemDetails.of(ErrorCode.MALFORMED_REQUEST));
    }

    /** 상위 클래스가 만든 ProblemDetail에 우리 type·code를 붙인다. 상태와 detail은 그대로 둔다. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception e, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(e, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem
                && (problem.getProperties() == null || !problem.getProperties().containsKey("code"))) {
            ErrorCode code = ErrorCode.forStatus(response.getStatusCode().value());
            problem.setType(URI.create(code.type()));
            problem.setProperty("code", code.code());
        }
        return response;
    }

    private static ResponseEntity<Object> validationFailed(List<Map<String, String>> errors, HttpHeaders headers) {
        ProblemDetail problem = ProblemDetails.of(ErrorCode.VALIDATION_FAILED);
        problem.setProperty("errors", errors);
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status()).headers(headers).body(problem);
    }

    private static Map<String, String> fieldError(FieldError error) {
        return Map.of("field", error.getField(), "message", String.valueOf(error.getDefaultMessage()));
    }
}
