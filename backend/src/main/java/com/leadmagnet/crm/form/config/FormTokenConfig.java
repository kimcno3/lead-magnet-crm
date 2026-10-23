package com.leadmagnet.crm.form.config;

import com.leadmagnet.crm.common.crypto.PiiCryptoProperties;
import com.leadmagnet.crm.form.domain.FormToken;
import java.util.Arrays;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 키 설정으로 {@link FormToken} 빈을 만든다 (ADR 0019). 폼 토큰 키는 개인정보 키(LEAD_ENC_KEY·LEAD_HMAC_KEY)와
 * (이전 키 포함) 같으면 기동을 멈춘다: 공개로 나가는 토큰의 서명 키가 개인정보 키와 같으면 한쪽이 새면 다른 쪽도 샌다.
 * 오류 메시지에는 키 값을 싣지 않는다.
 */
@Configuration(proxyBeanMethods = false)
public class FormTokenConfig {

    @Bean
    FormToken formToken(FormTokenProperties properties, PiiCryptoProperties pii) {
        byte[] key = properties.keyBytes();
        // 이전 키(LEAD_ENC_PREVIOUS_KEYS·LEAD_HMAC_PREVIOUS_KEY)도 포함한다 (ADR 0022).
        if (pii.candidates().stream().anyMatch(other -> Arrays.equals(key, other))) {
            throw new IllegalArgumentException(
                "FORM_TOKEN_KEY는 LEAD_ENC_KEY·LEAD_HMAC_KEY와 달라야 합니다(이전 키 포함)");
        }
        return new FormToken(key);
    }
}
