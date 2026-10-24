package com.leadmagnet.crm.demo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadmagnet.crm.audit.domain.AuditActor;
import com.leadmagnet.crm.auth.domain.Operator;
import com.leadmagnet.crm.auth.domain.OperatorRepository;
import com.leadmagnet.crm.campaign.application.CampaignService;
import com.leadmagnet.crm.campaign.domain.Campaign;
import com.leadmagnet.crm.campaign.domain.CampaignRepository;
import com.leadmagnet.crm.creator.application.CreatorService;
import com.leadmagnet.crm.creator.domain.Creator;
import com.leadmagnet.crm.demo.DemoCatalog.CampaignSpec;
import com.leadmagnet.crm.demo.DemoCatalog.CreatorSpec;
import com.leadmagnet.crm.demo.DemoCatalog.FormSpec;
import com.leadmagnet.crm.demo.DemoCatalog.FormState;
import com.leadmagnet.crm.demo.DemoCatalog.TemplateSpec;
import com.leadmagnet.crm.demo.DemoCatalog.TrafficSpec;
import com.leadmagnet.crm.form.application.FormService;
import com.leadmagnet.crm.form.domain.FieldChange;
import com.leadmagnet.crm.form.domain.Form;
import com.leadmagnet.crm.lead.application.ContactProtector;
import com.leadmagnet.crm.lead.domain.ConsentEvidence;
import com.leadmagnet.crm.lead.domain.Lead;
import com.leadmagnet.crm.lead.domain.LeadRepository;
import com.leadmagnet.crm.lead.domain.Submission;
import com.leadmagnet.crm.lead.domain.SubmissionValidator;
import com.leadmagnet.crm.link.application.LinkService;
import com.leadmagnet.crm.link.domain.Attribution;
import com.leadmagnet.crm.link.domain.DistributionLink;
import com.leadmagnet.crm.template.application.TemplateService;
import com.leadmagnet.crm.template.domain.HtmlTemplate;
import com.leadmagnet.crm.tracking.domain.BotUserAgents;
import com.leadmagnet.crm.tracking.domain.Visit;
import com.leadmagnet.crm.tracking.domain.VisitRepository;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 로컬 체험용 예시 데이터를 넣는다 (ADR 0023). {@code app.demo.enabled}(DEMO_DATA)가 true이고, 운영자가 있고,
 * 캠페인이 하나도 없을 때만 한 번 넣는다. 그래서 두 번째 기동은 아무것도 넣지 않는다.
 * <ul>
 *   <li>템플릿 업로드·캠페인·폼 생성과 게시·마감·크리에이터 등록과 보관·링크 생성은 도메인 서비스로 한다(검사, 게시 스냅샷,
 *       잠금 순서, 링크 하나 규칙이 그대로 지켜진다). 게시·마감·보관 감사 기록의 주체는 {@link #ACTOR}다(운영자를 사칭하지 않는다).</li>
 *   <li>방문·신청은 지난 {@value DemoCatalog#PERIOD_DAYS}일에 흩어 놓아야 해서 리포지토리로 직접 넣는다. 신청 입력은 실제 제출과 같은
 *       {@link SubmissionValidator}로 검증·정규화하고 연락처는 {@link ContactProtector}로 암호화·해시해, 원문은 DB에도 로그에도 남지 않는다.</li>
 *   <li>캠페인·폼·링크·크리에이터의 생성·게시 시각은 방문보다 앞이 되도록 마지막에 JDBC로 옮긴다. 감사 기록 시각은 옮기지 않는다.</li>
 * </ul>
 * 로그는 건수만 한 줄 남긴다. 중간에 실패하면 이미 만든 캠페인 때문에 다음 기동에서도 건너뛰므로 {@code --fresh}로 다시 시작한다.
 */
@Component
public class DemoDataSeeder {

    /** 시드가 남기는 감사 기록의 주체. 운영자 id·IP가 없고 User-Agent로 시드임을 밝힌다. */
    public static final AuditActor ACTOR = new AuditActor(null, null, "lead-magnet-crm demo seeder (DEMO_DATA)");

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final long RANDOM_SEED = 95L;
    /** 낮보다 저녁에 몰리는 방문 시각(시). */
    private static final int[] HOURS = {8, 9, 10, 11, 12, 12, 13, 14, 15, 16, 18, 19, 20, 20, 21, 21, 22, 22, 23, 0};
    private static final Map<String, String> REFERRERS = Map.of("instagram", "https://l.instagram.com/",
        "x", "https://t.co/", "youtube", "https://www.youtube.com/", "threads", "https://www.threads.net/");
    private static final List<String> BROWSERS = List.of(
        "Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 Instagram 390.0.0",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.5 Mobile/15E148 Safari/604.1",
        "Mozilla/5.0 (Linux; Android 15; SM-S928N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36");
    private static final List<String> BOTS = List.of(
        "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)",
        "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)",
        "Mozilla/5.0 (compatible; Yeti/1.1; +https://naver.me/spd)",
        "kakaotalk-scrap/1.0");

    private final DemoProperties properties;
    private final OperatorRepository operators;
    private final CampaignRepository campaignRepository;
    private final TemplateService templates;
    private final CampaignService campaigns;
    private final FormService forms;
    private final CreatorService creators;
    private final LinkService links;
    private final VisitRepository visits;
    private final LeadRepository leads;
    private final ContactProtector protector;
    private final BotUserAgents bots;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public DemoDataSeeder(DemoProperties properties, OperatorRepository operators,
            CampaignRepository campaignRepository, TemplateService templates, CampaignService campaigns,
            FormService forms, CreatorService creators, LinkService links, VisitRepository visits,
            LeadRepository leads, ContactProtector protector, BotUserAgents bots, ObjectMapper objectMapper,
            JdbcTemplate jdbc, PlatformTransactionManager transactionManager, Clock clock) {
        this.properties = properties;
        this.operators = operators;
        this.campaignRepository = campaignRepository;
        this.templates = templates;
        this.campaigns = campaigns;
        this.forms = forms;
        this.creators = creators;
        this.links = links;
        this.visits = visits;
        this.leads = leads;
        this.protector = protector;
        this.bots = bots;
        this.objectMapper = objectMapper;
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public enum Outcome { DISABLED, NO_OPERATOR, NOT_EMPTY, SEEDED }

    public record SeedResult(Outcome outcome, int templates, int campaigns, int forms, int creators, int links,
            int visits, int botVisits, int leads) {

        static SeedResult skipped(Outcome outcome) {
            return new SeedResult(outcome, 0, 0, 0, 0, 0, 0, 0, 0);
        }
    }

    /** 운영자 시드(ApplicationRunner)가 끝난 뒤 돈다. 실패해도 앱은 계속 뜬다(예시 데이터는 체험용이다). */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            seedIfEnabled();
        } catch (RuntimeException e) {
            // 메시지에는 행 값이 실릴 수 있어 예외 종류만 남긴다.
            log.warn("예시 데이터 생성 실패(앱은 계속 실행): 원인={}. 깨끗이 다시 시작하려면 scripts/local-up.sh --fresh",
                e.getClass().getName());
        }
    }

    public SeedResult seedIfEnabled() {
        if (!properties.enabled()) {
            return SeedResult.skipped(Outcome.DISABLED);
        }
        Optional<Operator> owner = operators.findAll(Sort.by("id")).stream().findFirst();
        if (owner.isEmpty()) {
            log.info("예시 데이터 건너뜀: 운영자 계정이 없음(ADMIN_SEED_EMAIL 확인)");
            return SeedResult.skipped(Outcome.NO_OPERATOR);
        }
        if (campaignRepository.count() > 0) {
            log.info("예시 데이터 건너뜀: 캠페인이 이미 있음");
            return SeedResult.skipped(Outcome.NOT_EMPTY);
        }
        SeedResult result = new Run(owner.get().getId(), clock.instant()).seed();
        log.info("예시 데이터 생성: 템플릿 {}, 캠페인 {}, 폼 {}, 크리에이터 {}, 링크 {}, 방문 {}(봇 {}), 신청 {}",
            result.templates(), result.campaigns(), result.forms(), result.creators(), result.links(), result.visits(),
            result.botVisits(), result.leads());
        return result;
    }

    /** 한 번의 시드. 시각은 시작 시각 now 기준 "며칠 전"으로 계산한다. */
    private final class Run {

        private final long operatorId;
        private final Instant now;
        private final Random random = new Random(RANDOM_SEED);
        private final DemoPeople people = new DemoPeople(random);
        private final Map<String, HtmlTemplate> templateByKey = new HashMap<>();
        private final Map<String, Creator> creatorByKey = new HashMap<>();
        private final Map<String, Campaign> campaignByKey = new HashMap<>();
        private final Map<String, Form> formByKey = new HashMap<>();
        /** "form|channel|creator" → 링크 */
        private final Map<String, DistributionLink> linkByKey = new HashMap<>();
        private final List<Visit> visitRows = new ArrayList<>();
        private final List<Lead> leadRows = new ArrayList<>();

        Run(long operatorId, Instant now) {
            this.operatorId = operatorId;
            this.now = now;
        }

        SeedResult seed() {
            for (TemplateSpec spec : DemoCatalog.TEMPLATES) {
                templateByKey.put(spec.key(), templates.upload(spec.file(), read(spec.file()), spec.name(), operatorId));
            }
            for (CreatorSpec spec : DemoCatalog.CREATORS) {
                creatorByKey.put(spec.key(), creators.create(spec.name(), spec.handle(), spec.memo(), operatorId));
            }
            for (CampaignSpec spec : DemoCatalog.CAMPAIGNS) {
                campaignByKey.put(spec.key(), campaigns.create(spec.name(), spec.description(),
                    spec.startsDaysAgo() == null ? null : daysAgo(spec.startsDaysAgo()),
                    spec.endsDaysAgo() == null ? null : daysAgo(spec.endsDaysAgo()), spec.retentionDays(), operatorId));
            }
            for (FormSpec spec : DemoCatalog.FORMS) {
                formByKey.put(spec.key(), createForm(spec));
            }
            // 보관된 크리에이터의 링크는 보관 전에 만들어 둔다(보관 뒤에는 새 링크를 만들 수 없다).
            for (CreatorSpec spec : DemoCatalog.CREATORS) {
                if (spec.archived()) {
                    creators.update(creatorByKey.get(spec.key()).getId(), null, null, null, "archived", ACTOR);
                }
            }
            for (FormSpec spec : DemoCatalog.FORMS) {
                spec.traffic().forEach(traffic -> plan(spec, traffic));
            }
            int botVisits = planBots();
            transaction.executeWithoutResult(status -> insertTraffic());
            for (FormSpec spec : DemoCatalog.FORMS) {
                if (spec.state() == FormState.CLOSED) {
                    forms.update(formByKey.get(spec.key()).getId(), null, null, null, "closed", ACTOR);
                }
            }
            transaction.executeWithoutResult(status -> backdate());
            return new SeedResult(Outcome.SEEDED, templateByKey.size(), campaignByKey.size(), formByKey.size(),
                creatorByKey.size(), linkByKey.size(), visitRows.size(), botVisits, leadRows.size());
        }

        private Form createForm(FormSpec spec) {
            Form form = forms.create(campaignByKey.get(spec.campaign()).getId(), templateByKey.get(spec.template()).getId(),
                spec.title(), spec.consentText(), spec.slug());
            if (spec.state() == FormState.DRAFT) {
                return form;
            }
            List<FieldChange> labels = spec.labels().isEmpty() ? null : form.getFieldConfig().stream()
                .map(f -> new FieldChange(f.name(), spec.labels().get(f.name()), null)).toList();
            forms.update(form.getId(), null, null, labels, "published", ACTOR);
            for (String channel : DemoCatalog.CHANNELS) {
                link(spec.key(), form.getId(), channel, null);
            }
            for (TrafficSpec traffic : spec.traffic()) {
                if (traffic.creator() != null) {
                    link(spec.key(), form.getId(), traffic.channel(), traffic.creator());
                }
            }
            return forms.get(form.getId());
        }

        private void link(String formKey, long formId, String channel, String creator) {
            String key = formKey + "|" + channel + "|" + creator;
            if (!linkByKey.containsKey(key)) {
                Long creatorId = creator == null ? null : creatorByKey.get(creator).getId();
                linkByKey.put(key, links.create(formId, channel, creatorId).link());
            }
        }

        /** 방문자 traffic.visitors()명의 방문(1~3회)과 그중 traffic.leads()명의 신청을 만든다. */
        private void plan(FormSpec spec, TrafficSpec traffic) {
            Form form = formByKey.get(spec.key());
            Attribution attribution = traffic.channel() == null ? Attribution.direct()
                : Attribution.of(linkByKey.get(spec.key() + "|" + traffic.channel() + "|" + traffic.creator()));
            String referrer = traffic.channel() == null ? null : REFERRERS.get(traffic.channel());
            List<Integer> converting = new ArrayList<>();
            for (int i = 0; i < traffic.visitors(); i++) {
                converting.add(i);
            }
            Collections.shuffle(converting, random);
            List<Integer> leadIndexes = converting.subList(0, traffic.leads());
            for (int i = 0; i < traffic.visitors(); i++) {
                UUID visitor = randomUuid();
                String userAgent = BROWSERS.get(random.nextInt(BROWSERS.size()));
                Instant at = randomTime(traffic.fromDaysAgo(), traffic.toDaysAgo());
                int count = 1 + (random.nextInt(10) < 3 ? 1 : 0) + (random.nextInt(20) == 0 ? 1 : 0);
                Instant last = at;
                for (int v = 0; v < count; v++) {
                    visitRows.add(Visit.record(form.getId(), attribution.linkId(), attribution.channel(), visitor,
                        referrer, userAgent, bots.isBot(userAgent), last));
                    Instant next = last.plus(Duration.ofMinutes(30 + random.nextInt(60 * 30)));
                    if (v + 1 < count && next.isBefore(now.minus(Duration.ofMinutes(20)))) {
                        last = next;
                    } else {
                        count = v + 1;
                    }
                }
                if (leadIndexes.contains(i)) {
                    Instant submitted = min(last.plus(Duration.ofMinutes(2 + random.nextInt(10))),
                        now.minus(Duration.ofMinutes(1)));
                    leadRows.add(lead(spec, form, attribution, visitor, submitted));
                }
            }
        }

        private Lead lead(FormSpec spec, Form form, Attribution attribution, UUID visitor, Instant at) {
            Submission submission = SubmissionValidator.validate(form.getFieldConfig(), people.answers(spec.template()),
                true);
            return Lead.receive(form.getId(), form.getCampaignId(), attribution, visitor, submission,
                protector.protect(submission.name(), submission.phone(), submission.email()),
                ConsentEvidence.of(form.getConsentText()), at);
        }

        /** 링크 미리보기·검색 봇 방문. 폼마다 몇 건, 신청은 없다. 지표에서 빠지는 것을 보여 준다. */
        private int planBots() {
            int count = 0;
            for (FormSpec spec : DemoCatalog.FORMS) {
                if (spec.traffic().isEmpty()) {
                    continue;
                }
                Form form = formByKey.get(spec.key());
                for (int i = 0; i < 3 + random.nextInt(4); i++) {
                    TrafficSpec traffic = spec.traffic().get(random.nextInt(spec.traffic().size()));
                    Attribution attribution = traffic.channel() == null ? Attribution.direct()
                        : Attribution.of(linkByKey.get(spec.key() + "|" + traffic.channel() + "|" + traffic.creator()));
                    String userAgent = BOTS.get(random.nextInt(BOTS.size()));
                    visitRows.add(Visit.record(form.getId(), attribution.linkId(), attribution.channel(), randomUuid(),
                        null, userAgent, bots.isBot(userAgent), randomTime(traffic.fromDaysAgo(), traffic.toDaysAgo())));
                    count++;
                }
            }
            return count;
        }

        private void insertTraffic() {
            visitRows.sort(Comparator.comparing(Visit::getOccurredAt));
            visits.saveAll(visitRows);
            leadRows.sort(Comparator.comparing(Lead::getCreatedAt));
            for (Lead lead : leadRows) {
                if (!leads.insertIfAbsent(lead, toJson(lead.getPayload()))) {
                    throw new IllegalStateException("예시 신청이 중복으로 걸러졌습니다");
                }
            }
        }

        /** 생성·게시 시각을 방문 기간 앞으로 옮긴다. 마감은 설계한 날, 초안은 이틀 전이다. */
        private void backdate() {
            for (HtmlTemplate template : templateByKey.values()) {
                jdbc.update("UPDATE html_templates SET created_at = ?, updated_at = ? WHERE id = ?",
                    at(DemoCatalog.PERIOD_DAYS + 5), at(DemoCatalog.PERIOD_DAYS + 5), template.getId());
            }
            for (CreatorSpec spec : DemoCatalog.CREATORS) {
                jdbc.update("UPDATE creators SET created_at = ?, updated_at = ? WHERE id = ?",
                    at(DemoCatalog.PERIOD_DAYS + 4), at(spec.archived() ? 11 : DemoCatalog.PERIOD_DAYS + 4),
                    creatorByKey.get(spec.key()).getId());
            }
            for (Campaign campaign : campaignByKey.values()) {
                jdbc.update("UPDATE campaigns SET created_at = ?, updated_at = ? WHERE id = ?",
                    at(DemoCatalog.PERIOD_DAYS + 3), at(DemoCatalog.PERIOD_DAYS + 3), campaign.getId());
            }
            for (FormSpec spec : DemoCatalog.FORMS) {
                long id = formByKey.get(spec.key()).getId();
                switch (spec.state()) {
                    case DRAFT -> jdbc.update("UPDATE forms SET created_at = ?, updated_at = ? WHERE id = ?",
                        at(2), at(2), id);
                    case PUBLISHED -> jdbc.update(
                        "UPDATE forms SET created_at = ?, published_at = ?, updated_at = ? WHERE id = ?",
                        at(DemoCatalog.PERIOD_DAYS + 2), at(DemoCatalog.PERIOD_DAYS + 1), at(DemoCatalog.PERIOD_DAYS + 1), id);
                    case CLOSED -> jdbc.update(
                        "UPDATE forms SET created_at = ?, published_at = ?, closed_at = ?, updated_at = ? WHERE id = ?",
                        at(DemoCatalog.PERIOD_DAYS + 2), at(DemoCatalog.PERIOD_DAYS + 1), at(spec.closedDaysAgo()),
                        at(spec.closedDaysAgo()), id);
                }
            }
            for (DistributionLink link : linkByKey.values()) {
                jdbc.update("UPDATE distribution_links SET created_at = ? WHERE id = ?", at(DemoCatalog.PERIOD_DAYS + 1),
                    link.getId());
            }
        }

        private OffsetDateTime at(int days) {
            return OffsetDateTime.ofInstant(daysAgo(days), ZoneOffset.UTC);
        }

        private Instant daysAgo(int days) {
            return now.minus(Duration.ofDays(days));
        }

        /** [from, to]일 전(Asia/Seoul 날짜) 중 하루의 저녁 위주 시각. 지금보다 늦으면 조금 전으로 당긴다. */
        private Instant randomTime(int fromDaysAgo, int toDaysAgo) {
            int days = toDaysAgo + random.nextInt(fromDaysAgo - toDaysAgo + 1);
            LocalDate date = LocalDate.ofInstant(now, SEOUL).minusDays(days);
            Instant at = date.atTime(HOURS[random.nextInt(HOURS.length)], random.nextInt(60), random.nextInt(60))
                .atZone(SEOUL).toInstant();
            Instant latest = now.minus(Duration.ofMinutes(20));
            if (at.isAfter(latest)) {
                at = latest.minus(Duration.ofMinutes(random.nextInt(240)));
            }
            Instant earliest = daysAgo(fromDaysAgo);
            return at.isBefore(earliest) ? earliest.plus(Duration.ofMinutes(30 + random.nextInt(600))) : at;
        }

        /** UUID v4 모양(방문자 쿠키와 같은 형식). 시드가 같으면 같은 값이다. */
        private UUID randomUuid() {
            long most = (random.nextLong() & 0xffffffffffff0fffL) | 0x0000000000004000L;
            long least = (random.nextLong() & 0x3fffffffffffffffL) | 0x8000000000000000L;
            return new UUID(most, least);
        }

        private static Instant min(Instant a, Instant b) {
            return a.isBefore(b) ? a : b;
        }

        private String toJson(Map<String, Object> payload) {
            try {
                return objectMapper.writeValueAsString(payload);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("payload를 JSON으로 바꾸지 못했습니다", e);
            }
        }
    }

    private static byte[] read(String file) {
        try (InputStream in = new ClassPathResource("demo/" + file).getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
