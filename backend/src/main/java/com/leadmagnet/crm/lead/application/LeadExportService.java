package com.leadmagnet.crm.lead.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadmagnet.crm.audit.application.AuditService;
import com.leadmagnet.crm.audit.domain.AuditActor;
import com.leadmagnet.crm.audit.domain.AuditEvent;
import com.leadmagnet.crm.campaign.domain.CampaignRepository;
import com.leadmagnet.crm.common.Csv;
import com.leadmagnet.crm.common.DateRange;
import com.leadmagnet.crm.common.PhoneFormat;
import com.leadmagnet.crm.common.crypto.PiiCryptoException;
import com.leadmagnet.crm.common.error.DomainException;
import com.leadmagnet.crm.common.error.DomainException.FieldError;
import com.leadmagnet.crm.common.error.ErrorCode;
import com.leadmagnet.crm.creator.domain.Creator;
import com.leadmagnet.crm.creator.domain.CreatorRepository;
import com.leadmagnet.crm.form.domain.Form;
import com.leadmagnet.crm.form.domain.FormRepository;
import com.leadmagnet.crm.lead.domain.Lead;
import com.leadmagnet.crm.lead.domain.LeadRepository;
import com.leadmagnet.crm.link.domain.AttributionChannel;
import com.leadmagnet.crm.link.domain.DistributionLink;
import com.leadmagnet.crm.link.domain.DistributionLinkRepository;
import com.leadmagnet.crm.template.domain.FormField;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 명단 CSV 다운로드 (ADR 0016). 두 단계로 나눈다.
 * <ol>
 *   <li>{@link #start}: 필터를 검증하고, 내보낼 행의 범위(지금 보이는 가장 큰 id)와 그 범위의 건수를 정한 뒤 감사 로그를
 *       같은 트랜잭션에서 남긴다. 기록이 실패하면 응답 바이트를 하나도 쓰기 전에 요청이 실패한다.</li>
 *   <li>{@link #write}: 같은 범위(id ≤ maxId)의 행을 id 순으로 {@value #BATCH_SIZE}건씩 keyset으로 읽어 스트림에 쓴다
 *       (배치마다 COUNT 없음). 신청은 바뀌거나 지워지지 않으므로, maxId보다 작은 id의 신청이 그 사이에 늦게 커밋되는
 *       경우(동시 신청)가 아니면 기록한 건수와 내려주는 행 수가 같다.</li>
 * </ol>
 * 기본은 가린 값(payload의 연락처처럼 보이는 항목 포함)이고 reveal이면 원문이다. 보존 기간이 지나 파기된 신청은 연락처·추가 항목 칸이 비고 "파기" 칸에 "파기됨"이다. 복호화하지 못한 행은 연락처 칸에 "복호화 불가"를 적고 로그에는 신청 id만 남긴다.
 */
@Service
public class LeadExportService {

    static final int BATCH_SIZE = 500;
    static final String UNAVAILABLE = "복호화 불가";
    static final String ANONYMIZED = "파기됨";
    static final List<String> HEADER = List.of("신청일", "이름", "전화번호", "이메일", "채널", "크리에이터", "폼", "추가 항목",
        "파기");

    private static final Logger log = LoggerFactory.getLogger(LeadExportService.class);
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        .withZone(DateRange.ZONE);
    private static final DateTimeFormatter CREATED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(DateRange.ZONE);

    private final LeadRepository leads;
    private final CampaignRepository campaigns;
    private final FormRepository forms;
    private final DistributionLinkRepository links;
    private final CreatorRepository creators;
    private final ContactProtector protector;
    private final AuditService audit;
    private final ObjectMapper objectMapper;

    public LeadExportService(LeadRepository leads, CampaignRepository campaigns, FormRepository forms,
            DistributionLinkRepository links, CreatorRepository creators, ContactProtector protector,
            AuditService audit, ObjectMapper objectMapper) {
        this.leads = leads;
        this.campaigns = campaigns;
        this.forms = forms;
        this.links = links;
        this.creators = creators;
        this.protector = protector;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    /** 모르는 channel은 422, 없는 캠페인은 404(둘 다 기록 없음). 감사 기록이 실패하면 예외가 그대로 올라간다. */
    @Transactional
    public LeadExport start(long campaignId, Long formId, String channel, boolean reveal, AuditActor actor) {
        List<FieldError> errors = new ArrayList<>();
        AttributionChannel channelFilter = LeadFilter.parseChannel(channel, errors);
        if (!errors.isEmpty()) {
            throw DomainException.validation(errors);
        }
        if (!campaigns.existsById(campaignId)) {
            throw new DomainException(ErrorCode.NOT_FOUND);
        }
        LeadFilter filter = new LeadFilter(campaignId, formId, channelFilter);
        long maxId = leads.findBy(filter.toSpecification(), q -> q.sortBy(Sort.by(Sort.Order.desc("id"))).first())
            .map(Lead::getId).orElse(0L);
        long count = leads.count(upTo(filter, maxId));

        audit.recordRequired(actor, AuditEvent.export(campaignId, formId,
            channelFilter == null ? null : channelFilter.value(), reveal, count));
        return new LeadExport(campaignId, formId, channelFilter, reveal, maxId, count);
    }

    /** UTF-8 BOM, 헤더, 행 순서로 쓴다. 스트림은 닫지 않는다. */
    public void write(LeadExport export, OutputStream out) throws IOException {
        Writer writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        writer.write('﻿');
        writer.write(Csv.line(HEADER));
        if (export.count() > 0) {
            Lookups lookups = lookups(export.campaignId());
            Specification<Lead> inRange = upTo(filterOf(export), export.maxId());
            long lastId = 0;
            while (true) {
                long after = lastId;
                List<Lead> batch = leads.findBy(
                    inRange.and((root, query, cb) -> cb.greaterThan(root.get("id"), after)),
                    q -> q.sortBy(Sort.by("id")).limit(BATCH_SIZE).all());
                for (Lead lead : batch) {
                    writer.write(Csv.line(row(lead, export.reveal(), lookups)));
                }
                if (batch.size() < BATCH_SIZE) {
                    break;
                }
                lastId = batch.get(batch.size() - 1).getId();
            }
        }
        writer.flush();
    }

    private static LeadFilter filterOf(LeadExport export) {
        return new LeadFilter(export.campaignId(), export.formId(), export.channel());
    }

    /** 감사 기록의 건수와 스트림이 같은 범위를 보게 하는 조건(id ≤ maxId). */
    private static Specification<Lead> upTo(LeadFilter filter, long maxId) {
        return filter.toSpecification().and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("id"), maxId));
    }

    private List<String> row(Lead lead, boolean reveal, Lookups lookups) {
        // 파기된 신청은 연락처·추가 항목을 비우고 마지막 칸에 표시한다(ADR 0020).
        LeadContact shown = lead.isAnonymized() ? new LeadContact(null, null, null) : contactCells(lead, reveal);
        String name = shown.name();
        String phone = shown.phone();
        String email = shown.email();
        String creator = lead.getLinkId() == null ? null : lookups.creatorByLink().get(lead.getLinkId());
        List<String> cells = new ArrayList<>();
        cells.add(CREATED_AT.format(lead.getCreatedAt()));
        cells.add(name);
        cells.add(phone);
        cells.add(email);
        cells.add(lead.getChannel().value());
        cells.add(creator);
        cells.add(lookups.formTitles().get(lead.getFormId()));
        cells.add(lead.isAnonymized() ? null : payloadJson(reveal ? lead.getPayload()
            : ExtraContacts.mask(lead.getPayload(), lookups.fieldsByForm().get(lead.getFormId()))));
        cells.add(lead.isAnonymized() ? ANONYMIZED : null);
        return cells;
    }

    private LeadContact contactCells(Lead lead, boolean reveal) {
        try {
            LeadContact contact = protector.reveal(lead);
            // 원문 전화번호는 하이픈을 넣는다: 숫자만이면 스프레드시트가 앞의 0을 지운다(ADR 0016)
            return reveal ? new LeadContact(contact.name(), PhoneFormat.hyphenate(contact.phone()), contact.email())
                : contact.masked();
        } catch (PiiCryptoException e) {
            log.warn("신청 연락처를 복호화하지 못해 CSV에 복호화 불가로 적음: leadId={}", lead.getId());
            return new LeadContact(UNAVAILABLE, UNAVAILABLE, UNAVAILABLE);
        }
    }

    private Lookups lookups(long campaignId) {
        List<Form> campaignForms = forms.findAllByCampaignIdOrderByIdAsc(campaignId);
        Map<Long, String> titles = campaignForms.stream().collect(Collectors.toMap(Form::getId, Form::getTitle));
        List<DistributionLink> campaignLinks = links.findAllByFormIdIn(titles.keySet());
        Map<Long, String> creatorNames = creators.findAllById(campaignLinks.stream().map(DistributionLink::getCreatorId)
                .filter(Objects::nonNull).distinct().toList()).stream()
            .collect(Collectors.toMap(Creator::getId, Creator::getName));
        Map<Long, String> creatorByLink = campaignLinks.stream().filter(l -> l.getCreatorId() != null)
            .collect(Collectors.toMap(DistributionLink::getId, l -> creatorNames.get(l.getCreatorId())));
        Map<Long, List<FormField>> fieldsByForm = campaignForms.stream()
            .collect(Collectors.toMap(Form::getId, Form::getFieldConfig));
        return new Lookups(titles, creatorByLink, fieldsByForm);
    }

    private String payloadJson(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            // 저장된 payload는 jsonb에서 읽은 값이라 다시 직렬화할 수 있다.
            throw new IllegalStateException("payload를 JSON으로 바꾸지 못했습니다", e);
        }
    }

    private record Lookups(Map<Long, String> formTitles, Map<Long, String> creatorByLink,
            Map<Long, List<FormField>> fieldsByForm) {
    }

    /** 내보낼 범위. formId·channel은 null이면 필터 없음. maxId 이하의 행만 쓴다(기록한 건수와 맞추려고). */
    public record LeadExport(long campaignId, Long formId, AttributionChannel channel, boolean reveal, long maxId,
            long count) {

        /** 내려받을 파일 이름. ASCII만 쓴다. */
        public String fileName(Instant now) {
            return "leads-campaign-" + campaignId + "-" + FILE_STAMP.format(now) + (reveal ? "-raw" : "")
                + ".csv";
        }
    }
}
