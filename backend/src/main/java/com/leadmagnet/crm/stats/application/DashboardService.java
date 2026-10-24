package com.leadmagnet.crm.stats.application;

import com.leadmagnet.crm.common.Csv;
import com.leadmagnet.crm.common.DateRange;
import com.leadmagnet.crm.common.error.DomainException;
import com.leadmagnet.crm.common.error.DomainException.FieldError;
import com.leadmagnet.crm.lead.application.LeadQueryService;
import com.leadmagnet.crm.link.domain.AttributionChannel;
import com.leadmagnet.crm.stats.application.StatsService.ChannelMetrics;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 유입 현황 대시보드 (ADR 0005). 모든 캠페인에 걸친 합계·일별·폼·채널·크리에이터 지표와 최근 신청.
 * 기간은 Asia/Seoul 날짜이고 양 끝을 포함한다. 둘 다 없으면 서울 오늘까지 {@value #DEFAULT_DAYS}일,
 * to만 없으면 오늘까지, from만 없으면 to에서 {@value #DEFAULT_DAYS}일이다.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    static final int DEFAULT_DAYS = 30;
    static final int RECENT_LEADS = 10;
    /** 일별 행이 날짜마다 하나라 기간을 1년(윤년 366일)까지로 막는다. */
    public static final int MAX_DAYS = 366;
    static final List<String> CSV_HEADER = List.of("캠페인", "폼", "상태", "방문", "방문자", "신청", "전환 방문자", "전환율");

    private final StatsQuery query;
    private final LeadQueryService leads;
    private final Clock clock;

    public DashboardService(StatsQuery query, LeadQueryService leads, Clock clock) {
        this.query = query;
        this.leads = leads;
        this.clock = clock;
    }

    /** 적용된 기간(양 끝 포함). */
    public record Period(LocalDate from, LocalDate to) {
    }

    public record DailyMetrics(LocalDate date, Metrics metrics) {
    }

    public record FormMetrics(long campaignId, String campaignName, long formId, String title, String status,
            Metrics metrics) {
    }

    /** creatorId가 null이면 "직접·자체 링크" 행이고 name·status도 null이다. */
    public record CreatorTotal(Long creatorId, String name, String status, Metrics metrics) {
    }

    /** 최근 신청 한 줄. name은 가린 이름(없거나 파기·복호화 불가면 null)이다. 전화번호·이메일은 없다. */
    public record RecentLead(long id, Instant createdAt, long campaignId, String campaignName, String formTitle,
            String channel, String creatorName, String name) {
    }

    public record Dashboard(Period period, Metrics totals, List<DailyMetrics> daily, List<FormMetrics> forms,
            List<ChannelMetrics> channels, List<CreatorTotal> creators, List<RecentLead> recentLeads) {
    }

    /** 폼 시트 CSV(UTF-8 BOM 포함 문자열)와 행 수. */
    public record FormSheetCsv(Period period, String content, int rows) {
    }

    public Dashboard dashboard(String from, String to) {
        Period period = resolve(from, to);
        DateRange range = range(period);
        Map<String, Metrics> byChannel = query.dashboardChannels(range);
        List<StatsQuery.RecentLeadRow> recent = query.recentLeads(range, RECENT_LEADS);
        Map<Long, String> names = leads.maskedNames(recent.stream().map(StatsQuery.RecentLeadRow::id).toList());
        return new Dashboard(period,
            query.dashboardTotals(range),
            query.dashboardDaily(range, period.from(), period.to()).stream()
                .map(r -> new DailyMetrics(r.date(), r.metrics())).toList(),
            forms(range),
            Arrays.stream(AttributionChannel.values())
                .map(c -> new ChannelMetrics(c, byChannel.getOrDefault(c.value(), Metrics.ZERO))).toList(),
            query.dashboardCreators(range).stream()
                .map(r -> new CreatorTotal(r.creatorId(), r.name(), r.status(), r.metrics())).toList(),
            recent.stream().map(r -> new RecentLead(r.id(), r.createdAt(), r.campaignId(), r.campaignName(),
                r.formTitle(), r.channel(), r.creatorName(), names.get(r.id()))).toList());
    }

    /** 폼 시트만 CSV로. 개인정보가 없는 집계 값뿐이다. 칸은 {@link Csv}(수식 시작 문자 무력화)로 만든다. */
    public FormSheetCsv formSheetCsv(String from, String to) {
        Period period = resolve(from, to);
        List<FormMetrics> rows = forms(range(period));
        StringBuilder csv = new StringBuilder("﻿").append(Csv.line(CSV_HEADER));
        for (FormMetrics r : rows) {
            Metrics m = r.metrics();
            csv.append(Csv.line(List.of(r.campaignName(), r.title(), r.status(), String.valueOf(m.visits()),
                String.valueOf(m.visitors()), String.valueOf(m.leads()), String.valueOf(m.convertedVisitors()),
                m.conversionRate().toPlainString())));
        }
        return new FormSheetCsv(period, csv.toString(), rows.size());
    }

    private List<FormMetrics> forms(DateRange range) {
        return query.dashboardForms(range).stream().map(r -> new FormMetrics(r.campaignId(), r.campaignName(),
            r.formId(), r.title(), r.status(), r.metrics())).toList();
    }

    /**
     * 형식·범위를 먼저 검증(422)한 뒤 빈 쪽을 채우고, 채운 기간을 다시 검증한다(from만 미래로 주면 422).
     * 채운 기간이 {@value #MAX_DAYS}일을 넘으면 422 validation_failed(errors[].code range_too_long).
     */
    Period resolve(String from, String to) {
        DateRange.parse(from, to);
        LocalDate toDate = to == null ? LocalDate.ofInstant(clock.instant(), DateRange.ZONE) : LocalDate.parse(to);
        LocalDate fromDate = from == null ? toDate.minusDays(DEFAULT_DAYS - 1) : LocalDate.parse(from);
        DateRange.parse(fromDate.toString(), toDate.toString());
        if (ChronoUnit.DAYS.between(fromDate, toDate) + 1 > MAX_DAYS) {
            throw DomainException.validation(List.of(new FieldError("from",
                "대시보드 기간은 " + MAX_DAYS + "일을 넘을 수 없습니다", "range_too_long")));
        }
        return new Period(fromDate, toDate);
    }

    private static DateRange range(Period period) {
        return DateRange.parse(period.from().toString(), period.to().toString());
    }
}
