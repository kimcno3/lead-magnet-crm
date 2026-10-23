package com.leadmagnet.crm.template.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.leadmagnet.crm.common.error.DomainException;
import com.leadmagnet.crm.common.error.ErrorCode;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class FormContractParserTest {

    private static String page(String formBody) {
        return "<!doctype html><html><body><form action=\"https://evil.example/x\">" + formBody + "</form></body></html>";
    }

    private static void assertRejected(String html, ErrorCode expected) {
        assertThatThrownBy(() -> FormContractParser.parse(html))
            .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    @Test
    void 정상_폼에서_필드_타입_필수여부_라벨을_추출한다() {
        List<FormField> fields = FormContractParser.parse(page("""
            <label for="n">이름</label><input id="n" name="name" required>
            <label>연락처 <input type="TEL" name="phone"></label>
            <input type="email" name="email" placeholder="you@example.com">
            <input type="number" name="age">
            <input type="color" name="fav">
            <textarea name="memo"></textarea>
            <input type="hidden" name="utm" value="x">
            <button type="submit">신청</button>
            <input type="submit" name="go" value="go">
            """));

        assertThat(fields).extracting(FormField::name)
            .containsExactly("name", "phone", "email", "age", "fav", "memo", "utm");
        assertThat(fields).extracting(FormField::type)
            .containsExactly("text", "tel", "email", "number", "text", "textarea", "hidden");
        assertThat(fields).extracting(FormField::required)
            .containsExactly(true, false, false, false, false, false, false);
        assertThat(fields).extracting(FormField::label)
            .containsExactly("이름", "연락처", "you@example.com", "age", "fav", "memo", "utm");
        assertThat(fields).allSatisfy(f -> assertThat(f.options()).isEmpty());
    }

    @Test
    void 라벨은_for_연결_감싼_라벨_placeholder_name_순서로_고른다() {
        List<FormField> fields = FormContractParser.parse(page("""
            <label for="a">for 라벨</label>
            <label>감싼 라벨 <input id="a" name="aa"></label>
            <label>감싼 라벨 <input name="bb"></label>
            <input name="cc" placeholder="힌트">
            <input name="dd" placeholder="">
            <input name="email">
            """));

        assertThat(fields).extracting(FormField::label)
            .containsExactly("for 라벨", "감싼 라벨", "힌트", "dd", "email");
    }

    @Test
    void select의_option_value를_options로_추출하고_value가_없으면_텍스트를_쓴다() {
        List<FormField> fields = FormContractParser.parse(page("""
            <input name="email">
            <select name="plan"><option value="a">에이</option><option>비</option></select>
            """));

        FormField plan = fields.get(1);
        assertThat(plan.type()).isEqualTo("select");
        assertThat(plan.options()).containsExactly("a", "비");
    }

    @Test
    void 같은_이름의_radio와_checkbox는_하나의_필드로_묶는다() {
        List<FormField> fields = FormContractParser.parse(page("""
            <input name="email">
            <input type="radio" name="gender" value="f" required><input type="radio" name="gender" value="m">
            <input type="checkbox" name="topic" value="a"><input type="checkbox" name="topic" value="b">
            """));

        assertThat(fields).hasSize(3);
        assertThat(fields.get(1)).satisfies(f -> {
            assertThat(f.type()).isEqualTo("radio");
            assertThat(f.required()).isTrue();
            assertThat(f.options()).containsExactly("f", "m");
        });
        assertThat(fields.get(2).options()).containsExactly("a", "b");
    }

    @Test
    void radio와_checkbox가_아닌데_이름이_겹치면_거부한다() {
        assertRejected(page("<input name=\"email\"><input name=\"x\"><input name=\"x\">"), ErrorCode.TEMPLATE_FIELD_INVALID);
        assertRejected(page("<input name=\"email\"><input type=\"radio\" name=\"x\" value=\"1\"><input type=\"checkbox\" name=\"x\" value=\"2\">"),
            ErrorCode.TEMPLATE_FIELD_INVALID);
    }

    @Test
    void form이_없으면_거부한다() {
        assertRejected("<html><body><input name=\"email\"></body></html>", ErrorCode.TEMPLATE_FORM_COUNT_INVALID);
    }

    @Test
    void form이_둘이면_거부한다() {
        assertRejected("<form><input name=\"email\"></form><form><input name=\"phone\"></form>",
            ErrorCode.TEMPLATE_FORM_COUNT_INVALID);
    }

    @Test
    void file_입력이_있으면_거부한다() {
        assertRejected(page("<input name=\"email\"><input type=\"file\" name=\"doc\">"), ErrorCode.TEMPLATE_FORBIDDEN_FIELD);
    }

    @Test
    void password_입력이_있으면_거부한다() {
        assertRejected(page("<input name=\"email\"><input type=\"PASSWORD\" name=\"pw\">"), ErrorCode.TEMPLATE_FORBIDDEN_FIELD);
    }

    @Test
    void 필드_이름_규칙을_어기면_거부한다() {
        for (String bad : List.of("1abc", "a-b", "한글", "a b", "a".repeat(51))) {
            assertRejected(page("<input name=\"email\"><input name=\"" + bad + "\">"), ErrorCode.TEMPLATE_FIELD_INVALID);
        }
        assertThat(FormContractParser.parse(page("<input name=\"email\"><input name=\"" + "a".repeat(50) + "\">"))).hasSize(2);
    }

    @Test
    void 필드가_51개이면_거부하고_50개는_허용한다() {
        assertThat(FormContractParser.parse(page(inputs(49) + "<input name=\"email\">"))).hasSize(50);
        assertRejected(page(inputs(50) + "<input name=\"email\">"), ErrorCode.TEMPLATE_FIELD_INVALID);
    }

    @Test
    void phone과_email이_모두_없으면_거부한다() {
        assertRejected(page("<input name=\"name\"><input name=\"company\">"), ErrorCode.TEMPLATE_CONTACT_FIELD_MISSING);
    }

    @Test
    void phone만_있어도_허용한다() {
        assertThat(FormContractParser.parse(page("<input name=\"phone\">"))).hasSize(1);
    }

    private static String inputs(int count) {
        return IntStream.range(0, count).mapToObj(i -> "<input name=\"f" + i + "\">").reduce("", String::concat);
    }
}
