package com.leadmagnet.crm.lead.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadmagnet.crm.common.error.ErrorCode;
import com.leadmagnet.crm.common.error.ProblemDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 신청 제출 경로({@code POST /api/public/forms/{slug}/submissions})의 본문을 읽기 전에 거르는 필터.
 * 인증 없는 공개 입력이라 서버가 경계다.
 * <ul>
 *   <li>{@code Origin: null}(sandbox 프레임)은 403 forbidden_origin. Origin이 없거나 실제 출처면 통과</li>
 *   <li>본문이 maxBytes를 넘으면 413 payload_too_large. Content-Length로 먼저 거르고, chunked 본문은
 *       maxBytes + 1 바이트까지만 읽어 판단한다(메모리 상한이 곧 maxBytes)</li>
 * </ul>
 */
public class SubmissionGuardFilter extends OncePerRequestFilter {

    private static final Pattern PATH = Pattern.compile("^/api/public/forms/[^/]+/submissions$");

    private final long maxBytes;
    private final ObjectMapper objectMapper;

    public SubmissionGuardFilter(long maxBytes, ObjectMapper objectMapper) {
        this.maxBytes = maxBytes;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath() + (request.getPathInfo() == null ? "" : request.getPathInfo());
        return !HttpMethod.POST.matches(request.getMethod()) || !PATH.matcher(path).matches();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ("null".equals(request.getHeader("Origin"))) {
            reject(request, response, ErrorCode.FORBIDDEN_ORIGIN);
            return;
        }
        if (request.getContentLengthLong() > maxBytes) {
            reject(request, response, ErrorCode.PAYLOAD_TOO_LARGE);
            return;
        }
        byte[] body = request.getInputStream().readNBytes((int) maxBytes + 1);
        if (body.length > maxBytes) {
            reject(request, response, ErrorCode.PAYLOAD_TOO_LARGE);
            return;
        }
        chain.doFilter(new BufferedBodyRequest(request, body), response);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, ErrorCode code) throws IOException {
        ProblemDetail problem = ProblemDetails.of(code);
        try {
            problem.setInstance(URI.create(request.getRequestURI()));
        } catch (IllegalArgumentException ignored) {
            // URI로 옮길 수 없는 경로는 instance를 비워 둔다.
        }
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }

    /** 이미 읽은 본문을 다시 읽게 해 주는 요청 래퍼. */
    private static final class BufferedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        BufferedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return in.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public int read() {
                    return in.read();
                }

                @Override
                public int read(byte[] b, int off, int len) {
                    return in.read(b, off, len);
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
            InputStream in = new ByteArrayInputStream(body);
            return new BufferedReader(new InputStreamReader(in, charset));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }
}
