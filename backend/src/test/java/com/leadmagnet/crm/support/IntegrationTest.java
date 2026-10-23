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
// 테스트 전용 서명 키와 개인정보 암호화·HMAC 키, 폼 토큰 키. 운영 값은 환경 변수(JWT_SECRET, LEAD_ENC_KEY, LEAD_HMAC_KEY,
// FORM_TOKEN_KEY)로만 받는다. 제출 rate limit은 테스트가 같은 IP로 많이 제출하므로 크게 둔다(한도 자체는 SubmissionRateLimitTest).
// 로그인 시도 제한도 같은 이유로 크게 둔다(한도 자체는 LoginThrottleApiTest). 신뢰 프록시는 루프백으로 두어 admin 포트의
// X-Forwarded-For 해석을 공유 컨텍스트에서 확인한다(public 포트는 그래도 읽지 않는다, ADR 0021).
// 보존 기간 파기 스케줄러는 끄고(수동 실행·서비스로 테스트), 배치는 2건으로 줄여 keyset 페이징을 지나게 한다.
// admin 커넥터는 RANDOM_PORT(@LocalServerPort), public 커넥터도 임의 포트로 띄우고 실제 포트는 PublicPort 빈에서 읽는다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"JWT_SECRET=test-only-jwt-secret-not-for-production-0123456789",
        "LEAD_ENC_KEY=" + IntegrationTest.TEST_ENC_KEY, "LEAD_HMAC_KEY=" + IntegrationTest.TEST_HMAC_KEY,
        "FORM_TOKEN_KEY=" + IntegrationTest.TEST_FORM_TOKEN_KEY,
        "app.public.rate-limit.ip-per-minute=100000", "app.public.rate-limit.visitor-per-minute=100000",
        "app.auth.login-throttle.email-ip-max-failures=100000", "app.auth.login-throttle.email-max-failures=100000",
        "app.auth.login-throttle.ip-max-failures=100000",
        "app.trusted-proxies=127.0.0.1/32,::1/128",
        "app.ports.public-port=0", "app.retention.enabled=false", "app.retention.batch-size=2"})
@Import(TestcontainersConfiguration.class)
public @interface IntegrationTest {

    /** base64(0x00..0x1f). 테스트 전용. */
    String TEST_ENC_KEY = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
    /** base64(0x20..0x3f). 테스트 전용. */
    String TEST_HMAC_KEY = "ICEiIyQlJicoKSorLC0uLzAxMjM0NTY3ODk6Ozw9Pj8=";
    /** base64(0x40..0x5f). 테스트 전용. */
    String TEST_FORM_TOKEN_KEY = "QEFCQ0RFRkdISUpLTE1OT1BRUlNUVVZXWFlaW1xdXl8=";
}
