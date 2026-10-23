package com.leadmagnet.crm.link.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** DB의 CHECK 제약이 소문자 값을 요구하므로 enum 이름 대신 value()를 저장한다. */
@Converter
public class ChannelConverter implements AttributeConverter<Channel, String> {

    @Override
    public String convertToDatabaseColumn(Channel attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public Channel convertToEntityAttribute(String dbData) {
        return dbData == null ? null : Channel.fromValue(dbData);
    }
}
