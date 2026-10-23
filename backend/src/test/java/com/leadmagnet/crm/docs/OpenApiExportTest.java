package com.leadmagnet.crm.docs;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadmagnet.crm.support.IntegrationTest;
import io.restassured.RestAssured;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

/**
 * 실행 중인 앱의 /v3/api-docs.yaml을 docs/api/openapi.yaml로 내보낸다.
 * 기본은 파일을 덮어쓰고, -Dopenapi.verify=true(CI)이면 쓰지 않고 커밋된 파일과 다르면 실패한다.
 * 갱신: cd backend && ./gradlew test --tests '*OpenApiExportTest'
 * 출력 경로는 Gradle이 openapi.output 시스템 속성으로 넘긴다(작업 디렉터리에 의존하지 않는다).
 */
@IntegrationTest
// 테스트 전용 ErrorProbeController는 문서에 넣지 않는다
@TestPropertySource(properties = "springdoc.paths-to-exclude=/api/admin/__test/**")
class OpenApiExportTest {

    private static final boolean VERIFY = Boolean.getBoolean("openapi.verify");

    @LocalServerPort
    int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    private String fetch(String path) {
        return given().when().get(path).then().statusCode(200).extract().asString();
    }

    private static Path target() {
        String output = System.getProperty("openapi.output");
        assertThat(output).as("Gradle이 openapi.output을 넘긴다(IDE 실행이면 -Dopenapi.output=<경로>)").isNotBlank();
        return Path.of(output);
    }

    private String generated() {
        String yaml = fetch("/v3/api-docs.yaml");
        return yaml.endsWith("\n") ? yaml : yaml + "\n";
    }

    @Test
    void 문서는_공개_엔드포인트와_Bearer_스키마까지_담는다() {
        String yaml = fetch("/v3/api-docs.yaml");

        assertThat(yaml)
            .contains("/api/admin/auth/login", "/api/admin/templates", "/api/admin/campaigns/{campaignId}/leads",
                "/api/admin/campaigns/{campaignId}/forms", "/api/admin/forms/{formId}/links",
                "/api/admin/stats/campaigns")
            .contains("/f/{slug}", "/l/{code}", "/api/public/forms/{slug}", "/api/public/forms/{slug}/submissions")
            .contains("bearerFormat: JWT", "http://localhost:8081", "problem+json");
    }

    @Test
    void 로그인_갱신_로그아웃을_뺀_관리자_오퍼레이션은_모두_bearer_보안을_요구한다() throws IOException {
        JsonNode paths = new ObjectMapper().readTree(fetch("/v3/api-docs")).get("paths");
        java.util.Set<String> open = java.util.Set.of("/api/admin/auth/login", "/api/admin/auth/refresh",
            "/api/admin/auth/logout");

        paths.fieldNames().forEachRemaining(path -> {
            if (!path.startsWith("/api/admin/") || open.contains(path)) {
                return;
            }
            paths.get(path).fields().forEachRemaining(op -> assertThat(op.getValue().path("security").toString())
                .as("%s %s", op.getKey(), path).contains("bearer"));
        });
    }

    @Test
    void 내보내기는_문서를_파일로_쓴다() throws IOException {
        assumeThat(VERIFY).as("verify 모드에서는 쓰지 않는다").isFalse();

        Path target = target();
        Files.createDirectories(target.getParent());
        Files.writeString(target, generated(), StandardCharsets.UTF_8);
    }

    @Test
    void 커밋된_문서가_앱이_내보낸_문서와_같다() throws IOException {
        assumeThat(VERIFY).as("-Dopenapi.verify=true일 때만 비교한다").isTrue();

        Path target = target();
        String hint = "docs/api/openapi.yaml이 최신이 아니다. 'cd backend && ./gradlew test --tests *OpenApiExportTest'로 "
            + "다시 만들어 커밋한다";
        assertThat(target).as(hint).exists();
        assertThat(Files.readString(target, StandardCharsets.UTF_8)).as(hint).isEqualTo(generated());
    }
}
