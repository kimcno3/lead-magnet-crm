package com.leadmagnet.crm.template.domain;

import java.util.List;

/** 템플릿 form에서 추출한 필드 하나. radio·checkbox는 같은 name을 하나로 묶고 options에 value를 모은다. */
public record FormField(String name, String type, boolean required, String label, List<String> options) {
}
