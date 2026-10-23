package com.leadmagnet.crm.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.leadmagnet.crm.common.error.DomainException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DateRangeTest {

    @Test
    void 둘_다_없으면_양쪽_모두_열려_있다() {
        DateRange range = DateRange.parse(null, null);

        assertThat(range.fromInstant()).isNull();
        assertThat(range.toExclusiveInstant()).isNull();
    }

    @Test
    void from은_서울_자정_포함_to는_다음날_서울_자정_미포함이다() {
        DateRange range = DateRange.parse("2026-03-11", "2026-03-11");

        // 서울은 UTC+9: 3/11 00:00 KST = 3/10 15:00 UTC
        assertThat(range.fromInstant()).isEqualTo(Instant.parse("2026-03-10T15:00:00Z"));
        assertThat(range.toExclusiveInstant()).isEqualTo(Instant.parse("2026-03-11T15:00:00Z"));
    }

    @Test
    void from만_있어도_to만_있어도_된다() {
        assertThat(DateRange.parse("2026-03-11", null).toExclusiveInstant()).isNull();
        assertThat(DateRange.parse(null, "2026-03-11").fromInstant()).isNull();
    }

    @Test
    void from이_to보다_뒤면_422다() {
        assertThatThrownBy(() -> DateRange.parse("2026-03-12", "2026-03-11"))
            .isInstanceOfSatisfying(DomainException.class, e -> {
                assertThat(e.getErrorCode().status().value()).isEqualTo(422);
                assertThat(e.getErrors()).extracting(DomainException.FieldError::field).containsExactly("from");
            });
    }

    @Test
    void 형식이_틀리면_해당_필드_오류를_모아_422다() {
        assertThatThrownBy(() -> DateRange.parse("2026/03/10", "2026-02-30"))
            .isInstanceOfSatisfying(DomainException.class, e ->
                assertThat(e.getErrors()).extracting(DomainException.FieldError::field)
                    .containsExactly("from", "to"));
        assertThatThrownBy(() -> DateRange.parse("2026-3-1", null)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> DateRange.parse("", null)).isInstanceOf(DomainException.class);
    }

    @Test
    void 허용_연도_범위를_벗어나면_해당_필드_422다() {
        assertThatThrownBy(() -> DateRange.parse("1999-12-31", "+999999999-12-31"))
            .isInstanceOfSatisfying(DomainException.class, e ->
                assertThat(e.getErrors()).extracting(DomainException.FieldError::field)
                    .containsExactly("from", "to"));
        assertThatThrownBy(() -> DateRange.parse(null, "2101-01-01")).isInstanceOf(DomainException.class);
        assertThat(DateRange.parse("2000-01-01", "2100-12-31")).isNotNull();
    }
}
