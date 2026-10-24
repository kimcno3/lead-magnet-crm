package com.leadmagnet.crm.demo;

import java.util.List;
import java.util.Map;

/**
 * 예시 데이터의 설계도 (ADR 0023). GLOW UP RIZZ의 사업 라인(아카데미·MCN·커머스 요고·F&B)을 본뜬 캠페인 네 개와 폼 다섯 개,
 * 가상의 크리에이터 다섯 명(한 명은 보관), 링크별 방문자 수와 신청 수를 담는다. 사람 이름·핸들은 모두 지어낸 것이다.
 * 신청 수는 방문자 중 몇 명이 신청했는지를 정한다(전환율 대략 8~25%). 날짜는 "오늘로부터 며칠 전"(Asia/Seoul)이다.
 */
final class DemoCatalog {

    /** 이 기간(일)보다 오래된 방문은 만들지 않는다. 게시 시각은 이보다 하루 앞이다. */
    static final int PERIOD_DAYS = 21;

    private DemoCatalog() {
    }

    enum FormState { DRAFT, PUBLISHED, CLOSED }

    record TemplateSpec(String key, String file, String name) {
    }

    record CreatorSpec(String key, String name, String handle, String memo, boolean archived) {
    }

    /** archived면 폼을 마감한 뒤 보관한다(행사가 끝난 캠페인). 목록은 이 순서대로 위에서 아래로 보인다. */
    record CampaignSpec(String key, String name, String description, Integer retentionDays, Integer startsDaysAgo,
            Integer endsDaysAgo, boolean archived) {
    }

    /**
     * channel이 null이면 링크 없이 들어온 방문(direct)이다. creator가 null이면 기본(자체) 링크다. 방문은
     * [fromDaysAgo, toDaysAgo]일 전 사이에 흩어진다.
     */
    record TrafficSpec(String channel, String creator, int visitors, int leads, int fromDaysAgo, int toDaysAgo) {

        static TrafficSpec link(String channel, int visitors, int leads) {
            return new TrafficSpec(channel, null, visitors, leads, PERIOD_DAYS, 0);
        }

        static TrafficSpec creator(String creator, String channel, int visitors, int leads) {
            return new TrafficSpec(channel, creator, visitors, leads, PERIOD_DAYS, 0);
        }

        static TrafficSpec direct(int visitors, int leads) {
            return new TrafficSpec(null, null, visitors, leads, PERIOD_DAYS, 0);
        }

        TrafficSpec between(int from, int to) {
            return new TrafficSpec(channel, creator, visitors, leads, from, to);
        }
    }

    /** labels: 템플릿에서 뽑은 라벨 대신 쓸 라벨(필드 이름 → 라벨). closedDaysAgo는 CLOSED일 때만 쓴다. */
    record FormSpec(String key, String campaign, String template, String slug, String title, String consentText,
            FormState state, Map<String, String> labels, int closedDaysAgo, List<TrafficSpec> traffic) {
    }

    static final List<String> CHANNELS = List.of("instagram", "x", "youtube", "threads");

    static final List<TemplateSpec> TEMPLATES = List.of(
        new TemplateSpec("academy", "academy-live-class.html", "아카데미 · 무료 라이브 클래스 신청"),
        new TemplateSpec("mcn", "mcn-partner.html", "MCN · 크리에이터 파트너 상담"),
        new TemplateSpec("yogo", "yogo-brand-inquiry.html", "요고 · 브랜드 입점·공동구매 문의"),
        new TemplateSpec("fnb", "seongsu-tasting.html", "F&B · 성수 매장 시식회 신청"));

    static final List<CreatorSpec> CREATORS = List.of(
        new CreatorSpec("daon", "다온핏", "@daon.fit", "피트니스 · 10분 홈트 숏폼, 인스타·유튜브 중심", false),
        new CreatorSpec("rhythm", "리듬서랍", "@rhythm_drawer", "음악 · 어쿠스틱 커버와 라이브, 유튜브 롱폼", false),
        new CreatorSpec("haesol", "해솔쌤", "@haesol.study", "교육 · 공부법·유튜브 입문 강의, 유튜브 중심", false),
        new CreatorSpec("yuna", "옷장정리 유나", "@yuna.closet", "스타일링 · 데일리 코디, 인스타·스레드", false),
        new CreatorSpec("doyun", "주말산책 도윤", "@doyun.weekend", "라이프스타일 · 지난 시즌 협업 종료(보관)", true));

