package com.leadmagnet.crm.stats.domain;

import com.leadmagnet.crm.common.error.DomainException;
import com.leadmagnet.crm.common.error.DomainException.FieldError;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 지표 조회 기간. 날짜는 Asia/Seoul 기준(ADR 0005)이고 양쪽 모두 선택이다(없으면 그쪽은 무제한).
 * 시각 범위는 [from 00:00, to 다음날 00:00)이다.
 */
public final class DateRange {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final Instant from;
    private final Instant toExclusive;

    private DateRange(Instant from, Instant toExclusive) {
        this.from = from;
        this.toExclusive = toExclusive;
    }

    /** yyyy-MM-dd 문자열(null 가능)을 받는다. 형식이 틀리거나 from이 to보다 뒤면 422. */
    public static DateRange parse(String from, String to) {
        List<FieldError> errors = new ArrayList<>();
        LocalDate fromDate = parseDate("from", from, errors);
        LocalDate toDate = parseDate("to", to, errors);
        if (errors.isEmpty() && fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            errors.add(new FieldError("from", "from은 to보다 늦을 수 없습니다", "invalid_range"));
        }
        if (!errors.isEmpty()) {
            throw DomainException.validation(errors);
        }
        try {
            return new DateRange(fromDate == null ? null : fromDate.atStartOfDay(ZONE).toInstant(),
                toDate == null ? null : toDate.plusDays(1).atStartOfDay(ZONE).toInstant());
        } catch (DateTimeException | ArithmeticException e) {
            throw DomainException.validation("to", "지원하지 않는 날짜 범위입니다");
        }
    }

    private static LocalDate parseDate(String field, String value, List<FieldError> errors) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeException e) {
            errors.add(new FieldError(field, field + "은 yyyy-MM-dd 형식이어야 합니다", "invalid_format"));
            return null;
        }
    }

    /** 포함하는 시작 시각. 무제한이면 null. */
    public Instant fromInstant() {
        return from;
    }

    /** 포함하지 않는 끝 시각(to 다음날 00:00). 무제한이면 null. */
    public Instant toExclusiveInstant() {
        return toExclusive;
    }
}
