package com.leadmagnet.crm.form.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.leadmagnet.crm.common.error.DomainException;
import com.leadmagnet.crm.common.error.ErrorCode;
import com.leadmagnet.crm.template.domain.FormField;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class FormTest {

    static final Instant T0 = Instant.parse("2026-10-23T00:00:00Z");
    static final Instant T1 = T0.plusSeconds(60);
    static final Instant T2 = T0.plusSeconds(120);

    static final List<FormField> FIELDS = List.of(
        new FormField("name", "text", true, "이름", null),
        new FormField("plan", "select", false, "플랜", List.of("a", "b")));

    Form draft() {
        return Form.create(1L, 2L, new Slug("spring"), "제목", "동의합니다", FIELDS, T0);
    }

    @Test
    void 생성하면_draft이고_필드_스냅샷을_복사한다() {
        Form f = draft();

        assertThat(f.getStatus()).isEqualTo(FormStatus.DRAFT);
        assertThat(f.getSlug()).isEqualTo("spring");
        assertThat(f.getFieldConfig()).isEqualTo(FIELDS);
        assertThat(f.getPublishedAt()).isNull();
        assertThat(f.getClosedAt()).isNull();
        assertThat(f.getCreatedAt()).isEqualTo(T0);
    }

    @Test
    void 제목과_동의문구가_비었거나_너무_길면_검증_실패이다() {
        assertInvalid(() -> Form.create(1L, 2L, new Slug("spring"), " ", "c", FIELDS, T0));
        assertInvalid(() -> Form.create(1L, 2L, new Slug("spring"), "a".repeat(201), "c", FIELDS, T0));
        assertInvalid(() -> Form.create(1L, 2L, new Slug("spring"), "t", " ", FIELDS, T0));
        assertInvalid(() -> Form.create(1L, 2L, new Slug("spring"), "t", null, FIELDS, T0));
        assertInvalid(() -> Form.create(1L, 2L, new Slug("spring"), "t", "a".repeat(2001), FIELDS, T0));
        assertThat(Form.create(1L, 2L, new Slug("spring"), "a".repeat(200), "a".repeat(2000), FIELDS, T0)).isNotNull();
    }

    @Test
    void 게시하면_published이고_publishedAt이_기록된다() {
        Form f = draft();

        f.publish(T1);

        assertThat(f.getStatus()).isEqualTo(FormStatus.PUBLISHED);
        assertThat(f.getPublishedAt()).isEqualTo(T1);
        assertThat(f.getUpdatedAt()).isEqualTo(T1);
    }

    @Test
    void 마감하면_closed이고_closedAt이_기록된다() {
        Form f = draft();
        f.publish(T1);

        f.close(T2);

        assertThat(f.getStatus()).isEqualTo(FormStatus.CLOSED);
        assertThat(f.getClosedAt()).isEqualTo(T2);
        assertThat(f.getPublishedAt()).isEqualTo(T1);
    }

    @Test
    void 잘못된_전이는_409_invalid_form_transition이다() {
        Form published = draft();
        published.publish(T1);
        Form closed = draft();
        closed.publish(T1);
        closed.close(T2);

        assertTransition(() -> draft().close(T1));
        assertTransition(() -> published.publish(T2));
        assertTransition(() -> closed.publish(T2));
        assertTransition(() -> closed.close(T2));
    }

    @Test
    void draft에서는_내용을_수정할_수_있고_준_값만_바꾼다() {
        Form f = draft();

        f.edit("새 제목", null, null, T1);

        assertThat(f.getTitle()).isEqualTo("새 제목");
        assertThat(f.getConsentText()).isEqualTo("동의합니다");
        assertThat(f.getFieldConfig()).isEqualTo(FIELDS);
        assertThat(f.getUpdatedAt()).isEqualTo(T1);
    }

    @Test
    void fieldConfig는_label과_required만_바꾼다() {
        Form f = draft();

        f.edit(null, null, List.of(
            new FieldChange("name", "성함", false),
            new FieldChange("plan", "플랜", true)), T1);

        assertThat(f.getFieldConfig().get(0).label()).isEqualTo("성함");
        assertThat(f.getFieldConfig().get(0).required()).isFalse();
        assertThat(f.getFieldConfig().get(1).required()).isTrue();
        assertThat(f.getFieldConfig().get(1).options()).containsExactly("a", "b");
    }

    @Test
    void 필드_추가_삭제_이름변경은_검증_실패이고_값은_그대로다() {
        Form f = draft();

        FieldChange name = new FieldChange("name", "이름", true);
        FieldChange plan = new FieldChange("plan", "플랜", false);
        assertInvalid(() -> f.edit(null, null, List.of(name), T1));
        assertInvalid(() -> f.edit(null, null, List.of(name, plan, new FieldChange("extra", "x", false)), T1));
        assertInvalid(() -> f.edit(null, null, List.of(name, new FieldChange("renamed", "플랜", false)), T1));
        assertInvalid(() -> f.edit(null, null, List.of(name, name), T1));
        assertInvalid(() -> f.edit(null, null, List.of(name, new FieldChange("plan", " ", false)), T1));
        assertThat(f.getFieldConfig()).isEqualTo(FIELDS);
    }

    @Test
    void 필드의_label이나_required를_생략하면_현재_값을_유지한다() {
        Form f = draft();

        f.edit(null, null, List.of(new FieldChange("name", null, null), new FieldChange("plan", "새 플랜", null)), T1);

        assertThat(f.getFieldConfig().get(0)).isEqualTo(FIELDS.get(0));
        assertThat(f.getFieldConfig().get(0).required()).isTrue();
        assertThat(f.getFieldConfig().get(1).label()).isEqualTo("새 플랜");
        assertThat(f.getFieldConfig().get(1).required()).isFalse();
    }

    @Test
    void 제목_동의문구_label에_NUL_같은_제어_문자가_있으면_검증_실패이다() {
        assertInvalid(() -> Form.create(1L, 2L, new Slug("spring"), "제목\u0000", "c", FIELDS, T0));
        assertInvalid(() -> Form.create(1L, 2L, new Slug("spring"), "t", "동의\u0000", FIELDS, T0));
        Form f = draft();
        assertInvalid(() -> f.edit("새\u0000", null, null, T1));
        assertInvalid(() -> f.edit(null, null, List.of(new FieldChange("name", "성\u0000함", true),
            new FieldChange("plan", "플랜", false)), T1));
        assertThat(f.getTitle()).isEqualTo("제목");
        assertThat(f.getFieldConfig()).isEqualTo(FIELDS);
        assertThat(Form.create(1L, 2L, new Slug("spring"), "t", "첫 줄\r\n둘째 줄", FIELDS, T0)).isNotNull();
    }

    @Test
    void 검증_실패는_errors에_담길_필드를_가진다() {
        assertThatThrownBy(() -> Form.create(1L, 2L, new Slug("spring"), " ", "c", FIELDS, T0))
            .isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.getErrors()).extracting(DomainException.FieldError::field).containsExactly("title"));
    }

    @Test
    void 잘못된_수정_값은_다른_필드도_바꾸지_않는다() {
        Form f = draft();

        assertInvalid(() -> f.edit("새 제목", " ", null, T1));

        assertThat(f.getTitle()).isEqualTo("제목");
    }

    @Test
    void 공개_가능_여부는_published만_통과하고_draft는_404_closed는_410이다() {
        Form f = draft();
        assertThatThrownBy(f::ensurePublic).isInstanceOf(DomainException.class)
            .extracting(e -> ((DomainException) e).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);

        f.publish(T1);
        f.ensurePublic();

        f.close(T2);
        assertThatThrownBy(f::ensurePublic).isInstanceOf(DomainException.class)
            .extracting(e -> ((DomainException) e).getErrorCode()).isEqualTo(ErrorCode.GONE);
    }

    @Test
    void draft가_아니면_수정은_409_form_not_editable이다() {
        Form published = draft();
        published.publish(T1);
        Form closed = draft();
        closed.publish(T1);
        closed.close(T2);

        for (Form f : List.of(published, closed)) {
            assertThatThrownBy(() -> f.edit("x", null, null, T2))
                .isInstanceOfSatisfying(DomainException.class,
                    e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORM_NOT_EDITABLE));
            assertThat(f.getTitle()).isEqualTo("제목");
        }
    }

    private static void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    private static void assertTransition(Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_FORM_TRANSITION));
    }
}
