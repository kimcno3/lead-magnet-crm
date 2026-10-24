package com.leadmagnet.crm.stats.application;

import com.leadmagnet.crm.common.DateRange;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 지표 집계 네이티브 SQL (ADR 0005). 지표 정의가 SQL에 그대로 보이도록 지표마다 정의를 주석으로 인용한다.
 * "(:x IS NULL OR col >= :x)" 형태의 선택적 기간 조건은 인덱스를 못 탈 수 있으나 이 규모에서는 허용한다.
 * visits에는 campaign_id가 없어 forms를 거친다. 기간 조건은 occurred_at/created_at >= from AND < to(다음날 00:00).
 * 방문은 모든 쿼리의 visits_in에서 NOT is_bot으로 봇 방문을 뺀다(ADR 0005 변경 기록). 신청은 봇 표시가 없어 그대로 센다.
 */
@Component
class StatsQuery {

    record CampaignRow(long campaignId, String name, String status, Metrics metrics) {
    }

    /** creatorId가 null이면 "직접·자체 링크"(크리에이터 없는 링크 + direct) 행이다. */
    record CreatorRow(Long creatorId, String name, String status, Metrics metrics) {
    }

    record CreatorChannelRow(Long creatorId, String channel, Metrics metrics) {
    }

    /**
     * 캠페인별 지표. 캠페인은 데이터가 없어도 포함하고(LEFT JOIN, 없으면 0) 최신순(created_at desc, id desc)이다.
     * :campaignId가 null이면 전체 캠페인, 아니면 그 캠페인만 읽도록 방문·신청 CTE에서부터 거른다.
     * 방문·신청·전환은 campaign_id로 한 번씩 묶어 집계한 뒤 campaigns에 붙인다(캠페인마다 다시 훑지 않는다).
     */
    private static final String BY_CAMPAIGN = """
        WITH visits_in AS (
            SELECT f.campaign_id, v.visitor_id
            FROM visits v JOIN forms f ON f.id = v.form_id
            WHERE (CAST(:campaignId AS bigint) IS NULL OR f.campaign_id = :campaignId)
              -- 봇 방문(is_bot)은 어떤 지표에도 넣지 않는다 (ADR 0005 변경 기록, ADR 0019)
              AND NOT v.is_bot
              AND (CAST(:from AS timestamptz) IS NULL OR v.occurred_at >= :from)
              AND (CAST(:to AS timestamptz) IS NULL OR v.occurred_at < :to)
        ), leads_in AS (
            SELECT l.campaign_id, l.visitor_id
            FROM leads l
            WHERE (CAST(:campaignId AS bigint) IS NULL OR l.campaign_id = :campaignId)
              AND (CAST(:from AS timestamptz) IS NULL OR l.created_at >= :from)
              AND (CAST(:to AS timestamptz) IS NULL OR l.created_at < :to)
        ), visit_stats AS (
            SELECT vi.campaign_id,
                -- 방문 visits = 기간 내 visits 행 수
                COUNT(*) AS visits,
                -- 방문자 visitors = 기간 내 COUNT(DISTINCT visitor_id)
                COUNT(DISTINCT vi.visitor_id) AS visitors
            FROM visits_in vi
            GROUP BY vi.campaign_id
        ), lead_stats AS (
            SELECT li.campaign_id,
                -- 신청 leads = 기간 내 leads 행 수 (visitor_id가 NULL인 신청도 센다)
                COUNT(*) AS leads
            FROM leads_in li
            GROUP BY li.campaign_id
        ), converted_stats AS (
            SELECT vi.campaign_id,
                -- 전환 방문자 convertedVisitors = 기간 내 방문 기록이 있는 visitor_id 중 기간 내 신청한 visitor_id 수(교집합)
                COUNT(DISTINCT vi.visitor_id) AS converted
            FROM visits_in vi
            WHERE EXISTS (SELECT 1 FROM leads_in li
                          WHERE li.campaign_id = vi.campaign_id AND li.visitor_id = vi.visitor_id)
            GROUP BY vi.campaign_id
        )
        SELECT c.id, c.name, c.status,
            COALESCE(vs.visits, 0) AS visits,
            COALESCE(vs.visitors, 0) AS visitors,
            COALESCE(ls.leads, 0) AS leads,
            COALESCE(cs.converted, 0) AS converted
        FROM campaigns c
        LEFT JOIN visit_stats vs ON vs.campaign_id = c.id
        LEFT JOIN lead_stats ls ON ls.campaign_id = c.id
        LEFT JOIN converted_stats cs ON cs.campaign_id = c.id
        WHERE CAST(:campaignId AS bigint) IS NULL OR c.id = :campaignId
        ORDER BY c.created_at DESC, c.id DESC
        """;

