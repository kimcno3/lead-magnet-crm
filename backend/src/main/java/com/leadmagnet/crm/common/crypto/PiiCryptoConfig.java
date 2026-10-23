package com.leadmagnet.crm.common.crypto;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 키 설정으로 {@link PiiCipher}·{@link PiiHasher} 빈을 만든다 (ADR 0015, 0022). 암호화는 현재 키 id와 이전 키를 모두 가진 키 링으로 만든다. */
@Configuration
public class PiiCryptoConfig {

    @Bean
    PiiCipher piiCipher(PiiCryptoProperties properties) {
        return new PiiCipher(properties.currentKeyId(), properties.encKeyRing());
    }

    @Bean
    PiiHasher piiHasher(PiiCryptoProperties properties) {
        return new PiiHasher(properties.hmacKeyBytes());
    }
}
