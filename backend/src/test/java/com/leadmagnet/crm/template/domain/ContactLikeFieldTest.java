package com.leadmagnet.crm.template.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.leadmagnet.crm.template.domain.ContactLikeField.Kind;
import java.util.List;
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
    }

    @Test
    void 합성어와_접두어_토큰도_연락처다() {
        for (String name : List.of("telephone", "cellphone", "mobilephone", "phonenumber", "phoneno", "phone_model",
            "mobileNo", "phone2")) {
            assertThat(ContactLikeField.kindOf(name, "text")).as(name).isEqualTo(Kind.PHONE);
        }
        for (String name : List.of("emailaddress", "mailaddress", "useremail", "email_backup", "emailAddr")) {
            assertThat(ContactLikeField.kindOf(name, "text")).as(name).isEqualTo(Kind.EMAIL);
        }
    }

    @Test
    void 부분_문자열만_겹치는_이름은_연락처가_아니다() {
        for (String name : List.of("telegram", "mailbox_size", "hotel", "headphone", "microphone", "intel", "hpv")) {
            assertThat(ContactLikeField.kindOf(name, "text")).as(name).isNull();
        }
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
        assertThat(ContactLikeField.extraKindOf("phone", "tel")).isNull();
        assertThat(ContactLikeField.extraKindOf("email", "text")).isNull();
        assertThat(ContactLikeField.extraKindOf("phone2", "tel")).isEqualTo(Kind.PHONE);
    }
}