    /**
     * 한 캠페인의 채널별 지표. 방문은 visits.channel, 신청은 leads.channel로 센다.
     * 전환은 같은 채널 안의 교집합이다(ADR 0005): 방문과 신청의 channel이 같아야 한다.
     * 데이터가 있는 채널만 돌려주고, 다섯 채널 채우기는 호출한 쪽이 한다.
     */
    private static final String BY_CHANNEL = """
        WITH visits_in AS (
            SELECT v.channel, v.visitor_id
            FROM visits v JOIN forms f ON f.id = v.form_id
            WHERE f.campaign_id = :campaignId
              -- 봇 방문(is_bot)은 어떤 지표에도 넣지 않는다 (ADR 0005 변경 기록, ADR 0019)
              AND NOT v.is_bot
              AND (CAST(:from AS timestamptz) IS NULL OR v.occurred_at >= :from)
              AND (CAST(:to AS timestamptz) IS NULL OR v.occurred_at < :to)
        ), leads_in AS (
            SELECT l.channel, l.visitor_id
            FROM leads l
            WHERE l.campaign_id = :campaignId
              AND (CAST(:from AS timestamptz) IS NULL OR l.created_at >= :from)
              AND (CAST(:to AS timestamptz) IS NULL OR l.created_at < :to)
        ), channels AS (
            SELECT channel FROM visits_in UNION SELECT channel FROM leads_in
        )
        SELECT ch.channel,
            -- 방문 visits = 기간 내 visits 행 수 (visits.channel 기준)
            (SELECT COUNT(*) FROM visits_in vi WHERE vi.channel = ch.channel) AS visits,
            -- 방문자 visitors = 기간 내 COUNT(DISTINCT visitor_id) (visits.channel 기준)
            (SELECT COUNT(DISTINCT vi.visitor_id) FROM visits_in vi WHERE vi.channel = ch.channel) AS visitors,
            -- 신청 leads = 기간 내 leads 행 수 (leads.channel 기준)
            (SELECT COUNT(*) FROM leads_in li WHERE li.channel = ch.channel) AS leads,
            -- 전환 방문자 convertedVisitors = 기간 내 방문 기록이 있는 visitor_id 중 기간 내 신청한 visitor_id 수(교집합),
            -- 같은 채널 안에서만 교집합을 구한다
            (SELECT COUNT(DISTINCT vi.visitor_id) FROM visits_in vi
              WHERE vi.channel = ch.channel
                AND EXISTS (SELECT 1 FROM leads_in li
                            WHERE li.channel = vi.channel AND li.visitor_id = vi.visitor_id)) AS converted
        FROM channels ch
        """;

    /**
     * 한 캠페인의 방문·신청에 크리에이터 키를 붙인다(ADR 0014). 크리에이터는 link_id → distribution_links.creator_id로
     * 유도하고(visits·leads에는 크리에이터 컬럼이 없다), creator_key 0은 크리에이터 없는 링크와 direct(링크 없음)를
     * 묶은 "직접·자체 링크"다. id는 BIGSERIAL(1부터)이라 0과 겹치지 않는다.
     */
    private static final String CREATOR_SCOPE = """
        WITH visits_in AS (
            SELECT COALESCE(dl.creator_id, 0) AS creator_key, v.channel, v.visitor_id
            FROM visits v
            JOIN forms f ON f.id = v.form_id
            LEFT JOIN distribution_links dl ON dl.id = v.link_id
            WHERE f.campaign_id = :campaignId
              -- 봇 방문(is_bot)은 어떤 지표에도 넣지 않는다 (ADR 0005 변경 기록, ADR 0019)
              AND NOT v.is_bot
              AND (CAST(:from AS timestamptz) IS NULL OR v.occurred_at >= :from)
              AND (CAST(:to AS timestamptz) IS NULL OR v.occurred_at < :to)
        ), leads_in AS (
            SELECT COALESCE(dl.creator_id, 0) AS creator_key, l.channel, l.visitor_id
            FROM leads l
            LEFT JOIN distribution_links dl ON dl.id = l.link_id
            WHERE l.campaign_id = :campaignId
              AND (CAST(:from AS timestamptz) IS NULL OR l.created_at >= :from)
              AND (CAST(:to AS timestamptz) IS NULL OR l.created_at < :to)
        )
        """;

