package com.leadmagnet.crm.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** 실제 PostgreSQL(Testcontainers)과 Flyway 마이그레이션이 적용된 전체 컨텍스트로 테스트한다. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
// 테스트 전용 서명 키와 개인정보 암호화·HMAC 키. 운영 값은 환경 변수(JWT_SECRET, LEAD_ENC_KEY, LEAD_HMAC_KEY)로만 받는다.
// admin 커넥터는 RANDOM_PORT(@LocalServerPort), public 커넥터도 임의 포트로 띄우고 실제 포트는 PublicPort 빈에서 읽는다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"JWT_SECRET=test-only-jwt-secret-not-for-production-0123456789",
        "LEAD_ENC_KEY=" + IntegrationTest.TEST_ENC_KEY, "LEAD_HMAC_KEY=" + IntegrationTest.TEST_HMAC_KEY,
        "app.ports.public-port=0"})
@Import(TestcontainersConfiguration.class)
public @interface IntegrationTest {

    /** base64(0x00..0x1f). 테스트 전용. */
    String TEST_ENC_KEY = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
    /** base64(0x20..0x3f). 테스트 전용. */
    String TEST_HMAC_KEY = "ICEiIyQlJicoKSorLC0uLzAxMjM0NTY3ODk6Ozw9Pj8=";
}
