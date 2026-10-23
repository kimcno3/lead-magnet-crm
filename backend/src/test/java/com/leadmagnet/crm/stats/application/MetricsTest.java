package com.leadmagnet.crm.stats.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MetricsTest {

    @Test
    void 전환율은_HALF_UP으로_소수_넷째_자리까지_반올림한다() {
        // 1/32 = 0.03125 -> HALF_UP 0.0313 (HALF_EVEN이면 0.0312)
        assertThat(new Metrics(0, 32, 0, 1).conversionRate()).isEqualTo(new BigDecimal("0.0313"));
        assertThat(new Metrics(0, 3, 0, 2).conversionRate()).isEqualTo(new BigDecimal("0.6667"));
    }

    @Test
    void 방문자가_0이면_전환율은_0이다() {
        assertThat(new Metrics(0, 0, 4, 0).conversionRate()).isEqualTo(new BigDecimal("0.0000"));
    }
}