    /**
     * 한 캠페인의 크리에이터별 지표. 이 캠페인 폼에 링크가 있는 크리에이터는 데이터가 없어도 0으로 나오고,
     * "직접·자체 링크"(creator_key 0) 행은 항상 나온다. 순서는 크리에이터 id(등록순) 다음 직접·자체 링크.
     * 전환은 같은 크리에이터 안의 교집합이다(ADR 0005 규칙을 크리에이터로 연장): 방문과 신청의 creator_key가 같아야 한다.
     */
    private static final String BY_CREATOR = CREATOR_SCOPE + """
        , creator_keys AS (
            SELECT dl.creator_id AS creator_key
            FROM distribution_links dl JOIN forms f ON f.id = dl.form_id
            WHERE f.campaign_id = :campaignId AND dl.creator_id IS NOT NULL
            UNION SELECT creator_key FROM visits_in
            UNION SELECT creator_key FROM leads_in
            UNION SELECT 0
        ), visit_stats AS (
            SELECT vi.creator_key,
                -- 방문 visits = 기간 내 visits 행 수 (크리에이터별)
                COUNT(*) AS visits,
                -- 방문자 visitors = 기간 내 COUNT(DISTINCT visitor_id) (크리에이터별)
                COUNT(DISTINCT vi.visitor_id) AS visitors
            FROM visits_in vi
            GROUP BY vi.creator_key
        ), lead_stats AS (
            SELECT li.creator_key,
                -- 신청 leads = 기간 내 leads 행 수 (visitor_id가 NULL인 신청도 센다)
                COUNT(*) AS leads
            FROM leads_in li
            GROUP BY li.creator_key
        ), converted_stats AS (
            SELECT vi.creator_key,
                -- 전환 방문자 convertedVisitors = 기간 내 방문 기록이 있는 visitor_id 중 기간 내 신청한 visitor_id 수(교집합),
                -- 같은 캠페인·같은 크리에이터 안에서만 교집합을 구한다(채널은 달라도 된다)
                COUNT(DISTINCT vi.visitor_id) AS converted
            FROM visits_in vi
            WHERE EXISTS (SELECT 1 FROM leads_in li
                          WHERE li.creator_key = vi.creator_key AND li.visitor_id = vi.visitor_id)
            GROUP BY vi.creator_key
        )
        SELECT NULLIF(k.creator_key, 0) AS creator_id, c.name, c.status,
            COALESCE(vs.visits, 0) AS visits,
            COALESCE(vs.visitors, 0) AS visitors,
            COALESCE(ls.leads, 0) AS leads,
            COALESCE(cs.converted, 0) AS converted
        FROM creator_keys k
        LEFT JOIN creators c ON c.id = k.creator_key
        LEFT JOIN visit_stats vs ON vs.creator_key = k.creator_key
        LEFT JOIN lead_stats ls ON ls.creator_key = k.creator_key
        LEFT JOIN converted_stats cs ON cs.creator_key = k.creator_key
        ORDER BY k.creator_key = 0, k.creator_key
        """;

    /**
     * 한 캠페인의 크리에이터 × 채널 지표. 방문은 visits.channel, 신청은 leads.channel로 센다.
     * BY_CREATOR와 같은 방식으로 (creator_key, channel)마다 한 번씩 묶어 집계한 뒤 붙인다.
     * 전환은 같은 크리에이터·같은 채널 안의 교집합이다. 데이터가 있는 조합만 돌려주고 0 채우기는 호출한 쪽이 한다.
     */
    private static final String BY_CREATOR_CHANNEL = CREATOR_SCOPE + """
        , pairs AS (
            SELECT creator_key, channel FROM visits_in
            UNION SELECT creator_key, channel FROM leads_in
        ), visit_stats AS (
            SELECT vi.creator_key, vi.channel,
                -- 방문 visits = 기간 내 visits 행 수 (크리에이터·visits.channel 기준)
                COUNT(*) AS visits,
                -- 방문자 visitors = 기간 내 COUNT(DISTINCT visitor_id) (크리에이터·visits.channel 기준)
                COUNT(DISTINCT vi.visitor_id) AS visitors
            FROM visits_in vi
            GROUP BY vi.creator_key, vi.channel
        ), lead_stats AS (
            SELECT li.creator_key, li.channel,
                -- 신청 leads = 기간 내 leads 행 수 (크리에이터·leads.channel 기준, visitor_id가 NULL인 신청도 센다)
                COUNT(*) AS leads
            FROM leads_in li
            GROUP BY li.creator_key, li.channel
        ), converted_stats AS (
            SELECT vi.creator_key, vi.channel,
                -- 전환 방문자 convertedVisitors = 기간 내 방문 기록이 있는 visitor_id 중 기간 내 신청한 visitor_id 수(교집합),
                -- 같은 캠페인·같은 크리에이터·같은 채널 안에서만 교집합을 구한다
                COUNT(DISTINCT vi.visitor_id) AS converted
            FROM visits_in vi
            WHERE EXISTS (SELECT 1 FROM leads_in li
                          WHERE li.creator_key = vi.creator_key AND li.channel = vi.channel
                            AND li.visitor_id = vi.visitor_id)
            GROUP BY vi.creator_key, vi.channel
        )
        SELECT NULLIF(p.creator_key, 0) AS creator_id, p.channel,
            COALESCE(vs.visits, 0) AS visits,
            COALESCE(vs.visitors, 0) AS visitors,
            COALESCE(ls.leads, 0) AS leads,
            COALESCE(cs.converted, 0) AS converted
        FROM pairs p
        LEFT JOIN visit_stats vs ON vs.creator_key = p.creator_key AND vs.channel = p.channel
        LEFT JOIN lead_stats ls ON ls.creator_key = p.creator_key AND ls.channel = p.channel
        LEFT JOIN converted_stats cs ON cs.creator_key = p.creator_key AND cs.channel = p.channel
        """;

