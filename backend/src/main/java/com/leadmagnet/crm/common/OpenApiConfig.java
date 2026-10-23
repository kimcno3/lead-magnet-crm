package com.leadmagnet.crm.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import java.util.TreeMap;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 문서 메타데이터. 문서 엔드포인트는 admin 포트에서만 열리지만(ADR 0010) 공개 컨트롤러도 같은 컨텍스트에 있어
 * 한 문서에 함께 담긴다. 공개 경로(/f, /l, /api/public)는 오퍼레이션 단위 servers로 public 출처(:8081)를 가리킨다.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    private static final List<String> PUBLIC_PREFIXES = List.of("/f/", "/l/", "/api/public/");

    @Bean
    OpenAPI leadMagnetOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("리드마그넷 CRM API")
                .version("0.1.0")
                .description("""
                    관리자 API(`/api/admin/**`)는 admin 출처(기본 :8080)에서 Bearer access token으로 호출한다.
                    공개 경로(`/f/{slug}`, `/l/{code}`, `/api/public/**`)는 같은 앱이지만 public 출처
                    (`PUBLIC_BASE_URL`, 기본 :8081)에서만 열리고 인증이 없다. 서로의 포트에서는 404다 (ADR 0010).
                    에러는 모두 ProblemDetail(`application/problem+json`, 확장 속성 `code`)이다."""))
            .servers(List.of(new Server().url("http://localhost:8080").description("admin 출처 (관리자 API, 이 문서)")));
    }

    @Bean
    OpenApiCustomizer publicPathServers() {
        Server publicServer = new Server().url("http://localhost:8081")
            .description("public 출처 (PUBLIC_BASE_URL). /f, /l, /api/public 경로는 이 출처에서만 열린다");
        return openApi -> {
            openApi.getPaths().forEach((path, item) -> {
                if (PUBLIC_PREFIXES.stream().anyMatch(path::startsWith)) {
                    item.readOperations().forEach(op -> op.setServers(List.of(publicServer)));
                }
            });
            // 컨트롤러 스캔 순서에 기대지 않도록 경로를 정렬해 내보낸 파일이 항상 같게 한다
            Paths sorted = new Paths();
            new TreeMap<>(openApi.getPaths()).forEach(sorted::addPathItem);
            openApi.setPaths(sorted);
        };
    }
}
