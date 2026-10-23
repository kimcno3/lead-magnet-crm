package com.leadmagnet.crm.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/** 기동 시 @ConfigurationProperties 바인딩이 실패하면 컨텍스트가 뜨지 않는다. 키 규칙을 확인한다. */
class PiiCryptoPropertiesTest {

    static final String ENC = Base64.getEncoder().encodeToString(PiiCipherTest.KEY);
    static final String HMAC = Base64.getEncoder().encodeToString(PiiCipherTest.OTHER_KEY);

    @Test
    void base64_32바이트_키_두_개면_바인딩된다() {
        PiiCryptoProperties properties = bind(ENC, HMAC);

        assertThat(properties.encKeyBytes()).isEqualTo(PiiCipherTest.KEY);
        assertThat(properties.hmacKeyBytes()).isEqualTo(PiiCipherTest.OTHER_KEY);
    }

    @Test
    void 암호화_키가_없으면_기동이_실패한다() {
        assertThatThrownBy(() -> bind(null, HMAC)).isInstanceOf(BindException.class)
            .rootCause().hasMessageContaining("LEAD_ENC_KEY");
        assertThatThrownBy(() -> bind("", HMAC)).isInstanceOf(BindException.class);
    }

    @Test
    void HMAC_키가_없으면_기동이_실패한다() {
        assertThatThrownBy(() -> bind(ENC, null)).isInstanceOf(BindException.class)
            .rootCause().hasMessageContaining("LEAD_HMAC_KEY");
    }

    @Test
    void 길이가_32바이트가_아니면_기동이_실패하고_키_값은_메시지에_싣지_않는다() {
        String short16 = Base64.getEncoder().encodeToString(new byte[16]);
        String long48 = Base64.getEncoder().encodeToString(new byte[48]);

        assertThatThrownBy(() -> bind(short16, HMAC)).isInstanceOf(BindException.class)
            .rootCause().hasMessageContaining("LEAD_ENC_KEY").hasMessageContaining("32바이트")
            .hasMessageNotContaining(short16);
        assertThatThrownBy(() -> bind(ENC, long48)).isInstanceOf(BindException.class)
            .rootCause().hasMessageContaining("LEAD_HMAC_KEY");
    }

    @Test
    void base64가_아니면_기동이_실패한다() {
        assertThatThrownBy(() -> bind("change-me!!", HMAC)).isInstanceOf(BindException.class)
            .rootCause().hasMessageContaining("LEAD_ENC_KEY").hasMessageNotContaining("change-me!!");
    }

    @Test
    void 두_키가_같으면_기동이_실패한다() {
        assertThatThrownBy(() -> bind(ENC, ENC)).isInstanceOf(BindException.class)
            .rootCause().hasMessageContaining("달라야");
    }

    private static PiiCryptoProperties bind(String enc, String hmac) {
        Map<String, String> values = new HashMap<>();
        if (enc != null) {
            values.put("app.pii.enc-key", enc);
        }
        if (hmac != null) {
            values.put("app.pii.hmac-key", hmac);
        }
        return new Binder(new MapConfigurationPropertySource(values)).bindOrCreate("app.pii", PiiCryptoProperties.class);
    }
}