    /**
     * 한 크리에이터의 캠페인별 지표. 그 크리에이터의 링크가 있는 캠페인만, 최신순(created_at desc, id desc)이다.
     * 방문·신청은 그 크리에이터 링크로 귀속된 것만 세고(link_id → creator_id), 전환은 같은 캠페인 안의 교집합이다.
     */
    private static final String CREATOR_BY_CAMPAIGN = """
        WITH visits_in AS (
            SELECT f.campaign_id, v.visitor_id
            FROM visits v
            JOIN distribution_links dl ON dl.id = v.link_id
            JOIN forms f ON f.id = v.form_id
            WHERE dl.creator_id = :creatorId
              -- 봇 방문(is_bot)은 어떤 지표에도 넣지 않는다 (ADR 0005 변경 기록, ADR 0019)
              AND NOT v.is_bot
              AND (CAST(:from AS timestamptz) IS NULL OR v.occurred_at >= :from)
              AND (CAST(:to AS timestamptz) IS NULL OR v.occurred_at < :to)
        ), leads_in AS (
            SELECT l.campaign_id, l.visitor_id
            FROM leads l
            JOIN distribution_links dl ON dl.id = l.link_id
            WHERE dl.creator_id = :creatorId
              AND (CAST(:from AS timestamptz) IS NULL OR l.created_at >= :from)
              AND (CAST(:to AS timestamptz) IS NULL OR l.created_at < :to)
        ), creator_campaigns AS (
            SELECT DISTINCT f.campaign_id
            FROM distribution_links dl JOIN forms f ON f.id = dl.form_id
            WHERE dl.creator_id = :creatorId
        ), visit_stats AS (
            SELECT vi.campaign_id,
                -- 방문 visits = 기간 내 visits 행 수
                COUNT(*) AS visits,
                -- 방문자 visitors = 기간 내 COUNT(DISTINCT visitor_id)
                COUNT(DISTINCT vi.visitor_id) AS visitors
            FROM visits_in vi
            GROUP BY vi.campaign_id
        ), lead_stats AS (
            SELECT li.campaign_id,
                -- 신청 leads = 기간 내 leads 행 수 (visitor_id가 NULL인 신청도 센다)
                COUNT(*) AS leads
            FROM leads_in li
            GROUP BY li.campaign_id
        ), converted_stats AS (
            SELECT vi.campaign_id,
                -- 전환 방문자 convertedVisitors = 기간 내 방문 기록이 있는 visitor_id 중 기간 내 신청한 visitor_id 수(교집합),
                -- 이 크리에이터의 같은 캠페인 안에서만 교집합을 구한다
                COUNT(DISTINCT vi.visitor_id) AS converted
            FROM visits_in vi
            WHERE EXISTS (SELECT 1 FROM leads_in li
                          WHERE li.campaign_id = vi.campaign_id AND li.visitor_id = vi.visitor_id)
            GROUP BY vi.campaign_id
        )
        SELECT c.id, c.name, c.status,
            COALESCE(vs.visits, 0) AS visits,
            COALESCE(vs.visitors, 0) AS visitors,
            COALESCE(ls.leads, 0) AS leads,
            COALESCE(cs.converted, 0) AS converted
        FROM campaigns c
        JOIN creator_campaigns cc ON cc.campaign_id = c.id
        LEFT JOIN visit_stats vs ON vs.campaign_id = c.id
        LEFT JOIN lead_stats ls ON ls.campaign_id = c.id
        LEFT JOIN converted_stats cs ON cs.campaign_id = c.id
        ORDER BY c.created_at DESC, c.id DESC
        """;

