package com.leadmagnet.crm.stats.application;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 한 묶음(캠페인 또는 캠페인 안의 채널)의 지표. 정의는 ADR 0005. */
public record Metrics(long visits, long visitors, long leads, long convertedVisitors) {

    public static final Metrics ZERO = new Metrics(0, 0, 0, 0);

    /** convertedVisitors / visitors를 소수 넷째 자리까지 반올림(HALF_UP). visitors가 0이면 0.0000. */
    public BigDecimal conversionRate() {
        if (visitors == 0) {
            return BigDecimal.ZERO.setScale(4);
        }
        return BigDecimal.valueOf(convertedVisitors).divide(BigDecimal.valueOf(visitors), 4, RoundingMode.HALF_UP);
    }
}
