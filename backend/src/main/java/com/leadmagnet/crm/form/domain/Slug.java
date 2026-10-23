package com.leadmagnet.crm.form.domain;

import com.leadmagnet.crm.common.error.DomainException;
import com.leadmagnet.crm.common.error.ErrorCode;
import java.security.SecureRandom;
import java.util.regex.Pattern;

/** 공개 폼 주소에 쓰는 slug. DB의 ck_forms_slug와 같은 규칙이다. */
public record Slug(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{1,62}[a-z0-9]$");
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final int RANDOM_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    public Slug {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw DomainException.validation("slug",
                "slug는 소문자, 숫자, 하이픈으로 3~64자여야 하고 하이픈으로 시작하거나 끝날 수 없습니다");
        }
    }

    public static Slug random() {
        StringBuilder sb = new StringBuilder(RANDOM_LENGTH);
        for (int i = 0; i < RANDOM_LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return new Slug(sb.toString());
    }
}