    /**
     * 대시보드(전체 캠페인)의 방문·신청 범위. 기간은 항상 양 끝이 있다(서비스가 기본값을 채운다).
     * day는 Asia/Seoul 날짜, creator_key는 CREATOR_SCOPE와 같다(0 = 직접·자체 링크).
     */
    private static final String DASHBOARD_SCOPE = """
        WITH visits_in AS (
            SELECT f.campaign_id, v.form_id, v.channel, COALESCE(dl.creator_id, 0) AS creator_key, v.visitor_id,
                CAST(v.occurred_at AT TIME ZONE 'Asia/Seoul' AS date) AS day
            FROM visits v
            JOIN forms f ON f.id = v.form_id
            LEFT JOIN distribution_links dl ON dl.id = v.link_id
            -- 봇 방문(is_bot)은 어떤 지표에도 넣지 않는다 (ADR 0005 변경 기록, ADR 0019)
            WHERE NOT v.is_bot
              AND v.occurred_at >= :from AND v.occurred_at < :to
        ), leads_in AS (
            SELECT l.campaign_id, l.form_id, l.channel, COALESCE(dl.creator_id, 0) AS creator_key, l.visitor_id,
                CAST(l.created_at AT TIME ZONE 'Asia/Seoul' AS date) AS day
            FROM leads l
            LEFT JOIN distribution_links dl ON dl.id = l.link_id
            WHERE l.created_at >= :from AND l.created_at < :to
        )
        """;

    /**
     * 전체 캠페인 합계 한 행. 방문자는 캠페인을 가리지 않고 COUNT(DISTINCT visitor_id)이고,
     * 전환은 같은 캠페인 안의 교집합(ADR 0005)인 방문자를 캠페인을 가리지 않고 한 번만 센다.
     */
    private static final String DASHBOARD_TOTALS = DASHBOARD_SCOPE + """
        SELECT
            -- 방문 visits = 기간 내 visits 행 수
            (SELECT COUNT(*) FROM visits_in) AS visits,
            -- 방문자 visitors = 기간 내 COUNT(DISTINCT visitor_id)
            (SELECT COUNT(DISTINCT visitor_id) FROM visits_in) AS visitors,
            -- 신청 leads = 기간 내 leads 행 수 (visitor_id가 NULL인 신청도 센다)
            (SELECT COUNT(*) FROM leads_in) AS leads,
            -- 전환 방문자 convertedVisitors = 기간 내 방문 기록이 있는 visitor_id 중 기간 내 신청한 visitor_id 수(교집합),
            -- 교집합은 같은 캠페인 안에서 구하고 여러 캠페인에서 전환한 방문자도 한 번만 센다
            (SELECT COUNT(DISTINCT vi.visitor_id) FROM visits_in vi
              WHERE EXISTS (SELECT 1 FROM leads_in li
                            WHERE li.campaign_id = vi.campaign_id AND li.visitor_id = vi.visitor_id)) AS converted
        """;

    /**
     * 서울 날짜별 지표. 기간의 모든 날짜를 generate_series로 만들어 데이터가 없는 날도 0으로 채운다.
     * 일별 전환 방문자는 "그날(서울 날짜) 방문 기록이 있는 visitor_id 중 같은 날 같은 캠페인에 신청한 visitor_id 수"다.
     * 그래서 일별 전환의 합은 기간 전체의 전환 방문자와 다를 수 있다(방문한 다음 날 신청하면 어느 날에도 잡히지 않는다).
     */
    private static final String DASHBOARD_DAILY = DASHBOARD_SCOPE + """
        , days AS (
            SELECT CAST(d AS date) AS day
            FROM generate_series(CAST(:fromDate AS date), CAST(:toDate AS date), interval '1 day') d
        ), visit_stats AS (
            SELECT vi.day,
                -- 방문 visits = 그날 visits 행 수
                COUNT(*) AS visits,
                -- 방문자 visitors = 그날 COUNT(DISTINCT visitor_id)
                COUNT(DISTINCT vi.visitor_id) AS visitors
            FROM visits_in vi
            GROUP BY vi.day
        ), lead_stats AS (
            SELECT li.day,
                -- 신청 leads = 그날 leads 행 수 (visitor_id가 NULL인 신청도 센다)
                COUNT(*) AS leads
            FROM leads_in li
            GROUP BY li.day
        ), converted_stats AS (
            SELECT vi.day,
                -- 일별 전환 방문자 = 그날 방문한 visitor_id 중 같은 날·같은 캠페인에 신청한 visitor_id 수(교집합)
                COUNT(DISTINCT vi.visitor_id) AS converted
            FROM visits_in vi
            WHERE EXISTS (SELECT 1 FROM leads_in li
                          WHERE li.day = vi.day AND li.campaign_id = vi.campaign_id
                            AND li.visitor_id = vi.visitor_id)
            GROUP BY vi.day
        )
        SELECT d.day,
            COALESCE(vs.visits, 0) AS visits,
            COALESCE(vs.visitors, 0) AS visitors,
            COALESCE(ls.leads, 0) AS leads,
            COALESCE(cs.converted, 0) AS converted
        FROM days d
        LEFT JOIN visit_stats vs ON vs.day = d.day
        LEFT JOIN lead_stats ls ON ls.day = d.day
        LEFT JOIN converted_stats cs ON cs.day = d.day
        ORDER BY d.day
        """;

