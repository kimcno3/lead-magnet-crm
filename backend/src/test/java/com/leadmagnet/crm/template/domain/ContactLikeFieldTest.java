package com.leadmagnet.crm.template.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.leadmagnet.crm.template.domain.ContactLikeField.Kind;
import org.junit.jupiter.api.Test;

class ContactLikeFieldTest {

    @Test
    void tel_email_타입은_이름과_상관없이_연락처다() {
        assertThat(ContactLikeField.kindOf("alt", "tel")).isEqualTo(Kind.PHONE);
        assertThat(ContactLikeField.kindOf("alt", "email")).isEqualTo(Kind.EMAIL);
    }

    @Test
    void 이름_토큰이_맞으면_텍스트_계열_입력도_연락처다() {
        assertThat(ContactLikeField.kindOf("mobile", "text")).isEqualTo(Kind.PHONE);
        assertThat(ContactLikeField.kindOf("phone_2", "text")).isEqualTo(Kind.PHONE);
        assertThat(ContactLikeField.kindOf("home-tel", "number")).isEqualTo(Kind.PHONE);
        assertThat(ContactLikeField.kindOf("HP", "text")).isEqualTo(Kind.PHONE);
        assertThat(ContactLikeField.kindOf("cellNumber", "text")).isEqualTo(Kind.PHONE);
        assertThat(ContactLikeField.kindOf("work_mail", "text")).isEqualTo(Kind.EMAIL);
        assertThat(ContactLikeField.kindOf("Email2", "text")).isEqualTo(Kind.EMAIL);
        assertThat(ContactLikeField.kindOf("연락처", "text")).isEqualTo(Kind.PHONE);
        assertThat(ContactLikeField.kindOf("전화번호", "text")).isEqualTo(Kind.PHONE);
    }

    @Test
    void 토큰이_아닌_부분_일치와_선택지_타입은_연락처가_아니다() {
        assertThat(ContactLikeField.kindOf("hotel", "text")).isNull();
        assertThat(ContactLikeField.kindOf("shp", "text")).isNull();
        assertThat(ContactLikeField.kindOf("email_opt_in", "checkbox")).isNull();
        assertThat(ContactLikeField.kindOf("phone_pref", "select")).isNull();
        assertThat(ContactLikeField.kindOf("mail_note", "textarea")).isNull();
        assertThat(ContactLikeField.kindOf("company", "text")).isNull();
    }

    @Test
    void 예약_이름은_추가_연락처가_아니다() {
        assertThat(ContactLikeField.isExtra("phone", "tel")).isFalse();
        assertThat(ContactLikeField.isExtra("email", "email")).isFalse();
        assertThat(ContactLikeField.isExtra("phone2", "tel")).isTrue();
    }
}
