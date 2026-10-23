package com.leadmagnet.crm.lead.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.leadmagnet.crm.template.domain.FormField;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExtraContactsTest {

    private static final List<FormField> CONFIG = List.of(
        new FormField("alt", "tel", false, "보조", List.of()),
        new FormField("note", "text", false, "메모", List.of()),
        new FormField("mail_pref", "checkbox", false, "수신", List.of("y")));

    @Test
    void 타입으로_연락처를_가리고_나머지는_그대로_둔다() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("alt", "01099998888");
        payload.put("note", "안녕");
        payload.put("mail_pref", List.of("y"));

        assertThat(ExtraContacts.mask(payload, CONFIG)).containsEntry("alt", "010-****-8888")
            .containsEntry("note", "안녕").containsEntry("mail_pref", List.of("y"));
    }

    @Test
    void 폼_설정이_없으면_이름만으로_판단한다() {
        Map<String, Object> payload = Map.of("work_mail", "a@b.co", "company", "하늘상사");

        assertThat(ExtraContacts.mask(payload, null)).containsEntry("work_mail", "a***@b.co")
            .containsEntry("company", "하늘상사");
        assertThat(ExtraContacts.raw(payload, null)).containsOnlyKeys("work_mail");
    }

    @Test
    void 형식을_알_수_없는_값도_원문을_남기지_않는다() {
        assertThat(ExtraContacts.mask(Map.of("alt", "abc"), CONFIG)).containsEntry("alt", "****");
        assertThat(ExtraContacts.mask(null, CONFIG)).isNull();
    }
}
