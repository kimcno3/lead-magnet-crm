package com.leadmagnet.crm.common;

import java.util.List;
import java.util.stream.Collectors;

/**
 * RFC 4180 CSV 한 줄 만들기 (ADR 0016). 줄 끝은 CRLF다.
 * 스프레드시트가 수식으로 해석하는 값(=, +, -, @, 탭, CR로 시작)에는 작은따옴표를 앞에 붙여 글자로 보이게 한다(CSV 수식 주입 방지).
 */
public final class Csv {

    private Csv() {
    }

    /** 칸들을 쉼표로 이은 한 줄. null 칸은 빈 칸이다. */
    public static String line(List<String> cells) {
        return cells.stream().map(Csv::cell).collect(Collectors.joining(",", "", "\r\n"));
    }

    /** 한 칸. 수식 시작 문자를 무력화한 뒤 쉼표·따옴표·줄바꿈이 있으면 따옴표로 감싼다. */
    public static String cell(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String safe = startsLikeFormula(value) ? "'" + value : value;
        if (safe.indexOf(',') >= 0 || safe.indexOf('"') >= 0 || safe.indexOf('\n') >= 0 || safe.indexOf('\r') >= 0) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    private static boolean startsLikeFormula(String value) {
        char first = value.charAt(0);
        return first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r';
    }
}
