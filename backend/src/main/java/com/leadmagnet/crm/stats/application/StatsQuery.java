package com.leadmagnet.crm.stats.application;

import com.leadmagnet.crm.stats.domain.DateRange;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
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
 */
@Component
class StatsQuery {

    record CampaignRow(long campaignId, String name, String status, Metrics metrics) {
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
