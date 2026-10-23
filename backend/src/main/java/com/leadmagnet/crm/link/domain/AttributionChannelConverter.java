package com.leadmagnet.crm.link.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** 방문·신청의 channel 컬럼. DB CHECK가 소문자 값을 요구하므로 value()를 저장한다. */
@Converter
public class AttributionChannelConverter implements AttributeConverter<AttributionChannel, String> {

    @Override
    public String convertToDatabaseColumn(AttributionChannel attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public AttributionChannel convertToEntityAttribute(String dbData) {
        return dbData == null ? null : AttributionChannel.fromValue(dbData);
    }
}
