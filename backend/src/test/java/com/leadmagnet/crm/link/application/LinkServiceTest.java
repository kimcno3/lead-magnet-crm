package com.leadmagnet.crm.link.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.leadmagnet.crm.common.error.DomainException;
import com.leadmagnet.crm.common.error.ErrorCode;
import com.leadmagnet.crm.form.application.FormService;
import com.leadmagnet.crm.form.domain.Form;
import com.leadmagnet.crm.form.domain.Slug;
import com.leadmagnet.crm.link.domain.Attribution;
import com.leadmagnet.crm.link.domain.AttributionChannel;
import com.leadmagnet.crm.link.domain.Channel;
import com.leadmagnet.crm.link.domain.DistributionLink;
import com.leadmagnet.crm.link.domain.DistributionLinkRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class LinkServiceTest {

    DistributionLinkRepository links = mock(DistributionLinkRepository.class);
    FormService forms = mock(FormService.class);
    LinkService service;
    Form form;

    @BeforeEach
    void setUp() {
        service = new LinkService(links, forms, new RandomLinkCodeGenerator(), Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneOffset.UTC));
        form = Form.create(1L, 1L, new Slug("spring-2026"), "제목", "동의", List.of(), Instant.now());
        when(forms.get(7L)).thenReturn(form);
    }

    @Test
    void 코드가_충돌하면_다섯_번까지_재시도해_성공한다() {
        when(links.saveAndFlush(any()))
            .thenThrow(new DataIntegrityViolationException("ux_distribution_links_code"))
            .thenThrow(new DataIntegrityViolationException("ux_distribution_links_code"))
            .thenAnswer(inv -> inv.getArgument(0));

        DistributionLink created = service.create(7L, "x");

        assertThat(created.getChannel()).isEqualTo(Channel.X);
        verify(links, times(3)).saveAndFlush(any());
    }

    @Test
    void 다섯_번_모두_충돌하면_500이다() {
        when(links.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("ux_distribution_links_code"));

        assertThatThrownBy(() -> service.create(7L, "x"))
            .isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR));
        verify(links, times(5)).saveAndFlush(any());
    }

    @Test
    void 코드_충돌이_아닌_무결성_오류는_재시도하지_않고_그대로_던진다() {
        when(links.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("fk_violation"));

        assertThatThrownBy(() -> service.create(7L, "x")).isInstanceOf(DataIntegrityViolationException.class);
        verify(links, times(1)).saveAndFlush(any());
    }

    @Test
    void 코드가_해당_폼의_것일_때만_링크를_돌려준다() {
        DistributionLink link = DistributionLink.create(7L, Channel.X, "abCD1234", "spring-2026", Instant.now());
        when(links.findByCode("abCD1234")).thenReturn(Optional.of(link));

        assertThat(service.resolveForForm(7L, "abCD1234")).contains(link);
        assertThat(service.resolveForForm(8L, "abCD1234")).isEmpty();
        assertThat(service.resolveForForm(7L, "zzzzzzzz")).isEmpty();
        assertThat(service.resolveForForm(7L, null)).isEmpty();
        assertThat(service.resolveForForm(7L, "bad!")).isEmpty();
    }

    @Test
    void 귀속은_해당_폼의_링크면_링크_id와_채널을_아니면_direct를_돌려준다() {
        DistributionLink link = DistributionLink.create(7L, Channel.THREADS, "abCD1234", "spring-2026", Instant.now());
        org.springframework.test.util.ReflectionTestUtils.setField(link, "id", 5L);
        when(links.findByCode("abCD1234")).thenReturn(Optional.of(link));

        assertThat(service.attribute(7L, "abCD1234")).isEqualTo(new Attribution(5L, AttributionChannel.THREADS));
        assertThat(service.attribute(8L, "abCD1234")).isEqualTo(Attribution.direct());
        assertThat(service.attribute(7L, null)).isEqualTo(Attribution.direct());
        assertThat(service.attribute(7L, "zzzzzzzz")).isEqualTo(Attribution.direct());
    }
}
