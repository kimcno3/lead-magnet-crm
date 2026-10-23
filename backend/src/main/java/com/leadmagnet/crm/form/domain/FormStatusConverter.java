package com.leadmagnet.crm.form.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** DB의 CHECK 제약이 소문자 값을 요구하므로 enum 이름 대신 value()를 저장한다. */
@Converter
public class FormStatusConverter implements AttributeConverter<FormStatus, String> {

    @Override
    public String convertToDatabaseColumn(FormStatus attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public FormStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : FormStatus.fromValue(dbData);
    }
}
