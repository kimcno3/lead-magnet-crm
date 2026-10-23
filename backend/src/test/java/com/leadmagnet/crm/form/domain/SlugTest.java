package com.leadmagnet.crm.form.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.leadmagnet.crm.common.error.DomainException;
import com.leadmagnet.crm.common.error.ErrorCode;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SlugTest {

    @ParameterizedTest
    @ValueSource(strings = {"abc", "a-b", "spring-2026", "0a1"})
    void 규칙에_맞는_slug는_받는다(String value) {
        assertThat(new Slug(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a", "ab", "-ab", "ab-", "Ab", "a_b", "a b", "한글", "a--"})
    void 규칙에_어긋나는_slug는_검증_실패이다(String value) {
        assertThatThrownBy(() -> new Slug(value))
            .isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void 길이는_3자에서_64자까지이다() {
        assertThat(new Slug("a".repeat(64)).value()).hasSize(64);
        assertThatThrownBy(() -> new Slug("a".repeat(65))).isInstanceOf(DomainException.class);
    }

    @Test
    void null은_검증_실패이다() {
        assertThatThrownBy(() -> new Slug(null)).isInstanceOf(DomainException.class);
    }

    @Test
    void 생성한_slug는_소문자와_숫자_8자이다() {
        Stream.generate(Slug::random).limit(200)
            .forEach(slug -> assertThat(slug.value()).matches("[a-z0-9]{8}"));
    }
}
