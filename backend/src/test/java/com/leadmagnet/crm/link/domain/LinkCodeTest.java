package com.leadmagnet.crm.link.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.leadmagnet.crm.common.error.DomainException;
import com.leadmagnet.crm.common.error.ErrorCode;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LinkCodeTest {

    @Test
    void 생성한_코드는_base62_8자다() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String code = LinkCode.generate();
            assertThat(code).matches("[0-9A-Za-z]{8}");
            seen.add(code);
        }
        assertThat(seen).hasSizeGreaterThan(990);
    }

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

    @Test
    void 링크_생성시_UTM이_채널과_slug로_정해진다() {
        DistributionLink link = DistributionLink.create(1L, Channel.YOUTUBE, "abCD1234", "spring-2026",
            java.time.Instant.parse("2026-11-01T00:00:00Z"));
        assertThat(link.getUtmSource()).isEqualTo("youtube");
        assertThat(link.getUtmMedium()).isEqualTo("social");
        assertThat(link.getUtmCampaign()).isEqualTo("spring-2026");
    }
}
