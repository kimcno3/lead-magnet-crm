package com.leadmagnet.crm.lead.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.leadmagnet.crm.common.SchedulingConfig;
import com.leadmagnet.crm.lead.application.LeadRetentionService;
import java.util.List;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;

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
    void scheduled로_실행하고_실패해도_예외를_밖으로_던지지_않고_예외_종류만_ERROR로_남긴다() {
        LeadRetentionService service = mock(LeadRetentionService.class);
        when(service.run(eq(LeadRetentionService.TRIGGER_SCHEDULED), any()))
            .thenThrow(new IllegalStateException("db down 01012345678"));
        // 콘솔로 흘리지 않고 이 테스트 안에서만 잡는다(ERROR 줄이 테스트 출력에 섞이지 않게 한다)
        Logger logger = (Logger) LoggerFactory.getLogger(RetentionScheduler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        boolean additive = logger.isAdditive();
        logger.setAdditive(false);
        try {
            new RetentionScheduler(service).runDaily();
        } finally {
            logger.setAdditive(additive);
            logger.detachAppender(appender);
        }

        verify(service).run(eq(LeadRetentionService.TRIGGER_SCHEDULED), any());
        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getFormattedMessage()).contains("보존 기간 파기 실패: 원인=java.lang.IllegalStateException")
            .doesNotContain("db down").doesNotContain("01012345678");
        assertThat(event.getThrowableProxy()).isNull();
    }

    @Test
    void 배치_크기는_1_이상이고_기본값은_켜짐_500건이다() {
        RetentionProperties defaults = new RetentionProperties(null, null);
        assertThat(defaults.enabled()).isTrue();
        assertThat(defaults.batchSize()).isEqualTo(500);
        assertThatThrownBy(() -> new RetentionProperties(true, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
