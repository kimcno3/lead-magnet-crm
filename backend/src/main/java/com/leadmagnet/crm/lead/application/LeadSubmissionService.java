package com.leadmagnet.crm.lead.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadmagnet.crm.common.PiiMasker;
import com.leadmagnet.crm.common.error.DomainException;
import com.leadmagnet.crm.common.error.ErrorCode;
import com.leadmagnet.crm.form.application.FormTokenService;
import com.leadmagnet.crm.form.application.PublicFormService;
import com.leadmagnet.crm.form.domain.Form;
import com.leadmagnet.crm.lead.domain.ConsentEvidence;
import com.leadmagnet.crm.lead.domain.Lead;
import com.leadmagnet.crm.lead.domain.LeadRepository;
import com.leadmagnet.crm.lead.domain.Submission;
import com.leadmagnet.crm.lead.domain.SubmissionValidator;
import com.leadmagnet.crm.link.application.LinkService;
import com.leadmagnet.crm.link.domain.Attribution;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공개 폼 신청 접수. 폼이 열려 있는지(draft·없음 404, closed 410) 확인하고 fieldConfig로 검증한 뒤 저장한다.
 * 중복(같은 폼의 같은 전화번호·이메일)은 DB 부분 유니크 인덱스가 최종으로 막고(ON CONFLICT DO NOTHING) 409로 바꾼다 (ADR 0007).
 * 연락처는 암호문과 HMAC 해시로만 저장한다(ADR 0015). 중복 판정은 해시 유니크 인덱스가 한다.
 * 폼 상태 다음에 작성 시간 토큰(422 invalid_form_token)을, 그다음 honeypot을 필드 검증보다 먼저 본다(ADR 0019).
 * honeypot이 채워지면 봇에게 차단 사실을 알리지 않도록 정상과 같이 끝내되 저장하지 않고, 로그에는 누적 건수만 남긴다.
 */
@Service
public class LeadSubmissionService {

    private static final Logger log = LoggerFactory.getLogger(LeadSubmissionService.class);

    private final PublicFormService publicForms;
    private final FormTokenService formTokens;
    private final LinkService links;
    private final LeadRepository leads;
    private final ContactProtector protector;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final AtomicLong honeypotHits = new AtomicLong();

    public LeadSubmissionService(PublicFormService publicForms, FormTokenService formTokens, LinkService links,
            LeadRepository leads, ContactProtector protector, Clock clock, ObjectMapper objectMapper) {
        this.publicForms = publicForms;
        this.formTokens = formTokens;
        this.links = links;
        this.leads = leads;
        this.protector = protector;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void submit(String slug, Map<String, Object> fields, boolean consent, String lm, UUID visitorId,
            String formToken) {
        Form form = publicForms.getOpen(slug);
        formTokens.verify(formToken, form.getSlug());
        if (SubmissionValidator.isHoneypotFilled(fields)) {
            // 입력값은 봇이 보낸 것이라도 개인정보일 수 있어 남기지 않는다. 건수만 센다.
            log.info("honeypot 제출 무시(저장 안 함): formId={}, 누적={}", form.getId(), honeypotHits.incrementAndGet());
            return;
        }
        Submission submission = SubmissionValidator.validate(form.getFieldConfig(), fields, consent);
        Attribution attribution = links.attribute(form.getId(), lm);
        Lead lead = Lead.receive(form.getId(), form.getCampaignId(), attribution, visitorId, submission,
            protector.protect(submission.name(), submission.phone(), submission.email()),
            ConsentEvidence.of(form.getConsentText()), clock.instant());
        if (!leads.insertIfAbsent(lead, toJson(lead.getPayload()))) {
            log.info("중복 신청 거절: formId={}, phone={}, email={}", form.getId(),
                PiiMasker.maskPhone(submission.phone()), PiiMasker.maskEmail(submission.email()));
            throw new DomainException(ErrorCode.DUPLICATE_SUBMISSION);
        }
        log.info("신청 접수: formId={}, channel={}, phone={}, email={}", form.getId(), attribution.channel().value(),
            PiiMasker.maskPhone(submission.phone()), PiiMasker.maskEmail(submission.email()));
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            // 검증을 통과한 문자열·문자열 목록뿐이라 일어나지 않는다.
            throw new IllegalStateException("payload를 JSON으로 바꾸지 못했습니다", e);
        }
    }
}
