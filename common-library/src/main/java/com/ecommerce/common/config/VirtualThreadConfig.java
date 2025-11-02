package com.ecommerce.common.config;

import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executors;

/**
 * Virtual Thread Configuration for Java 21+
 *
 * This configuration enables virtual threads for async operations across all services.
 * Virtual threads are lightweight threads that significantly reduce the overhead of
 * blocking operations, making them ideal for I/O-bound tasks.
 *
 * Benefits:
 * - Improved scalability for I/O-bound operations (database calls, REST API calls, etc.)
 * - Reduced memory footprint compared to platform threads
 * - Simplified code - can write blocking code that performs like async code
 *
 * @see <a href="https://openjdk.org/jeps/444">JEP 444: Virtual Threads</a>
 */
@Configuration
@EnableAsync
public class VirtualThreadConfig {

    /**
     * Configure the application task executor to use virtual threads.
     * This executor is used by Spring's @Async annotation and other async operations.
     *
     * @return AsyncTaskExecutor that uses virtual threads
     */
    @Bean(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME)
    public AsyncTaskExecutor applicationTaskExecutor() {
        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
    }

    /**
     * Create a general-purpose virtual thread executor for custom async operations.
     * Use this executor for:
     * - Database query execution
     * - External API calls
     * - File I/O operations
     * - Kafka message processing
     * - gRPC calls
     *
     * @return AsyncTaskExecutor using virtual threads
     */
    @Bean("virtualThreadExecutor")
    public AsyncTaskExecutor virtualThreadExecutor() {
        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
    }

    /**
     * Create a virtual thread executor specifically for I/O operations.
     * This executor should be used for:
     * - HTTP client calls
     * - Database operations
     * - Redis operations
     * - Elasticsearch queries
     *
     * @return AsyncTaskExecutor for I/O operations
     */
    @Bean("ioTaskExecutor")
    public AsyncTaskExecutor ioTaskExecutor() {
        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
    }
}
