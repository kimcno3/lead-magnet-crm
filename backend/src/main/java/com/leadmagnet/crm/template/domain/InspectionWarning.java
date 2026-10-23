package com.leadmagnet.crm.template.domain;

/** 업로드 HTML 검사 경고 하나. 계약 오류(422)와 달리 업로드를 막지 않는다. location은 문제가 있는 곳을 짧게 가리킨다. */
public record InspectionWarning(String code, Severity severity, String message, String location) {
}
