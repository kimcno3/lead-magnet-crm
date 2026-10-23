package com.leadmagnet.crm;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import com.leadmagnet.crm.support.IntegrationTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

@IntegrationTest
class HealthCheckTest {

    @LocalServerPort
    int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    void 헬스체크는_UP을_반환한다() {
        given()
            .when().get("/actuator/health")
            .then().statusCode(200).body("status", equalTo("UP"));
    }

    @Test
    void 노출하지_않은_actuator_엔드포인트는_인증_없이_막힌다() {
        given()
            .when().get("/actuator/env")
            .then().statusCode(401);
    }
}
