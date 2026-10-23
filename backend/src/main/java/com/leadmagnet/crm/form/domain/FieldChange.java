package com.leadmagnet.crm.form.domain;

/** fieldConfig 수정 요청의 필드 하나. null인 label·required는 현재 값을 유지한다. */
public record FieldChange(String name, String label, Boolean required) {
}
