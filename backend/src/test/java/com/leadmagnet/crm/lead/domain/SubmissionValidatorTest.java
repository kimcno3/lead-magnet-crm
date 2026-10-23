package com.leadmagnet.crm.lead.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.leadmagnet.crm.common.error.DomainException;
import com.leadmagnet.crm.common.error.DomainException.FieldError;
import com.leadmagnet.crm.template.domain.FormField;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SubmissionValidatorTest {

    static final List<FormField> CONFIG = List.of(
        new FormField("name", "text", true, "이름", List.of()),
        new FormField("phone", "tel", true, "전화", List.of()),
        new FormField("email", "email", false, "이메일", List.of()),
        new FormField("job", "select", false, "직업", List.of("student", "worker")),
        new FormField("plan", "radio", false, "플랜", List.of("a", "b")),
        new FormField("interests", "checkbox", false, "관심", List.of("x", "y", "z")),
        new FormField("memo", "textarea", false, "메모", List.of()),
        new FormField("backup_tel", "tel", false, "보조 번호", List.of()),
        new FormField("work_email", "email", false, "회사 메일", List.of()));

    static Map<String, Object> fields(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return map;
    }

    static Map<String, Object> valid(Object... extra) {
        Map<String, Object> map = fields("name", "홍길동", "phone", "010-1234-5678");
        for (int i = 0; i < extra.length; i += 2) {
            map.put((String) extra[i], extra[i + 1]);
        }
        return map;
    }

    static List<FieldError> errorsOf(List<FormField> config, Map<String, Object> fields, boolean consent) {
        return catchDomain(config, fields, consent).getErrors();
    }

    static DomainException catchDomain(List<FormField> config, Map<String, Object> fields, boolean consent) {
        try {
            SubmissionValidator.validate(config, fields, consent);
        } catch (DomainException e) {
            return e;
        }
        throw new AssertionError("검증 실패를 기대했지만 통과했다");
    }

    static void assertError(List<FieldError> errors, String field, String code) {
        assertThat(errors).anySatisfy(e -> {
            assertThat(e.field()).isEqualTo(field);
            assertThat(e.code()).isEqualTo(code);
            assertThat(e.message()).isNotBlank();
        });
    }

    @Test
    void 정상_입력은_name_phone_email을_컬럼_값으로_나머지는_payload로_돌려준다() {
        Submission s = SubmissionValidator.validate(CONFIG,
            valid("email", "Kim@Example.COM", "job", "student", "interests", List.of("x", "z"), "memo", "안녕"), true);

        assertThat(s.name()).isEqualTo("홍길동");
        assertThat(s.phone()).isEqualTo("01012345678");
        assertThat(s.email()).isEqualTo("kim@example.com");
        assertThat(s.payload()).containsOnlyKeys("job", "interests", "memo")
            .containsEntry("job", "student").containsEntry("interests", List.of("x", "z"));
    }

    @Test
    void 비어_있는_선택_필드는_payload에서_뺀다() {
        Submission s = SubmissionValidator.validate(CONFIG,
            valid("email", "", "job", "  ", "interests", List.of(), "memo", ""), true);

        assertThat(s.email()).isNull();
        assertThat(s.payload()).isEmpty();
    }

    @Test
    void 값_앞뒤_공백은_잘라서_저장한다() {
        Submission s = SubmissionValidator.validate(CONFIG, valid("name", "  홍길동  ", "memo", " 메모 "), true);

        assertThat(s.name()).isEqualTo("홍길동");
        assertThat(s.payload()).containsEntry("memo", "메모");
    }

    @Test
    void fieldConfig에_없는_필드는_unknown_field다() {
        List<FieldError> errors = errorsOf(CONFIG, valid("hacker", "x"), true);

        assertError(errors, "hacker", "unknown_field");
    }

    @Test
    void 필수_필드가_없거나_공백이면_required다() {
        List<FieldError> missing = errorsOf(CONFIG, fields("phone", "01012345678"), true);
        List<FieldError> blank = errorsOf(CONFIG, valid("name", "   "), true);

        assertError(missing, "name", "required");
        assertError(blank, "name", "required");
    }

    @Test
    void 필수_checkbox가_빈_배열이면_required다() {
        List<FormField> config = List.of(
            new FormField("phone", "tel", true, "전화", List.of()),
            new FormField("agree", "checkbox", true, "약관", List.of("y")));

        assertError(errorsOf(config, fields("phone", "01012345678", "agree", List.of()), true), "agree", "required");
        assertError(errorsOf(config, fields("phone", "01012345678", "agree", List.of(" ")), true), "agree", "required");
    }

    @Test
    void 값이_1000자를_넘으면_too_long이고_1000자는_통과한다() {
        assertError(errorsOf(CONFIG, valid("memo", "가".repeat(1001)), true), "memo", "too_long");
        assertThat(SubmissionValidator.validate(CONFIG, valid("memo", "가".repeat(1000)), true).payload())
            .containsKey("memo");
    }

    @Test
    void name은_100자를_넘으면_too_long이다() {
        assertError(errorsOf(CONFIG, valid("name", "가".repeat(101)), true), "name", "too_long");
        assertThat(SubmissionValidator.validate(CONFIG, valid("name", "가".repeat(100)), true).name()).hasSize(100);
    }

    @Test
    void checkbox_외에는_배열을_받지_않는다() {
        assertError(errorsOf(CONFIG, valid("memo", List.of("a")), true), "memo", "invalid_value");
        assertError(errorsOf(CONFIG, valid("job", List.of("student")), true), "job", "invalid_value");
        assertError(errorsOf(CONFIG, valid("phone", List.of("01012345678")), true), "phone", "invalid_value");
    }

    @Test
    void NUL이나_C0_제어_문자가_있는_값은_invalid_value다() {
        assertError(errorsOf(CONFIG, valid("name", "홍\u0000길동"), true), "name", "invalid_value");
        assertError(errorsOf(CONFIG, valid("email", "a\u0000@b.co"), true), "email", "invalid_value");
        assertError(errorsOf(CONFIG, valid("phone", "010\u00011234\u00025678"), true), "phone", "invalid_value");
        assertError(errorsOf(CONFIG, valid("memo", "메모\u001B"), true), "memo", "invalid_value");
        assertError(errorsOf(CONFIG, valid("interests", List.of("x", "y\u0000")), true), "interests", "invalid_value");
    }

    @Test
    void 탭과_줄바꿈은_제어_문자여도_받는다() {
        Submission s = SubmissionValidator.validate(CONFIG, valid("memo", "첫 줄\r\n\t둘째 줄"), true);

        assertThat(s.payload()).containsEntry("memo", "첫 줄\r\n\t둘째 줄");
    }

    @Test
    void 문자열도_문자열_배열도_아닌_값은_invalid_value다() {
        assertError(errorsOf(CONFIG, valid("memo", 123), true), "memo", "invalid_value");
        assertError(errorsOf(CONFIG, valid("memo", Map.of("a", "b")), true), "memo", "invalid_value");
        assertError(errorsOf(CONFIG, valid("interests", List.of(1)), true), "interests", "invalid_value");
    }

    @Test
    void 이메일은_형식을_검사하고_소문자로_정규화한다() {
        assertThat(SubmissionValidator.validate(CONFIG, valid("email", " A.B+c@Sub.Example.com "), true).email())
            .isEqualTo("a.b+c@sub.example.com");
        for (String bad : List.of("plain", "a@b", "@example.com", "a@@example.com", "a b@example.com", "a@exa mple.com")) {
            assertError(errorsOf(CONFIG, valid("email", bad), true), "email", "invalid_format");
        }
    }

    @Test
    void 이메일이_254자를_넘으면_invalid_format이다() {
        String tooLong = "a".repeat(250) + "@b.co";

        assertError(errorsOf(CONFIG, valid("email", tooLong), true), "email", "invalid_format");
    }

    @Test
    void email_타입_필드는_이름이_달라도_검사하고_정규화해서_payload에_넣는다() {
        Submission s = SubmissionValidator.validate(CONFIG, valid("work_email", "Boss@Corp.IO"), true);

        assertThat(s.payload()).containsEntry("work_email", "boss@corp.io");
        assertError(errorsOf(CONFIG, valid("work_email", "nope"), true), "work_email", "invalid_format");
    }

    @Test
    void 전화번호는_숫자만_남기고_한국_휴대전화_형식만_받는다() {
        assertThat(SubmissionValidator.validate(CONFIG, valid("phone", "010 1234 5678"), true).phone())
            .isEqualTo("01012345678");
        assertThat(SubmissionValidator.validate(CONFIG, valid("phone", "011-123-4567"), true).phone())
            .isEqualTo("0111234567");
        for (String bad : List.of("02-1234-5678", "010-123-456", "010-12345-67890", "abcdefghijk", "+82 10 1234 5678",
                "015-1234-5678")) {
            assertError(errorsOf(CONFIG, valid("phone", bad), true), "phone", "invalid_format");
        }
    }

    @Test
    void tel_타입_필드는_이름이_달라도_숫자만_남겨_payload에_넣는다() {
        Submission s = SubmissionValidator.validate(CONFIG, valid("backup_tel", "010-9999-8888"), true);

        assertThat(s.payload()).containsEntry("backup_tel", "01099998888");
        assertError(errorsOf(CONFIG, valid("backup_tel", "123"), true), "backup_tel", "invalid_format");
    }

    @Test
    void 이름이_phone이면_타입이_text여도_전화번호_규칙을_적용한다() {
        List<FormField> config = List.of(new FormField("phone", "text", true, "전화", List.of()));

        assertThat(SubmissionValidator.validate(config, fields("phone", "010-1234-5678"), true).phone())
            .isEqualTo("01012345678");
        assertError(errorsOf(config, fields("phone", "hello"), true), "phone", "invalid_format");
    }

    @Test
    void 이름이_email이면_타입이_text여도_이메일_규칙을_적용한다() {
        List<FormField> config = List.of(new FormField("email", "text", true, "메일", List.of()));

        assertThat(SubmissionValidator.validate(config, fields("email", "A@B.CO"), true).email()).isEqualTo("a@b.co");
        assertError(errorsOf(config, fields("email", "hello"), true), "email", "invalid_format");
    }

    @Test
    void select_radio_checkbox는_options에_있는_값만_받는다() {
        assertError(errorsOf(CONFIG, valid("job", "pirate"), true), "job", "invalid_option");
        assertError(errorsOf(CONFIG, valid("plan", "c"), true), "plan", "invalid_option");
        assertError(errorsOf(CONFIG, valid("interests", List.of("x", "q")), true), "interests", "invalid_option");
        assertError(errorsOf(CONFIG, valid("interests", "q"), true), "interests", "invalid_option");
    }

    @Test
    void checkbox에_문자열_하나를_보내도_options에_있으면_받는다() {
        Submission s = SubmissionValidator.validate(CONFIG, valid("interests", "y"), true);

        assertThat(s.payload()).containsEntry("interests", List.of("y"));
    }

    @Test
    void checkbox가_선택지보다_많은_값을_보내면_invalid_value다() {
        assertError(errorsOf(CONFIG, valid("interests", List.of("x", "y", "z", "x")), true), "interests", "invalid_value");
    }

    @Test
    void checkbox에_같은_값이_중복되면_invalid_value다() {
        assertError(errorsOf(CONFIG, valid("interests", List.of("x", "x")), true), "interests", "invalid_value");
    }

    @Test
    void 입력_필드가_50개를_넘으면_too_many_fields_오류_하나만_돌려준다() {
        Map<String, Object> input = valid();
        for (int i = 0; i < 49; i++) {
            input.put("junk" + i, "v");
        }

        List<FieldError> errors = errorsOf(CONFIG, input, false);

        assertThat(errors).singleElement().satisfies(e -> {
            assertThat(e.field()).isEqualTo("fields");
            assertThat(e.code()).isEqualTo("too_many_fields");
        });
    }

    @Test
    void 필드_50개까지는_허용하고_unknown_field_오류는_10개까지만_알린다() {
        Map<String, Object> input = valid();
        for (int i = 0; i < 48; i++) {
            input.put("junk" + i, "v");
        }

        List<FieldError> errors = errorsOf(CONFIG, input, true);

        assertThat(errors).hasSize(10).allSatisfy(e -> assertThat(e.code()).isEqualTo("unknown_field"));
    }

    @Test
    void 동의가_false면_consent_required다() {
        List<FieldError> errors = errorsOf(CONFIG, valid(), false);

        assertError(errors, "consent", "consent_required");
    }

    @Test
    void phone과_email이_모두_비면_contact_required다() {
        List<FormField> config = List.of(
            new FormField("name", "text", false, "이름", List.of()),
            new FormField("phone", "tel", false, "전화", List.of()),
            new FormField("email", "email", false, "메일", List.of()));

        List<FieldError> errors = errorsOf(config, fields("name", "홍길동"), true);

        assertError(errors, "contact", "contact_required");
    }

    @Test
    void phone만_있어도_email만_있어도_연락처_조건을_만족한다() {
        List<FormField> config = List.of(
            new FormField("phone", "tel", false, "전화", List.of()),
            new FormField("email", "email", false, "메일", List.of()));

        assertThat(SubmissionValidator.validate(config, fields("phone", "01012345678"), true).email()).isNull();
        assertThat(SubmissionValidator.validate(config, fields("email", "a@b.co"), true).phone()).isNull();
    }

    @Test
    void 여러_오류를_한_번에_모두_돌려준다() {
        DomainException e = catchDomain(CONFIG, fields("phone", "abc", "hacker", "x", "job", "pirate"), false);

        assertThat(e.getErrorCode().code()).isEqualTo("validation_failed");
        assertThat(e.getErrors()).extracting(FieldError::field, FieldError::code)
            .contains(org.assertj.core.api.Assertions.tuple("name", "required"),
                org.assertj.core.api.Assertions.tuple("phone", "invalid_format"),
                org.assertj.core.api.Assertions.tuple("hacker", "unknown_field"),
                org.assertj.core.api.Assertions.tuple("job", "invalid_option"),
                org.assertj.core.api.Assertions.tuple("consent", "consent_required"));
    }

    @Test
    void 연락처_필드에_오류가_있으면_contact_required를_덧붙이지_않는다() {
        List<FormField> config = List.of(new FormField("phone", "tel", false, "전화", List.of()),
            new FormField("email", "email", false, "메일", List.of()));

        List<FieldError> errors = errorsOf(config, fields("phone", "abc"), true);

        assertThat(errors).extracting(FieldError::code).containsExactly("invalid_format");
    }

    @Test
    void fields가_null이면_빈_입력으로_본다() {
        assertThatThrownBy(() -> SubmissionValidator.validate(CONFIG, null, true))
            .isInstanceOfSatisfying(DomainException.class, e -> assertError(e.getErrors(), "name", "required"));
    }

    @Test
    void 필드_값이_null이면_비어_있는_것으로_본다() {
        Map<String, Object> input = valid();
        input.put("memo", null);

        assertThat(SubmissionValidator.validate(CONFIG, input, true).payload()).isEmpty();
    }
}
