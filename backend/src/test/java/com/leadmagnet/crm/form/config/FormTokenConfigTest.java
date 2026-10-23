package com.leadmagnet.crm.form.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.leadmagnet.crm.common.crypto.PiiCryptoProperties;
import com.leadmagnet.crm.form.domain.FormToken;
import com.leadmagnet.crm.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** FORM_TOKEN_KEY가 개인정보 키와 같으면 기동이 실패한다. */
class FormTokenConfigTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({FormTokenProperties.class, PiiCryptoProperties.class})
    static class Properties {
    }

    final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(Properties.class, FormTokenConfig.class)
        .withPropertyValues("app.pii.enc-key=" + IntegrationTest.TEST_ENC_KEY,
            "app.pii.hmac-key=" + IntegrationTest.TEST_HMAC_KEY);

    @Test
    void 개인정보_키와_다른_키면_기동한다() {
        runner.withPropertyValues("app.form-token.key=" + IntegrationTest.TEST_FORM_TOKEN_KEY)
            .run(context -> assertThat(context).hasNotFailed().hasSingleBean(FormToken.class));
    }

    @Test
    void 개인정보_암호화_키나_HMAC_키와_같으면_기동이_실패하고_키_값은_메시지에_싣지_않는다() {
        for (String same : new String[] {IntegrationTest.TEST_ENC_KEY, IntegrationTest.TEST_HMAC_KEY}) {
            runner.withPropertyValues("app.form-token.key=" + same).run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).rootCause()
                    .hasMessageContaining("FORM_TOKEN_KEY는 LEAD_ENC_KEY·LEAD_HMAC_KEY와 달라야 합니다")
                    .hasMessageNotContaining(same);
            });
        }
    }
}
