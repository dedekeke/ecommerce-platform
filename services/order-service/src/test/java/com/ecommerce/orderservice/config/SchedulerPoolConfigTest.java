package com.ecommerce.orderservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.task.TaskSchedulingProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts the property keys used in {@code application.yml} to size the
 * dedicated scheduler pool actually bind to Spring's {@link TaskSchedulingProperties}.
 * A typo in the key (e.g. {@code spring.task.scheduler.*}) would silently leave
 * the single-threaded default in place, so this guards the fix for scheduler
 * starvation across order-service's several @Scheduled jobs.
 */
class SchedulerPoolConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(PropsConfig.class);

    @Test
    void should_bindPoolSizeAndThreadPrefix_when_schedulingPropertiesSet() {
        runner
            .withPropertyValues(
                "spring.task.scheduling.pool.size=4",
                "spring.task.scheduling.thread-name-prefix=order-sched-")
            .run(ctx -> {
                TaskSchedulingProperties props = ctx.getBean(TaskSchedulingProperties.class);
                assertThat(props.getPool().getSize()).isEqualTo(4);
                assertThat(props.getThreadNamePrefix()).isEqualTo("order-sched-");
            });
    }

    @Configuration
    @EnableConfigurationProperties(TaskSchedulingProperties.class)
    static class PropsConfig {
    }
}
