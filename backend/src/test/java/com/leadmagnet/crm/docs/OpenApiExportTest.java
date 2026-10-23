package com.leadmagnet.crm.docs;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

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
 * 기본은 파일을 덮어쓴다. -Dopenapi.verify=true(CI)이면 쓰지 않고 커밋된 파일과 다르면 실패한다.
 * 갱신: cd backend && ./gradlew test --tests '*OpenApiExportTest'
 */
@IntegrationTest
// 테스트 전용 ErrorProbeController는 문서에 넣지 않는다
@TestPropertySource(properties = "springdoc.paths-to-exclude=/api/admin/__test/**")
class OpenApiExportTest {

    private static final Path TARGET = Path.of("..", "docs", "api", "openapi.yaml");

    @LocalServerPort
    int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    private String fetch(String path) {
        return given().when().get(path).then().statusCode(200).extract().asString();
    }

    @Test
    void 문서는_공개_엔드포인트와_Bearer_스키마까지_담는다() {
        String yaml = fetch("/v3/api-docs.yaml");

        assertThat(yaml)
            .contains("/api/admin/auth/login", "/api/admin/templates", "/api/admin/campaigns/{campaignId}/leads",
                "/api/admin/forms/{formId}/links", "/api/admin/stats/campaigns")
            .contains("/f/{slug}", "/l/{code}", "/api/public/forms/{slug}", "/api/public/forms/{slug}/submissions")
            .contains("bearerFormat: JWT", "http://localhost:8081", "problem+json");
    }

    @Test
    void 내보낸_문서가_커밋된_파일과_같다() throws IOException {
        String generated = fetch("/v3/api-docs.yaml");
        if (!generated.endsWith("\n")) {
            generated += "\n";
        }

        if (Boolean.getBoolean("openapi.verify")) {
            assertThat(Files.exists(TARGET)).as("docs/api/openapi.yaml 없음. 먼저 내보내기를 실행한다").isTrue();
            assertThat(Files.readString(TARGET, StandardCharsets.UTF_8))
                .as("docs/api/openapi.yaml이 최신이 아니다. ./gradlew test --tests '*OpenApiExportTest'로 갱신한다")
                .isEqualTo(generated);
            return;
        }
        Files.createDirectories(TARGET.getParent());
        Files.writeString(TARGET, generated, StandardCharsets.UTF_8);
    }
}
