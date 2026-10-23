package com.leadmagnet.crm.creator.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** DB의 CHECK 제약이 소문자 값을 요구하므로 enum 이름 대신 value()를 저장한다. */
@Converter
public class CreatorStatusConverter implements AttributeConverter<CreatorStatus, String> {

    @Override
    public String convertToDatabaseColumn(CreatorStatus attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public CreatorStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : CreatorStatus.fromValue(dbData);
    }
}
