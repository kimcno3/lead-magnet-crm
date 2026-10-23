package com.leadmagnet.crm.common.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/** public 출처가 제공하는 경로(/f/**, /l/**, /api/public/**). OriginGuardFilter와 공개 SecurityFilterChain이 같은 값을 쓴다. */
public final class PublicPaths {

    /** SecurityFilterChain securityMatcher용 패턴. */
    public static final String[] PATTERNS = {"/f/**", "/l/**", "/api/public/**"};

    private static final List<String> PREFIXES = List.of("/f", "/l", "/api/public");

    private PublicPaths() {
    }

    /**
     * Tomcat이 디코딩·정규화한 경로(servletPath + pathInfo)로 판단한다. {@code /f/../api/admin}이나
     * {@code /f/..;/api/admin} 같은 원문 URI는 정규화 후 {@code /api/admin}이 되어 공개 경로로 보지 않는다.
     */
    public static boolean matches(HttpServletRequest request) {
        String path = request.getServletPath() + (request.getPathInfo() == null ? "" : request.getPathInfo());
        return matches(path);
    }

    static boolean matches(String path) {
        return PREFIXES.stream().anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"));
    }
}
