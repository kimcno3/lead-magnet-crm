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
     * 캠페인별 지표. 캠페인은 데이터가 없어도 포함하고 최신순(created_at desc, id desc)이다.
     * :campaignId가 null이면 전체 캠페인.
     */
    private static final String BY_CAMPAIGN = """
        WITH visits_in AS (
            SELECT f.campaign_id, v.visitor_id
            FROM visits v JOIN forms f ON f.id = v.form_id
            WHERE (CAST(:from AS timestamptz) IS NULL OR v.occurred_at >= :from)
              AND (CAST(:to AS timestamptz) IS NULL OR v.occurred_at < :to)
        ), leads_in AS (
            SELECT l.campaign_id, l.visitor_id
            FROM leads l
            WHERE (CAST(:from AS timestamptz) IS NULL OR l.created_at >= :from)
              AND (CAST(:to AS timestamptz) IS NULL OR l.created_at < :to)
        )
        SELECT c.id, c.name, c.status,
            -- 방문 visits = 기간 내 visits 행 수
            (SELECT COUNT(*) FROM visits_in vi WHERE vi.campaign_id = c.id) AS visits,
            -- 방문자 visitors = 기간 내 COUNT(DISTINCT visitor_id)
            (SELECT COUNT(DISTINCT vi.visitor_id) FROM visits_in vi WHERE vi.campaign_id = c.id) AS visitors,
            -- 신청 leads = 기간 내 leads 행 수 (visitor_id가 NULL인 신청도 센다)
            (SELECT COUNT(*) FROM leads_in li WHERE li.campaign_id = c.id) AS leads,
            -- 전환 방문자 convertedVisitors = 기간 내 방문 기록이 있는 visitor_id 중 기간 내 신청한 visitor_id 수(교집합)
            (SELECT COUNT(DISTINCT vi.visitor_id) FROM visits_in vi
              WHERE vi.campaign_id = c.id
                AND EXISTS (SELECT 1 FROM leads_in li
                            WHERE li.campaign_id = vi.campaign_id AND li.visitor_id = vi.visitor_id)) AS converted
        FROM campaigns c
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
