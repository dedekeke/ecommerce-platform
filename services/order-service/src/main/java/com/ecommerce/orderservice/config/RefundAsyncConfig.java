package com.ecommerce.orderservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Bounded executor backing the refund saga's {@code @Async("refundExecutor")}
 * dispatch.
 *
 * <p>We deliberately provide an explicit {@link ThreadPoolTaskExecutor} rather
 * than relying on Spring's default {@code SimpleAsyncTaskExecutor}, which
 * creates a brand-new thread for every submitted task and has no upper bound —
 * a refund storm could exhaust the host. This pool caps concurrency and queues
 * the overflow; the {@code CallerRunsPolicy} provides natural back-pressure
 * when the queue is also full (the submitting thread runs the task itself).</p>
 *
 * <p>{@code @EnableAsync} lives on {@code OrderServiceApplication}; this class
 * only contributes the bounded executor referenced by {@code @Async("refundExecutor")}.</p>
 */
@Configuration
public class RefundAsyncConfig {

    @Value("${refund.executor.core-pool-size:4}")
    private int corePoolSize;

    @Value("${refund.executor.max-pool-size:8}")
    private int maxPoolSize;

    @Value("${refund.executor.queue-capacity:100}")
    private int queueCapacity;

    @Bean("refundExecutor")
    public Executor refundExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("refund-saga-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