    static final List<CampaignSpec> CAMPAIGNS = List.of(
        new CampaignSpec("academy", "아카데미 · 유튜브 첫 1,000명 로드맵",
            "다음 세대 크리에이터를 위한 실전 유튜브 무료 라이브 클래스 모집. 해솔쌤·옷장정리 유나 채널과 함께 홍보한다.",
            null, null, null, false),
        new CampaignSpec("mcn", "MCN · 크리에이터 파트너 모집",
            "R/S 기반 맞춤 프로듀싱 파트너 상담 신청. 소속 크리에이터 추천 링크로 들어온 상담을 따로 본다.",
            null, null, null, false),
        new CampaignSpec("yogo", "요고 · 브랜드 입점·공동구매",
            "요고 플랫폼 브랜드 입점과 크리에이터 공동구매 협업 문의. 브랜드 담당자 연락처라 6개월만 보관한다.",
            180, null, null, false),
        new CampaignSpec("fnb", "F&B · 성수 매장 오픈 시식회",
            "성수 신규 매장 오픈 전 시식회 사전 신청. 정원이 차서 마감했고 행사가 끝나 보관했다.",
            null, PERIOD_DAYS + 3, 4, true));

    static final List<FormSpec> FORMS = List.of(
        new FormSpec("academy", "academy", "academy", "academy-first-1000", "유튜브 첫 1,000명 구독자 로드맵 무료 라이브",
            "무료 라이브 클래스 입장 안내와 아카데미 상담을 위해 이름·휴대폰 번호·이메일과 채널 정보를 수집하며, 신청일로부터 1년간 보관합니다.",
            FormState.PUBLISHED, Map.of("interests", "가장 듣고 싶은 내용"), 0, List.of(
                TrafficSpec.link("instagram", 60, 7),
                TrafficSpec.link("x", 15, 1),
                TrafficSpec.link("youtube", 45, 7),
                TrafficSpec.link("threads", 20, 2),
                TrafficSpec.creator("haesol", "youtube", 70, 15),
                TrafficSpec.creator("haesol", "instagram", 30, 4),
                TrafficSpec.creator("yuna", "instagram", 35, 4),
                TrafficSpec.creator("yuna", "threads", 15, 2),
                TrafficSpec.creator("rhythm", "youtube", 25, 2),
                TrafficSpec.direct(25, 3))),
        new FormSpec("mcn", "mcn", "mcn", "mcn-partner", "크리에이터 매니지먼트 파트너 상담 신청",
            "파트너 상담 연락을 위해 이름(활동명)·휴대폰 번호·이메일과 채널 정보를 수집하며, 신청일로부터 1년간 보관합니다.",
            FormState.PUBLISHED, Map.of(), 0, List.of(
                TrafficSpec.link("instagram", 30, 3),
                TrafficSpec.link("x", 8, 1),
                TrafficSpec.link("youtube", 20, 2),
                TrafficSpec.link("threads", 12, 1),
                TrafficSpec.creator("daon", "instagram", 40, 8),
                TrafficSpec.creator("daon", "youtube", 25, 4),
                TrafficSpec.creator("rhythm", "instagram", 15, 1),
                TrafficSpec.creator("rhythm", "youtube", 20, 2),
                TrafficSpec.creator("doyun", "instagram", 20, 3).between(PERIOD_DAYS, 12),
                TrafficSpec.direct(12, 2))),
        new FormSpec("yogo", "yogo", "yogo", "yogo-brand", "요고 브랜드 입점·공동구매 협업 문의",
            "협업 제안과 회신을 위해 회사명·담당자 이름·연락처·업무 이메일을 수집하며, 문의일로부터 6개월간 보관합니다.",
            FormState.PUBLISHED, Map.of(), 0, List.of(
                TrafficSpec.link("instagram", 20, 2),
                TrafficSpec.link("x", 10, 1),
                TrafficSpec.link("youtube", 12, 1),
                TrafficSpec.link("threads", 10, 1),
                TrafficSpec.direct(15, 3))),
        new FormSpec("yogo-draft", "yogo", "yogo", "yogo-season-proposal", "요고 시즌 공동구매 제안서 요청 (준비 중)",
            "제안서 발송을 위해 회사명·담당자 이름·연락처·업무 이메일을 수집하며, 요청일로부터 6개월간 보관합니다.",
            FormState.DRAFT, Map.of(), 0, List.of()),
        new FormSpec("fnb", "fnb", "fnb", "seongsu-tasting", "성수 신규 매장 오픈 시식회 사전 신청",
            "시식회 선정 안내를 위해 이름·휴대폰 번호를 수집하며, 행사 종료 후 1년간 보관합니다.",
            FormState.CLOSED, Map.of(), 4, List.of(
                TrafficSpec.link("instagram", 40, 8).between(PERIOD_DAYS, 5),
                TrafficSpec.link("x", 6, 0).between(PERIOD_DAYS, 5),
                TrafficSpec.link("youtube", 10, 1).between(PERIOD_DAYS, 5),
                TrafficSpec.link("threads", 20, 3).between(PERIOD_DAYS, 5),
                TrafficSpec.direct(10, 1).between(PERIOD_DAYS, 5))));

    static List<String> templateFiles() {
        return TEMPLATES.stream().map(TemplateSpec::file).toList();
    }
}
