package com.leadmagnet.crm.campaign.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.leadmagnet.crm.common.error.DomainException;
import com.leadmagnet.crm.common.error.ErrorCode;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CampaignTest {

    static final Instant NOW = Instant.parse("2026-10-23T00:00:00Z");
    static final Instant LATER = NOW.plusSeconds(3600);

    @Test
    void 생성하면_active이고_생성_시각이_기록된다() {
        Campaign c = Campaign.create("봄 캠페인", null, null, null, null, 1L, NOW);

        assertThat(c.getStatus()).isEqualTo(CampaignStatus.ACTIVE);
        assertThat(c.getCreatedBy()).isEqualTo(1L);
        assertThat(c.getCreatedAt()).isEqualTo(NOW);
        assertThat(c.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void 이름이_비었거나_100자를_넘으면_검증_실패이다() {
        assertInvalid(() -> Campaign.create(" ", null, null, null, null, 1L, NOW));
        assertInvalid(() -> Campaign.create(null, null, null, null, null, 1L, NOW));
        assertInvalid(() -> Campaign.create("a".repeat(101), null, null, null, null, 1L, NOW));
        assertThat(Campaign.create("a".repeat(100), null, null, null, null, 1L, NOW).getName()).hasSize(100);
    }

    @Test
    void 설명이_2000자를_넘으면_검증_실패이다() {
        assertInvalid(() -> Campaign.create("c", "a".repeat(2001), null, null, null, 1L, NOW));
        assertThat(Campaign.create("c", "a".repeat(2000), null, null, null, 1L, NOW)).isNotNull();
    }

    @Test
    void 이름이나_설명에_NUL_같은_제어_문자가_있으면_검증_실패이다() {
        assertInvalid(() -> Campaign.create("봄\u0000", null, null, null, null, 1L, NOW));
        assertInvalid(() -> Campaign.create("봄", "설명\u0007", null, null, null, 1L, NOW));
        Campaign c = Campaign.create("봄", "첫 줄\n둘째 줄\t끝", null, null, null, 1L, NOW);
        assertInvalid(() -> c.update("가을\u0000", null, null, null, null, LATER));
        assertThat(c.getName()).isEqualTo("봄");
    }

    @Test
    void 시작이_종료보다_같거나_늦으면_검증_실패이다() {
        assertInvalid(() -> Campaign.create("c", null, LATER, NOW, null, 1L, NOW));
        assertInvalid(() -> Campaign.create("c", null, NOW, NOW, null, 1L, NOW));
        assertThat(Campaign.create("c", null, NOW, LATER, null, 1L, NOW)).isNotNull();
        assertThat(Campaign.create("c", null, NOW, null, null, 1L, NOW)).isNotNull();
    }

    @Test
    void 수정은_준_값만_바꾸고_updatedAt을_갱신한다() {
        Campaign c = Campaign.create("c", "d", NOW, LATER, null, 1L, NOW);

        c.update("새 이름", null, null, null, null, LATER);

        assertThat(c.getName()).isEqualTo("새 이름");
        assertThat(c.getDescription()).isEqualTo("d");
        assertThat(c.getStartsAt()).isEqualTo(NOW);
        assertThat(c.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    void 수정으로_기간이_뒤집히면_검증_실패이고_값은_그대로다() {
        Campaign c = Campaign.create("c", null, NOW, LATER, null, 1L, NOW);

        assertInvalid(() -> c.update(null, null, LATER.plusSeconds(1), null, null, LATER));
        assertThat(c.getStartsAt()).isEqualTo(NOW);
    }

    @Test
    void 보관하면_archived가_되고_다시_보관하면_409이다() {
        Campaign c = Campaign.create("c", null, null, null, null, 1L, NOW);

        c.archive(LATER);

        assertThat(c.getStatus()).isEqualTo(CampaignStatus.ARCHIVED);
        assertThat(c.getUpdatedAt()).isEqualTo(LATER);
        assertThatThrownBy(() -> c.archive(LATER))
            .isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CAMPAIGN_ALREADY_ARCHIVED));
    }

    @Test
    void 보존_기간을_주지_않으면_365일이다() {
        assertThat(Campaign.create("c", null, null, null, null, 1L, NOW).getRetentionDays()).isEqualTo(365);
    }

    @Test
    void 보존_기간은_1일에서_3650일까지만_받는다() {
        assertThat(Campaign.create("c", null, null, null, 1, 1L, NOW).getRetentionDays()).isEqualTo(1);
        assertThat(Campaign.create("c", null, null, null, 3650, 1L, NOW).getRetentionDays()).isEqualTo(3650);
        assertInvalidField("retentionDays", () -> Campaign.create("c", null, null, null, 0, 1L, NOW));
        assertInvalidField("retentionDays", () -> Campaign.create("c", null, null, null, 3651, 1L, NOW));
        assertInvalidField("retentionDays", () -> Campaign.create("c", null, null, null, -1, 1L, NOW));
    }

    @Test
    void 수정으로_보존_기간을_바꾸고_범위를_벗어나면_값은_그대로다() {
        Campaign c = Campaign.create("c", null, null, null, 30, 1L, NOW);

        c.update(null, null, null, null, 90, LATER);
        assertThat(c.getRetentionDays()).isEqualTo(90);
        assertInvalidField("retentionDays", () -> c.update(null, null, null, null, 0, LATER));
        assertThat(c.getRetentionDays()).isEqualTo(90);
        c.update("새 이름", null, null, null, null, LATER);
        assertThat(c.getRetentionDays()).isEqualTo(90);
    }

    private static void assertInvalidField(String field, Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOfSatisfying(DomainException.class, e -> {
                assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                assertThat(e.getErrors()).extracting(DomainException.FieldError::field).containsExactly(field);
            });
    }

    private static void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }
}
