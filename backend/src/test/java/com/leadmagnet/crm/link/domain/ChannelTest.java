package com.leadmagnet.crm.link.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.leadmagnet.crm.common.error.DomainException;
import com.leadmagnet.crm.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

class ChannelTest {

    @Test
    void 링크_채널_네_개를_소문자로_파싱한다() {
        assertThat(Channel.parse("instagram")).isEqualTo(Channel.INSTAGRAM);
        assertThat(Channel.parse("x")).isEqualTo(Channel.X);
        assertThat(Channel.parse("youtube")).isEqualTo(Channel.YOUTUBE);
        assertThat(Channel.parse("threads")).isEqualTo(Channel.THREADS);
    }

    @Test
    void direct_모르는_값_빈_값은_422다() {
        for (String bad : new String[] {"direct", "facebook", "Instagram", "", " ", null}) {
            assertThatThrownBy(() -> Channel.parse(bad))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(e.getErrors()).extracting(DomainException.FieldError::field).containsExactly("channel");
                });
        }
    }
}
