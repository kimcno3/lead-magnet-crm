package com.leadmagnet.crm.link.domain;

import com.leadmagnet.crm.common.error.DomainException;
import java.util.List;
import java.util.Locale;

/**
 * 배포 링크를 만들 수 있는 채널. 선언 순서가 목록 정렬 순서다.
 * 방문·신청 귀속에는 링크 없이 들어온 경우를 뜻하는 direct가 더해지지만(ADR 0006), direct는 링크 채널이 아니므로 여기에 두지 않는다.
 */
public enum Channel {
    INSTAGRAM, X, YOUTUBE, THREADS;

    public static final String DIRECT = "direct";

    /** API와 DB에 쓰는 소문자 값. */
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Channel fromValue(String value) {
        for (Channel c : values()) {
            if (c.value().equals(value)) {
                return c;
            }
        }
        throw new IllegalArgumentException("알 수 없는 채널: " + value);
    }

    /** 요청값 파싱. 비어 있거나 모르는 값이거나 direct면 422. */
    public static Channel parse(String value) {
        if (value == null || value.isBlank()) {
            throw DomainException.validation("channel", "channel이 필요합니다");
        }
        String normalized = value.trim();
        for (Channel c : values()) {
            if (c.value().equals(normalized)) {
                return c;
            }
        }
        List<String> allowed = java.util.Arrays.stream(values()).map(Channel::value).toList();
        String reason = DIRECT.equals(normalized) ? "direct는 링크로 만들 수 없습니다. " : "";
        throw DomainException.validation("channel", reason + "channel은 " + allowed + " 중 하나여야 합니다");
    }
}