    /**
     * 폼별 지표. 기간 안에 방문이나 신청이 있는 폼만, 최신 캠페인순(created_at desc, id desc) 다음 폼 id순이다.
     * 전환은 같은 폼 안의 교집합이다: 그 폼의 방문 기록이 있는 visitor_id 중 그 폼으로 신청한 수.
     */
    private static final String DASHBOARD_FORMS = DASHBOARD_SCOPE + """
        , form_keys AS (
            SELECT form_id FROM visits_in UNION SELECT form_id FROM leads_in
        ), visit_stats AS (
            SELECT vi.form_id,
                -- 방문 visits = 기간 내 visits 행 수 (폼별)
                COUNT(*) AS visits,
                -- 방문자 visitors = 기간 내 COUNT(DISTINCT visitor_id) (폼별)
                COUNT(DISTINCT vi.visitor_id) AS visitors
            FROM visits_in vi
            GROUP BY vi.form_id
        ), lead_stats AS (
            SELECT li.form_id,
                -- 신청 leads = 기간 내 leads 행 수 (폼별, visitor_id가 NULL인 신청도 센다)
                COUNT(*) AS leads
            FROM leads_in li
            GROUP BY li.form_id
        ), converted_stats AS (
            SELECT vi.form_id,
                -- 전환 방문자 convertedVisitors = 기간 내 방문 기록이 있는 visitor_id 중 기간 내 신청한 visitor_id 수(교집합),
                -- 같은 폼 안에서만 교집합을 구한다
                COUNT(DISTINCT vi.visitor_id) AS converted
            FROM visits_in vi
            WHERE EXISTS (SELECT 1 FROM leads_in li
                          WHERE li.form_id = vi.form_id AND li.visitor_id = vi.visitor_id)
            GROUP BY vi.form_id
        )
        SELECT c.id AS campaign_id, c.name AS campaign_name, f.id AS form_id, f.title, f.status,
            COALESCE(vs.visits, 0) AS visits,
            COALESCE(vs.visitors, 0) AS visitors,
            COALESCE(ls.leads, 0) AS leads,
            COALESCE(cs.converted, 0) AS converted
        FROM form_keys k
        JOIN forms f ON f.id = k.form_id
        JOIN campaigns c ON c.id = f.campaign_id
        LEFT JOIN visit_stats vs ON vs.form_id = k.form_id
        LEFT JOIN lead_stats ls ON ls.form_id = k.form_id
        LEFT JOIN converted_stats cs ON cs.form_id = k.form_id
        ORDER BY c.created_at DESC, c.id DESC, f.id
        """;

    /**
     * 전체 캠페인의 채널별 지표. 방문은 visits.channel, 신청은 leads.channel로 센다.
     * 전환은 같은 캠페인·같은 채널 안의 교집합이다(BY_CHANNEL을 전체 캠페인으로 넓힌 것). 데이터가 있는 채널만 돌려준다.
     */
    private static final String DASHBOARD_CHANNELS = DASHBOARD_SCOPE + """
        , channels AS (
            SELECT channel FROM visits_in UNION SELECT channel FROM leads_in
        )
        SELECT ch.channel,
            -- 방문 visits = 기간 내 visits 행 수 (visits.channel 기준)
            (SELECT COUNT(*) FROM visits_in vi WHERE vi.channel = ch.channel) AS visits,
            -- 방문자 visitors = 기간 내 COUNT(DISTINCT visitor_id) (visits.channel 기준)
            (SELECT COUNT(DISTINCT vi.visitor_id) FROM visits_in vi WHERE vi.channel = ch.channel) AS visitors,
            -- 신청 leads = 기간 내 leads 행 수 (leads.channel 기준)
            (SELECT COUNT(*) FROM leads_in li WHERE li.channel = ch.channel) AS leads,
            -- 전환 방문자 convertedVisitors = 기간 내 방문 기록이 있는 visitor_id 중 기간 내 신청한 visitor_id 수(교집합),
            -- 같은 캠페인·같은 채널 안에서만 교집합을 구한다
            (SELECT COUNT(DISTINCT vi.visitor_id) FROM visits_in vi
              WHERE vi.channel = ch.channel
                AND EXISTS (SELECT 1 FROM leads_in li
                            WHERE li.campaign_id = vi.campaign_id AND li.channel = vi.channel
                              AND li.visitor_id = vi.visitor_id)) AS converted
        FROM channels ch
        """;

