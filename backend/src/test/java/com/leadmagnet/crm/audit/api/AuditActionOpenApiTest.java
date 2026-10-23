package com.leadmagnet.crm.audit.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadmagnet.crm.audit.domain.AuditAction;
import com.leadmagnet.crm.support.IntegrationTest;
import io.restassured.RestAssured;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

/** 감사 로그 action 파라미터의 허용 값은 손으로 적지 않고 {@link AuditAction}에서 만든다. */
@IntegrationTest
class AuditActionOpenApiTest {

    @LocalServerPort
    int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    JsonNode operation() throws IOException {
        String json = given().get("/v3/api-docs").then().statusCode(200).extract().asString();
        return new ObjectMapper().readTree(json).at("/paths/~1api~1admin~1audit-logs/get");
    }

    JsonNode actionParameter() throws IOException {
        for (JsonNode p : operation().get("parameters")) {
            if ("action".equals(p.get("name").asText())) {
                return p;
            }
        }
        throw new AssertionError("action 파라미터가 문서에 없다");
    }

    @Test
    void action_파라미터의_허용_값은_AuditAction_전부와_정확히_같다() throws IOException {
        List<String> documented = new ArrayList<>();
        actionParameter().at("/schema/enum").forEach(n -> documented.add(n.asText()));

        List<String> expected = Arrays.stream(AuditAction.values()).map(AuditAction::value).toList();
        assertThat(documented).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void 오퍼레이션_설명에_손으로_적은_action_목록이_남아_있지_않다() throws IOException {
        String description = operation().get("description").asText();

        for (AuditAction action : AuditAction.values()) {
            assertThat(description).as(action.value()).doesNotContain(action.value());
        }
    }
}
