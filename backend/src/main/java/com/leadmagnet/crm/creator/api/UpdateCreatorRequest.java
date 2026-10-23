package com.leadmagnet.crm.creator.api;

/** 없는(null) 필드는 바꾸지 않는다. handle·memo에 빈 문자열을 주면 지운다. status는 "archived"만 받는다. */
public record UpdateCreatorRequest(String name, String handle, String memo, String status) {
}
