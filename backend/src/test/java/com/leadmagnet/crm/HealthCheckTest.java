package com.leadmagnet.crm;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import com.leadmagnet.crm.common.PortsProperties;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthCheckTest {

    @LocalServerPort
    int port;

    @Autowired
    PortsProperties ports;

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
    void 노출하지_않은_actuator_엔드포인트는_404다() {
        given()
            .when().get("/actuator/env")
            .then().statusCode(404);
    }

    @Test
    void 출처별_포트_기본값은_8080과_8081이다() {
        assertThat(ports.admin()).isEqualTo(8080);
        assertThat(ports.publicPort()).isEqualTo(8081);
    }
}
