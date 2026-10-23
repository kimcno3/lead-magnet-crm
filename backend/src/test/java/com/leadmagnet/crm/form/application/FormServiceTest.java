package com.leadmagnet.crm.form.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.leadmagnet.crm.campaign.domain.Campaign;
import com.leadmagnet.crm.campaign.domain.CampaignRepository;
import com.leadmagnet.crm.common.error.DomainException;
import com.leadmagnet.crm.common.error.ErrorCode;
import com.leadmagnet.crm.form.domain.Form;
import com.leadmagnet.crm.form.domain.FormRepository;
import com.leadmagnet.crm.template.application.TemplateService;
import com.leadmagnet.crm.template.domain.FormField;
import com.leadmagnet.crm.template.domain.HtmlTemplate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class FormServiceTest {

    FormRepository forms = mock(FormRepository.class);
    CampaignRepository campaigns = mock(CampaignRepository.class);
    TemplateService templates = mock(TemplateService.class);
    Clock clock = Clock.fixed(Instant.parse("2026-10-23T00:00:00Z"), ZoneOffset.UTC);
    FormService service = new FormService(forms, campaigns, templates, clock);

    @BeforeEach
    void setUp() {
        Campaign campaign = Campaign.create("c", null, null, null, 1L, clock.instant());
        when(campaigns.findById(1L)).thenReturn(Optional.of(campaign));
        HtmlTemplate template = HtmlTemplate.create("t", "<form></form>",
            List.of(new FormField("name", "text", true, "이름", null)), List.of(), 1L, clock.instant());
        when(templates.get(2L)).thenReturn(template);
    }

    @Test
    void ux_forms_slug_유니크_위반은_slug_taken으로_바꾼다() {
        when(forms.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup",
            new RuntimeException("duplicate key value violates unique constraint \"ux_forms_slug\"")));

        assertThatThrownBy(() -> service.create(1L, 2L, "t", "c", "my-slug"))
            .isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SLUG_TAKEN));
    }

    @Test
    void 다른_무결성_위반은_그대로_다시_던진다() {
        DataIntegrityViolationException other = new DataIntegrityViolationException("fk",
            new RuntimeException("violates foreign key constraint \"forms_template_id_fkey\""));
        when(forms.saveAndFlush(any())).thenThrow(other);

        assertThatThrownBy(() -> service.create(1L, 2L, "t", "c", "my-slug")).isSameAs(other);
    }

    @Test
    void 랜덤_slug가_5번_모두_충돌하면_slug_taken이다() {
        when(forms.existsBySlug(any())).thenReturn(true);

        assertThatThrownBy(() -> service.create(1L, 2L, "t", "c", null))
            .isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SLUG_TAKEN));
        org.mockito.Mockito.verify(forms, org.mockito.Mockito.times(5)).existsBySlug(any());
    }

    @Test
    void 정상이면_저장한_폼을_돌려준다() {
        when(forms.saveAndFlush(any(Form.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(service.create(1L, 2L, "t", "c", null).getSlug()).matches("[a-z0-9]{8}");
    }
}
