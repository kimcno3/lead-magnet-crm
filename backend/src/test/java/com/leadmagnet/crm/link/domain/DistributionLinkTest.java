package com.leadmagnet.crm.link.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class DistributionLinkTest {

    @Test
    void 링크_생성시_UTM이_채널과_slug로_정해진다() {
        DistributionLink link = DistributionLink.create(1L, Channel.YOUTUBE, "abCD1234", "spring-2026",
            Instant.parse("2026-11-01T00:00:00Z"));
        assertThat(link.getUtmSource()).isEqualTo("youtube");
        assertThat(link.getUtmMedium()).isEqualTo("social");
        assertThat(link.getUtmCampaign()).isEqualTo("spring-2026");
    }
}
