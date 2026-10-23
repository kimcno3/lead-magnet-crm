package com.leadmagnet.crm.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.leadmagnet.crm.auth.application.LoginThrottle;
import com.leadmagnet.crm.common.SchedulingConfig;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;

/** 로그인 실패 기록 정리 스케줄 (ADR 0021 변경 기록). 지우는 규칙 자체는 LoginThrottleTest가 DB로 확인한다. */
class LoginFailureCleanupTest {

    @Test
    void 기동_5분_뒤부터_5분마다_돈다() throws Exception {
        Scheduled scheduled = LoginFailureCleanup.class.getMethod("purgeExpired").getAnnotation(Scheduled.class);
        assertThat(scheduled.fixedDelay()).isEqualTo(5);
        assertThat(scheduled.initialDelay()).isEqualTo(5);
        assertThat(scheduled.timeUnit()).isEqualTo(TimeUnit.MINUTES);

        new ApplicationContextRunner()
            .withUserConfiguration(SchedulingConfig.class, LoginFailureCleanup.class)
            .withBean(LoginThrottle.class, () -> mock(LoginThrottle.class))
            .run(context -> assertThat(fixedDelays(context)).containsExactly(Duration.ofMinutes(5)));
    }

    private static List<Duration> fixedDelays(ApplicationContext context) {
        return context.getBeansOfType(ScheduledTaskHolder.class).values().stream()
            .flatMap(holder -> holder.getScheduledTasks().stream())
            .map(ScheduledTask::getTask)
            .filter(FixedDelayTask.class::isInstance)
            .map(task -> ((FixedDelayTask) task).getIntervalDuration())
            .toList();
    }

    @Test
    void 정리를_부르고_실패해도_예외를_던지지_않고_예외_종류만_ERROR로_남긴다() {
        LoginThrottle throttle = mock(LoginThrottle.class);
        when(throttle.purgeExpired()).thenThrow(new IllegalStateException("db down 203.0.113.9"));
        Logger logger = (Logger) LoggerFactory.getLogger(LoginFailureCleanup.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        boolean additive = logger.isAdditive();
        logger.setAdditive(false);
        try {
            new LoginFailureCleanup(throttle).purgeExpired();
        } finally {
            logger.setAdditive(additive);
            logger.detachAppender(appender);
        }

        verify(throttle).purgeExpired();
        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getFormattedMessage()).contains("로그인 실패 기록 정리 실패: 원인=java.lang.IllegalStateException")
            .doesNotContain("203.0.113.9");
        assertThat(event.getThrowableProxy()).isNull();
    }
}