    /**
     * 전체 캠페인의 크리에이터별 지표(ADR 0014). 링크가 있는 크리에이터는 데이터가 없어도 0으로 나오고 "직접·자체 링크"
     * (creator_key 0) 행은 항상 나온다. 순서는 크리에이터 id(등록순) 다음 직접·자체 링크.
     * 전환은 같은 캠페인·같은 크리에이터 안의 교집합이다(BY_CREATOR를 전체 캠페인으로 넓힌 것).
     */
    private static final String DASHBOARD_CREATORS = DASHBOARD_SCOPE + """
        , creator_keys AS (
            SELECT dl.creator_id AS creator_key FROM distribution_links dl WHERE dl.creator_id IS NOT NULL
            UNION SELECT creator_key FROM visits_in
            UNION SELECT creator_key FROM leads_in
            UNION SELECT 0
        ), visit_stats AS (
            SELECT vi.creator_key,
                -- 방문 visits = 기간 내 visits 행 수 (크리에이터별)
                COUNT(*) AS visits,
                -- 방문자 visitors = 기간 내 COUNT(DISTINCT visitor_id) (크리에이터별)
                COUNT(DISTINCT vi.visitor_id) AS visitors
            FROM visits_in vi
            GROUP BY vi.creator_key
        ), lead_stats AS (
            SELECT li.creator_key,
                -- 신청 leads = 기간 내 leads 행 수 (visitor_id가 NULL인 신청도 센다)
                COUNT(*) AS leads
            FROM leads_in li
            GROUP BY li.creator_key
        ), converted_stats AS (
            SELECT vi.creator_key,
                -- 전환 방문자 convertedVisitors = 기간 내 방문 기록이 있는 visitor_id 중 기간 내 신청한 visitor_id 수(교집합),
                -- 같은 캠페인·같은 크리에이터 안에서만 교집합을 구한다(채널은 달라도 된다)
                COUNT(DISTINCT vi.visitor_id) AS converted
            FROM visits_in vi
            WHERE EXISTS (SELECT 1 FROM leads_in li
                          WHERE li.campaign_id = vi.campaign_id AND li.creator_key = vi.creator_key
                            AND li.visitor_id = vi.visitor_id)
            GROUP BY vi.creator_key
        )
        SELECT NULLIF(k.creator_key, 0) AS creator_id, c.name, c.status,
            COALESCE(vs.visits, 0) AS visits,
            COALESCE(vs.visitors, 0) AS visitors,
            COALESCE(ls.leads, 0) AS leads,
            COALESCE(cs.converted, 0) AS converted
        FROM creator_keys k
        LEFT JOIN creators c ON c.id = k.creator_key
        LEFT JOIN visit_stats vs ON vs.creator_key = k.creator_key
        LEFT JOIN lead_stats ls ON ls.creator_key = k.creator_key
        LEFT JOIN converted_stats cs ON cs.creator_key = k.creator_key
        ORDER BY k.creator_key = 0, k.creator_key
        """;

    /**
     * 기간 안의 최근 신청(created_at desc, id desc). 연락처 컬럼(*_enc, *_hash)은 읽지 않는다.
     * 가린 이름은 lead 패키지가 따로 만든다(복호화는 ContactProtector에서만, ADR 0015).
     */
    private static final String RECENT_LEADS = """
        SELECT l.id, l.created_at, l.campaign_id, c.name AS campaign_name, f.title AS form_title, l.channel,
            cr.name AS creator_name
        FROM leads l
        JOIN campaigns c ON c.id = l.campaign_id
        JOIN forms f ON f.id = l.form_id
        LEFT JOIN distribution_links dl ON dl.id = l.link_id
        LEFT JOIN creators cr ON cr.id = dl.creator_id
        WHERE l.created_at >= :from AND l.created_at < :to
        ORDER BY l.created_at DESC, l.id DESC
        LIMIT :limit
        """;

    private final NamedParameterJdbcTemplate jdbc;

    StatsQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** campaignId가 null이면 모든 캠페인, 아니면 그 캠페인 하나(없으면 빈 목록). */
    List<CampaignRow> byCampaign(Long campaignId, DateRange range) {
        MapSqlParameterSource params = rangeParams(range).addValue("campaignId", campaignId, Types.BIGINT);
        return jdbc.query(BY_CAMPAIGN, params, (rs, i) -> new CampaignRow(rs.getLong("id"), rs.getString("name"),
            rs.getString("status"), metrics(rs)));
    }

    /** 채널 값(소문자) → 지표. 데이터가 없는 채널은 들어 있지 않다. */
    Map<String, Metrics> byChannel(long campaignId, DateRange range) {
        MapSqlParameterSource params = rangeParams(range).addValue("campaignId", campaignId, Types.BIGINT);
        Map<String, Metrics> result = new LinkedHashMap<>();
        jdbc.query(BY_CHANNEL, params, rs -> {
            result.put(rs.getString("channel"), metrics(rs));
        });
        return result;
    }

