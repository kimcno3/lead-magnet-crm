package com.leadmagnet.crm.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextInputTest {

    @Test
    void NUL과_C0_제어_문자가_있으면_true다() {
        assertThat(TextInput.containsForbiddenControl("a\u0000b")).isTrue();
        assertThat(TextInput.containsForbiddenControl("\u0001")).isTrue();
        assertThat(TextInput.containsForbiddenControl("x\u0008")).isTrue();
        assertThat(TextInput.containsForbiddenControl("\u000Bx")).isTrue();
        assertThat(TextInput.containsForbiddenControl("\u001B[31m")).isTrue();
        assertThat(TextInput.containsForbiddenControl("\u001F")).isTrue();
    }

    @Test
    void 탭_줄바꿈_캐리지리턴과_일반_문자는_허용한다() {
        assertThat(TextInput.containsForbiddenControl("홍길동\t메모\r\n둘째 줄")).isFalse();
        assertThat(TextInput.containsForbiddenControl(" !~\u007F 한글😀")).isFalse();
        assertThat(TextInput.containsForbiddenControl("")).isFalse();
        assertThat(TextInput.containsForbiddenControl(null)).isFalse();
    }
}
