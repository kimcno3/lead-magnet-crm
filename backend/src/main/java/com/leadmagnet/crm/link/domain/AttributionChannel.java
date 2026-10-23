package com.leadmagnet.crm.link.domain;

import java.util.Locale;

/** 방문·신청의 귀속 채널. 링크 채널 네 개에 링크 없이 들어온 direct가 더해진다 (DB CHECK와 같은 값). */
public enum AttributionChannel {
    INSTAGRAM, X, YOUTUBE, THREADS, DIRECT;

    /** API와 DB에 쓰는 소문자 값. */
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static AttributionChannel of(Channel channel) {
        return valueOf(channel.name());
    }

    public static AttributionChannel direct() {
        return DIRECT;
    }

    public static AttributionChannel fromValue(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