    /** 한 캠페인의 크리에이터별 지표(직접·자체 링크 행 포함). */
    List<CreatorRow> byCreator(long campaignId, DateRange range) {
        MapSqlParameterSource params = rangeParams(range).addValue("campaignId", campaignId, Types.BIGINT);
        return jdbc.query(BY_CREATOR, params, (rs, i) -> new CreatorRow(rs.getObject("creator_id", Long.class),
            rs.getString("name"), rs.getString("status"), metrics(rs)));
    }

    /** 한 캠페인의 크리에이터 × 채널 지표. 데이터가 없는 조합은 들어 있지 않다. */
    List<CreatorChannelRow> byCreatorChannel(long campaignId, DateRange range) {
        MapSqlParameterSource params = rangeParams(range).addValue("campaignId", campaignId, Types.BIGINT);
        return jdbc.query(BY_CREATOR_CHANNEL, params, (rs, i) -> new CreatorChannelRow(
            rs.getObject("creator_id", Long.class), rs.getString("channel"), metrics(rs)));
    }

    /** 한 크리에이터의 캠페인별 지표(링크가 있는 캠페인만). */
    List<CampaignRow> creatorByCampaign(long creatorId, DateRange range) {
        MapSqlParameterSource params = rangeParams(range).addValue("creatorId", creatorId, Types.BIGINT);
        return jdbc.query(CREATOR_BY_CAMPAIGN, params, (rs, i) -> new CampaignRow(rs.getLong("id"),
            rs.getString("name"), rs.getString("status"), metrics(rs)));
    }

    record DailyRow(LocalDate date, Metrics metrics) {
    }

    record FormRow(long campaignId, String campaignName, long formId, String title, String status, Metrics metrics) {
    }

    record RecentLeadRow(long id, Instant createdAt, long campaignId, String campaignName, String formTitle,
            String channel, String creatorName) {
    }

    /** 전체 캠페인 합계. range는 양 끝이 있어야 한다. */
    Metrics dashboardTotals(DateRange range) {
        return jdbc.queryForObject(DASHBOARD_TOTALS, rangeParams(range), (rs, i) -> metrics(rs));
    }

    /** fromDate~toDate(서울 날짜, 양 끝 포함)의 날짜마다 한 행. range와 같은 기간이어야 한다. */
    List<DailyRow> dashboardDaily(DateRange range, LocalDate fromDate, LocalDate toDate) {
        MapSqlParameterSource params = rangeParams(range).addValue("fromDate", fromDate, Types.DATE)
            .addValue("toDate", toDate, Types.DATE);
        return jdbc.query(DASHBOARD_DAILY, params, (rs, i) -> new DailyRow(rs.getObject("day", LocalDate.class),
            metrics(rs)));
    }

    List<FormRow> dashboardForms(DateRange range) {
        return jdbc.query(DASHBOARD_FORMS, rangeParams(range), (rs, i) -> new FormRow(rs.getLong("campaign_id"),
            rs.getString("campaign_name"), rs.getLong("form_id"), rs.getString("title"), rs.getString("status"),
            metrics(rs)));
    }

    /** 채널 값(소문자) → 지표. 데이터가 없는 채널은 들어 있지 않다. */
    Map<String, Metrics> dashboardChannels(DateRange range) {
        Map<String, Metrics> result = new LinkedHashMap<>();
        jdbc.query(DASHBOARD_CHANNELS, rangeParams(range), rs -> {
            result.put(rs.getString("channel"), metrics(rs));
        });
        return result;
    }

    List<CreatorRow> dashboardCreators(DateRange range) {
        return jdbc.query(DASHBOARD_CREATORS, rangeParams(range), (rs, i) -> new CreatorRow(
            rs.getObject("creator_id", Long.class), rs.getString("name"), rs.getString("status"), metrics(rs)));
    }

    List<RecentLeadRow> recentLeads(DateRange range, int limit) {
        MapSqlParameterSource params = rangeParams(range).addValue("limit", limit, Types.INTEGER);
        return jdbc.query(RECENT_LEADS, params, (rs, i) -> new RecentLeadRow(rs.getLong("id"),
            rs.getObject("created_at", OffsetDateTime.class).toInstant(), rs.getLong("campaign_id"),
            rs.getString("campaign_name"), rs.getString("form_title"), rs.getString("channel"),
            rs.getString("creator_name")));
    }

    private static MapSqlParameterSource rangeParams(DateRange range) {
        return new MapSqlParameterSource()
            .addValue("from", utc(range.fromInstant()), Types.TIMESTAMP_WITH_TIMEZONE)
            .addValue("to", utc(range.toExclusiveInstant()), Types.TIMESTAMP_WITH_TIMEZONE);
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private static Metrics metrics(ResultSet rs) throws SQLException {
        return new Metrics(rs.getLong("visits"), rs.getLong("visitors"), rs.getLong("leads"),
            rs.getLong("converted"));
    }
}
