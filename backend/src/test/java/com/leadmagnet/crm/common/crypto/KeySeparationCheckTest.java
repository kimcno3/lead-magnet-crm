package com.leadmagnet.crm.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import com.leadmagnet.crm.auth.config.AuthProperties;
import com.leadmagnet.crm.form.config.FormTokenProperties;
import com.leadmagnet.crm.support.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** JWT_SECRET이 개인정보 키·폼 토큰 키와 같으면(원문 바이트 또는 base64 해독 바이트) 기동이 실패한다. 메시지에 값을 싣지 않는다. */
class KeySeparationCheckTest {

    static final String MESSAGE = "JWT_SECRET은 LEAD_ENC_KEY·LEAD_HMAC_KEY·FORM_TOKEN_KEY와 달라야 합니다";

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({AuthProperties.class, PiiCryptoProperties.class, FormTokenProperties.class})
    static class Properties {
    }

    ApplicationContextRunner runner(String jwt, String enc, String hmac, String formToken) {
        return new ApplicationContextRunner()
            .withUserConfiguration(Properties.class, KeySeparationCheck.class)
            .withPropertyValues("app.auth.admin-origin=http://localhost:5173", "app.auth.jwt-secret=" + jwt,
                "app.pii.enc-key=" + enc, "app.pii.hmac-key=" + hmac, "app.form-token.key=" + formToken);
    }

    @Test
    void JWT_SECRET이_다른_키들과_다르면_기동한다() {
        runner("jwt-secret-that-is-long-enough-and-unique-0123", IntegrationTest.TEST_ENC_KEY,
            IntegrationTest.TEST_HMAC_KEY, IntegrationTest.TEST_FORM_TOKEN_KEY)
            .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void JWT_SECRET이_키와_같은_base64_문자열이면_해독한_바이트가_같아_기동이_실패하고_값은_메시지에_싣지_않는다() {
        String enc = IntegrationTest.TEST_ENC_KEY;
        String hmac = IntegrationTest.TEST_HMAC_KEY;
        String token = IntegrationTest.TEST_FORM_TOKEN_KEY;
        for (String same : new String[] {enc, hmac, token}) {
            runner(same, enc, hmac, token).run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).rootCause()
                    .hasMessageContaining(MESSAGE).hasMessageNotContaining(same);
            });
        }
    }

    @Test
    void JWT_SECRET의_UTF8_바이트가_키_바이트와_같아도_기동이_실패한다() {
        // 32자 ASCII 문자열. 그 바이트를 그대로 base64로 인코딩한 값을 개인정보 키로 쓰는 경우.
        String raw = "0123456789abcdefghijklmnopqrstuv";
        String asKey = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));

        runner(raw, asKey, IntegrationTest.TEST_HMAC_KEY, IntegrationTest.TEST_FORM_TOKEN_KEY).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause()
                .hasMessageContaining(MESSAGE).hasMessageNotContaining(raw).hasMessageNotContaining(asKey);
        });
    }
}
