package com.leadmagnet.crm.link.domain;

import java.security.SecureRandom;
import java.util.regex.Pattern;

/** 배포 링크의 짧은 코드. base62(0-9A-Za-z) 8자. */
public final class LinkCode {

    public static final int LENGTH = 8;
    public static final Pattern FORMAT = Pattern.compile("[0-9A-Za-z]{8}");
    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final SecureRandom RANDOM = new SecureRandom();

    private LinkCode() {
    }

    public static String generate() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
