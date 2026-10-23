package com.leadmagnet.crm.lead.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.leadmagnet.crm.lead.application.LeadRetentionService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.Scheduled;

class RetentionSchedulerTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(RetentionSchedulingConfig.class)
        .withBean(LeadRetentionService.class, () -> mock(LeadRetentionService.class));

    @Test
    void 매일_서울_03시에_돈다() throws Exception {
        Scheduled scheduled = RetentionScheduler.class.getMethod("runDaily").getAnnotation(Scheduled.class);

        assertThat(scheduled.cron()).isEqualTo("0 0 3 * * *");
        assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
    }

    @Test
    void 설정이_없으면_켜지고_false면_스케줄러를_만들지_않는다() {
        runner.run(context -> assertThat(context).hasSingleBean(RetentionScheduler.class));
        runner.withPropertyValues("app.retention.enabled=true")
            .run(context -> assertThat(context).hasSingleBean(RetentionScheduler.class));
        runner.withPropertyValues("app.retention.enabled=false")
            .run(context -> assertThat(context).doesNotHaveBean(RetentionScheduler.class));
    }

    @Test
    void scheduled로_실행하고_실패해도_예외를_밖으로_던지지_않는다() {
        LeadRetentionService service = mock(LeadRetentionService.class);
        when(service.run(eq(LeadRetentionService.TRIGGER_SCHEDULED), any()))
            .thenThrow(new IllegalStateException("db down"));

        new RetentionScheduler(service).runDaily();

        verify(service).run(eq(LeadRetentionService.TRIGGER_SCHEDULED), any());
    }

    @Test
    void 배치_크기는_1_이상이고_기본값은_켜짐_500건이다() {
        RetentionProperties defaults = new RetentionProperties(null, null);
        assertThat(defaults.enabled()).isTrue();
        assertThat(defaults.batchSize()).isEqualTo(500);
        assertThatThrownBy(() -> new RetentionProperties(true, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
