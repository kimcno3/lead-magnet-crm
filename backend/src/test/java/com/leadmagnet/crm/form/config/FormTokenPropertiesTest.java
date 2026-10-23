package com.leadmagnet.crm.form.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/** FORM_TOKEN_KEY가 없거나 틀리면 기동이 실패한다(@ConfigurationProperties 바인딩 실패). */
class FormTokenPropertiesTest {

    static final byte[] KEY = new byte[32];
    static final String KEY_B64 = Base64.getEncoder().encodeToString(KEY);

    @Test
    void base64_32바이트_키면_바인딩된다() {
        assertThat(bind(KEY_B64).keyBytes()).isEqualTo(KEY);
        assertThat(bind(KEY_B64).toString()).doesNotContain(KEY_B64);
    }

    @Test
    void 키가_없으면_기동이_실패한다() {
        assertThatThrownBy(() -> bind(null)).isInstanceOf(BindException.class)
            .rootCause().hasMessageContaining("FORM_TOKEN_KEY");
        assertThatThrownBy(() -> bind(" ")).isInstanceOf(BindException.class)
            .rootCause().hasMessageContaining("FORM_TOKEN_KEY");
    }

    @Test
    void base64가_아니거나_32바이트가_아니면_기동이_실패하고_키_값은_메시지에_싣지_않는다() {
        String short16 = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> bind("change-me")).isInstanceOf(BindException.class)
            .rootCause().hasMessageContaining("FORM_TOKEN_KEY").hasMessageNotContaining("change-me");
        assertThatThrownBy(() -> bind(short16)).isInstanceOf(BindException.class)
            .rootCause().hasMessageContaining("32바이트").hasMessageNotContaining(short16);
    }

    private static FormTokenProperties bind(String key) {
        Map<String, String> values = new HashMap<>();
        if (key != null) {
            values.put("app.form-token.key", key);
        }
        return new Binder(new MapConfigurationPropertySource(values))
            .bindOrCreate("app.form-token", FormTokenProperties.class);
    }
}
