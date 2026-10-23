package com.leadmagnet.crm.creator.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.leadmagnet.crm.common.error.DomainException;
import com.leadmagnet.crm.common.error.ErrorCode;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CreatorTest {

    static final Instant NOW = Instant.parse("2026-10-23T00:00:00Z");
    static final Instant LATER = NOW.plusSeconds(3600);

    @Test
    void 생성하면_active이고_이름은_앞뒤_공백을_지우고_빈_핸들과_메모는_null이다() {
        Creator c = Creator.create("  김하늘 ", "  ", "", 1L, NOW);

        assertThat(c.getStatus()).isEqualTo(CreatorStatus.ACTIVE);
        assertThat(c.getName()).isEqualTo("김하늘");
        assertThat(c.getHandle()).isNull();
        assertThat(c.getMemo()).isNull();
        assertThat(c.getCreatedBy()).isEqualTo(1L);
        assertThat(c.getCreatedAt()).isEqualTo(NOW);
        assertThat(c.getUpdatedAt()).isEqualTo(NOW);
        assertThat(c.isArchived()).isFalse();
    }

    @Test
    void 핸들은_앞뒤_공백을_지워_저장한다() {
        assertThat(Creator.create("김하늘", " @sky_kim ", "R/S 20%", 1L, NOW).getHandle()).isEqualTo("@sky_kim");
    }

    @Test
    void 이름이_비었거나_100자를_넘으면_검증_실패이다() {
        assertInvalid("name", () -> Creator.create(" ", null, null, 1L, NOW));
        assertInvalid("name", () -> Creator.create(null, null, null, 1L, NOW));
        assertInvalid("name", () -> Creator.create("a".repeat(101), null, null, 1L, NOW));
        assertThat(Creator.create("a".repeat(100), null, null, 1L, NOW).getName()).hasSize(100);
    }

    @Test
    void 핸들이_100자를_넘거나_메모가_2000자를_넘으면_검증_실패이다() {
        assertInvalid("handle", () -> Creator.create("c", "h".repeat(101), null, 1L, NOW));
        assertInvalid("memo", () -> Creator.create("c", null, "m".repeat(2001), 1L, NOW));
        assertThat(Creator.create("c", "h".repeat(100), "m".repeat(2000), 1L, NOW)).isNotNull();
    }

    @Test
    void 이름_핸들_메모에_NUL_같은_제어_문자가_있으면_검증_실패이다() {
        assertInvalid("name", () -> Creator.create("김\u0000", null, null, 1L, NOW));
        assertInvalid("handle", () -> Creator.create("김", "@a\u0007", null, 1L, NOW));
        assertInvalid("memo", () -> Creator.create("김", null, "메모\u0000", 1L, NOW));
        assertThat(Creator.create("김", null, "첫 줄\n둘째 줄\t끝", 1L, NOW).getMemo()).contains("\n");
    }

    @Test
    void 수정은_준_값만_바꾸고_updatedAt을_갱신한다() {
        Creator c = Creator.create("김하늘", "@sky", "메모", 1L, NOW);

        c.update("김하늘2", null, null, LATER);

        assertThat(c.getName()).isEqualTo("김하늘2");
        assertThat(c.getHandle()).isEqualTo("@sky");
        assertThat(c.getMemo()).isEqualTo("메모");
        assertThat(c.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    void 수정에서_빈_문자열을_주면_핸들과_메모를_지운다() {
        Creator c = Creator.create("김하늘", "@sky", "메모", 1L, NOW);

        c.update(null, "", " ", LATER);

        assertThat(c.getHandle()).isNull();
        assertThat(c.getMemo()).isNull();
    }

    @Test
    void 수정_결과가_규칙을_어기면_아무것도_바꾸지_않는다() {
        Creator c = Creator.create("김하늘", "@sky", null, 1L, NOW);

        assertInvalid("handle", () -> c.update("새 이름", "h".repeat(101), null, LATER));

        assertThat(c.getName()).isEqualTo("김하늘");
        assertThat(c.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void 보관하면_archived가_되고_다시_보관하면_409이다() {
        Creator c = Creator.create("김하늘", null, null, 1L, NOW);

        c.archive(LATER);

        assertThat(c.isArchived()).isTrue();
        assertThat(c.getStatus()).isEqualTo(CreatorStatus.ARCHIVED);
        assertThat(c.getUpdatedAt()).isEqualTo(LATER);
        assertThatThrownBy(() -> c.archive(LATER))
            .isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CREATOR_ALREADY_ARCHIVED));
    }

    private static void assertInvalid(String field, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(DomainException.class, e -> {
            assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
            assertThat(e.getErrors()).extracting(DomainException.FieldError::field).containsExactly(field);
        });
    }
}
