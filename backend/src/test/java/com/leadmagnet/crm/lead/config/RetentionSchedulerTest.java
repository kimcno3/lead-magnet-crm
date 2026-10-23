package com.leadmagnet.crm.lead.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.leadmagnet.crm.common.SchedulingConfig;
import com.leadmagnet.crm.lead.application.LeadRetentionService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;

@ExtendWith(OutputCaptureExtension.class)
class RetentionSchedulerTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(SchedulingConfig.class, RetentionSchedulingConfig.class)
        .withBean(LeadRetentionService.class, () -> mock(LeadRetentionService.class));

    @Test
    void 매일_서울_03시에_돈다() throws Exception {
        Scheduled scheduled = RetentionScheduler.class.getMethod("runDaily").getAnnotation(Scheduled.class);

        assertThat(scheduled.cron()).isEqualTo("0 0 3 * * *");
        assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
    }

    @Test
    void 켜면_cron_작업이_등록되고_false면_파기_작업만_빠지며_스케줄링은_켜져_있다() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(RetentionScheduler.class);
            assertThat(cronExpressions(context)).containsExactly("0 0 3 * * *");
        });
        runner.withPropertyValues("app.retention.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(RetentionScheduler.class);
            assertThat(context).hasSingleBean(ScheduledTaskHolder.class);
            assertThat(cronExpressions(context)).isEmpty();
        });
    }

    private static List<String> cronExpressions(ApplicationContext context) {
        return context.getBeansOfType(ScheduledTaskHolder.class).values().stream()
            .flatMap(holder -> holder.getScheduledTasks().stream())
            .map(ScheduledTask::getTask)
            .filter(CronTask.class::isInstance)
            .map(task -> ((CronTask) task).getExpression())
            .toList();
    }

    @Test
    void scheduled로_실행하고_실패해도_예외를_밖으로_던지지_않고_예외_종류만_ERROR로_남긴다(CapturedOutput output) {
        LeadRetentionService service = mock(LeadRetentionService.class);
        when(service.run(eq(LeadRetentionService.TRIGGER_SCHEDULED), any()))
            .thenThrow(new IllegalStateException("db down 01012345678"));

        new RetentionScheduler(service).runDaily();

        verify(service).run(eq(LeadRetentionService.TRIGGER_SCHEDULED), any());
        assertThat(output.getAll()).contains("ERROR").contains("보존 기간 파기 실패: 원인=java.lang.IllegalStateException")
            .doesNotContain("db down").doesNotContain("01012345678").doesNotContain("\tat ");
    }

    @Test
    void 배치_크기는_1_이상이고_기본값은_켜짐_500건이다() {
        RetentionProperties defaults = new RetentionProperties(null, null);
        assertThat(defaults.enabled()).isTrue();
        assertThat(defaults.batchSize()).isEqualTo(500);
        assertThatThrownBy(() -> new RetentionProperties(true, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
