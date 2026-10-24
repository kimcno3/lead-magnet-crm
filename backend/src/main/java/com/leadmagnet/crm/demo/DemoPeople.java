package com.leadmagnet.crm.demo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 가상의 신청자와 폼 입력값 (ADR 0023). 이름은 흔한 성과 이름을 섞어 만들고, 전화번호는 실제로 쓰지 않는 010-5555-xxxx 블록,
 * 이메일은 example.com만 쓴다. 번호·이메일에 일련번호를 넣어 한 폼 안에서 중복(ADR 0007)이 생기지 않는다.
 * 같은 시드면 같은 사람들이 나온다.
 */
final class DemoPeople {

    private static final String[][] SURNAMES = {
        {"김", "kim"}, {"이", "lee"}, {"박", "park"}, {"최", "choi"}, {"정", "jung"}, {"강", "kang"}, {"조", "cho"},
        {"윤", "yoon"}, {"장", "jang"}, {"임", "lim"}, {"한", "han"}, {"오", "oh"}, {"서", "seo"}, {"신", "shin"},
        {"권", "kwon"}, {"황", "hwang"}, {"안", "ahn"}, {"송", "song"}, {"류", "ryu"}, {"홍", "hong"}};
    private static final String[][] GIVEN = {
        {"서연", "seoyeon"}, {"민준", "minjun"}, {"지우", "jiwoo"}, {"하은", "haeun"}, {"도현", "dohyun"},
        {"수아", "sua"}, {"예준", "yejun"}, {"지민", "jimin"}, {"서준", "seojun"}, {"채원", "chaewon"},
        {"현우", "hyunwoo"}, {"다은", "daeun"}, {"건우", "gunwoo"}, {"소율", "soyul"}, {"유진", "yujin"},
        {"시우", "siwoo"}, {"나연", "nayeon"}, {"태윤", "taeyoon"}, {"윤서", "yunseo"}, {"준호", "junho"},
        {"가은", "gaeun"}, {"민서", "minseo"}, {"재원", "jaewon"}, {"하린", "harin"}, {"승민", "seungmin"}};
    private static final String[] BRANDS = {"라온뷰티", "포근리빙", "데일리핏랩", "숲결", "모노웨어", "한입담", "블루밍데이",
        "오늘곡물", "결코스메틱", "무드랩", "소소상점", "바른식탁", "온새미로", "피카랩", "느린아침"};
    private static final String[] ACADEMY_NOTES = {"쇼츠와 롱폼 중 무엇부터 시작해야 할지 궁금해요.",
        "직장 다니면서 주 1회 업로드가 가능할까요?", "썸네일 문구 짜는 법을 알고 싶어요.",
        "편집 프로그램 추천도 해 주시면 좋겠어요.", "구독자 300명에서 정체 중입니다."};
    private static final String[] MCN_NOTES = {"브랜드 협업 제안이 늘어서 관리가 필요합니다.",
        "롱폼 기획을 같이 해 줄 프로듀서를 찾고 있어요.", "수익 배분 구조가 궁금합니다.",
        "촬영 장비와 스튜디오 지원이 되는지 알고 싶어요."};
    private static final String[] YOGO_NOTES = {"신제품 런칭에 맞춰 공동구매를 진행하고 싶습니다.",
        "뷰티 크리에이터 3~5명과 숏폼 캠페인을 원합니다.", "요고 입점 수수료와 정산 주기가 궁금합니다.",
        "추석 시즌 기획전 참여를 검토하고 있어요."};

    private final Random random;
    private int sequence;

    DemoPeople(Random random) {
        this.random = random;
    }

    /** 템플릿 key(DemoCatalog.TemplateSpec.key)에 맞는 폼 입력값. 연락처는 실제 제출처럼 하이픈을 넣은 문자열이다. */
    Map<String, Object> answers(String template) {
        sequence++;
        String[] surname = pick(SURNAMES);
        String[] given = pick(GIVEN);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("name", surname[0] + given[0]);
        fields.put("phone", String.format("010-5555-%04d", 1000 + sequence));
        String email = given[1] + "." + surname[1].charAt(0) + sequence + "@example.com";
        switch (template) {
            case "academy" -> {
                if (random.nextInt(10) < 7) {
                    fields.put("email", email);
                }
                if (random.nextInt(10) < 6) {
                    fields.put("channel_url", "https://www.youtube.com/@" + given[1] + sequence);
                }
                fields.put("channel_stage", weighted(new String[] {"아직 시작 전", "구독자 1천 미만", "1천~1만", "1만 이상"},
                    new int[] {4, 4, 2, 1}));
                fields.put("interests", some(List.of("기획", "촬영·편집", "썸네일·제목", "수익화")));
                if (random.nextInt(10) < 3) {
                    fields.put("message", pick(ACADEMY_NOTES));
                }
            }
            case "mcn" -> {
                fields.put("email", email);
                String platform = weighted(new String[] {"유튜브", "인스타그램", "틱톡", "스레드"}, new int[] {4, 4, 2, 1});
                fields.put("platform", platform);
                fields.put("channel_url", switch (platform) {
                    case "유튜브" -> "https://www.youtube.com/@";
                    case "인스타그램" -> "https://www.instagram.com/";
                    case "틱톡" -> "https://www.tiktok.com/@";
                    default -> "https://www.threads.net/@";
                } + given[1] + "_" + sequence);
                fields.put("followers", weighted(new String[] {"1만 미만", "1만~5만", "5만~20만", "20만 이상"},
                    new int[] {3, 4, 2, 1}));
                fields.put("category", pick(new String[] {"피트니스", "뷰티·패션", "음악", "교육", "라이프스타일"}));
                if (random.nextInt(10) < 5) {
                    fields.put("message", pick(MCN_NOTES));
                }
            }
            case "yogo" -> {
                fields.put("company", pick(BRANDS));
                fields.put("email", "partner" + sequence + "@example.com");
                fields.put("category", pick(new String[] {"뷰티", "패션·잡화", "식품·건강", "리빙", "기타"}));
                fields.put("monthly_budget", weighted(new String[] {"500만 원 미만", "500만~2,000만 원",
                    "2,000만~5,000만 원", "5,000만 원 이상"}, new int[] {3, 4, 2, 1}));
                if (random.nextInt(10) < 6) {
                    fields.put("message", pick(YOGO_NOTES));
                }
            }
            case "fnb" -> fields.put("party_size", weighted(new String[] {"1명", "2명", "3~4명"}, new int[] {2, 5, 2}));
            default -> throw new IllegalArgumentException("모르는 예시 템플릿: " + template);
        }
        return fields;
    }

    private String[] pick(String[][] values) {
        return values[random.nextInt(values.length)];
    }

    private String pick(String[] values) {
        return values[random.nextInt(values.length)];
    }

    private String weighted(String[] values, int[] weights) {
        int total = 0;
        for (int w : weights) {
            total += w;
        }
        int r = random.nextInt(total);
        for (int i = 0; i < values.length; i++) {
            r -= weights[i];
            if (r < 0) {
                return values[i];
            }
        }
        return values[values.length - 1];
    }

    private List<String> some(List<String> options) {
        List<String> chosen = new ArrayList<>();
        for (String option : options) {
            if (random.nextInt(10) < 4) {
                chosen.add(option);
            }
        }
        if (chosen.isEmpty()) {
            chosen.add(options.get(random.nextInt(options.size())));
        }
        return chosen;
    }
}
