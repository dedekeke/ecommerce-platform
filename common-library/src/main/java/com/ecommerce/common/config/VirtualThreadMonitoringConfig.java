package com.ecommerce.common.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

/**
 * Virtual Thread Monitoring Configuration
 *
 * This configuration sets up monitoring for virtual threads to detect:
 * - Thread pinning (when a virtual thread pins its carrier thread)
 * - Thread count metrics
 * - Performance issues
 *
 * Thread Pinning occurs when:
 * 1. A virtual thread executes a synchronized block
 * 2. A virtual thread calls native methods
 *
 * To avoid pinning:
 * - Replace synchronized blocks with ReentrantLock
 * - Use virtual thread-friendly APIs
 * - Monitor pinning events with JDK Flight Recorder
 */
@Configuration
@EnableScheduling
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnProperty(
        value = "virtual-threads.monitoring.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class VirtualThreadMonitoringConfig {

    private static final Logger logger = LoggerFactory.getLogger(VirtualThreadMonitoringConfig.class);
    private final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

    /**
     * Register metrics for virtual thread monitoring
     * Only registers if MeterRegistry is available (i.e., actuator is enabled)
     */
    @Bean
    public VirtualThreadMonitoringConfig registerVirtualThreadMetrics(MeterRegistry registry) {
        // Total thread count
        Gauge.builder("jvm.threads.total", threadMXBean, ThreadMXBean::getThreadCount)
                .description("Current number of live threads")
                .register(registry);

        // Platform thread count
        Gauge.builder("jvm.threads.platform", this, config -> {
                    long total = threadMXBean.getThreadCount();
                    long virtual = getVirtualThreadCount();
                    return total - virtual;
                })
                .description("Current number of platform threads")
                .register(registry);

        // Virtual thread count
        Gauge.builder("jvm.threads.virtual", this, config -> getVirtualThreadCount())
                .description("Current number of virtual threads")
                .register(registry);

        // Peak thread count
        Gauge.builder("jvm.threads.peak", threadMXBean, ThreadMXBean::getPeakThreadCount)
                .description("Peak number of live threads")
                .register(registry);

        logger.info("Virtual thread metrics registered");

        return this;
    }

    /**
     * Periodically log virtual thread statistics
     */
    @Scheduled(fixedRate = 60000) // Every minute
    public void logVirtualThreadStats() {
        long totalThreads = threadMXBean.getThreadCount();
        long virtualThreads = getVirtualThreadCount();
        long platformThreads = totalThreads - virtualThreads;
        long peakThreads = threadMXBean.getPeakThreadCount();

        logger.info("Thread Statistics - Total: {}, Platform: {}, Virtual: {}, Peak: {}",
                totalThreads, platformThreads, virtualThreads, peakThreads);
    }

    /**
     * Check for potential thread pinning issues
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void checkForThreadPinning() {
        ThreadInfo[] threadInfos = threadMXBean.dumpAllThreads(false, false);
        int pinnedCount = 0;

        for (ThreadInfo info : threadInfos) {
            if (info != null && isVirtualThreadPinned(info)) {
                pinnedCount++;
                logger.warn("Potential thread pinning detected - Thread: {} | State: {} | Lock: {}",
                        info.getThreadName(),
                        info.getThreadState(),
                        info.getLockName());
            }
        }

        if (pinnedCount > 0) {
            logger.warn("Total potentially pinned virtual threads: {}", pinnedCount);
        }
    }

    /**
     * Get the count of virtual threads
     * This is an approximation based on thread naming convention
     */
    private long getVirtualThreadCount() {
        ThreadInfo[] threadInfos = threadMXBean.dumpAllThreads(false, false);
        long count = 0;

        for (ThreadInfo info : threadInfos) {
            if (info != null && isVirtualThread(info)) {
                count++;
            }
        }

        return count;
    }

    /**
     * Check if a thread is a virtual thread based on its name
     */
    private boolean isVirtualThread(ThreadInfo info) {
        String threadName = info.getThreadName();
        // Virtual threads are typically named "" or have specific patterns
        // This is a heuristic and may need adjustment
        return threadName.isEmpty() || threadName.contains("VirtualThread");
    }

    /**
     * Heuristic to detect if a virtual thread might be pinned
     * A virtual thread is potentially pinned if it's in BLOCKED state
     * or waiting on a monitor
     */
    private boolean isVirtualThreadPinned(ThreadInfo info) {
        if (!isVirtualThread(info)) {
            return false;
        }

        Thread.State state = info.getThreadState();
        String lockName = info.getLockName();

        // Potential pinning indicators
        return (state == Thread.State.BLOCKED && lockName != null) ||
               (state == Thread.State.WAITING && lockName != null && lockName.contains("monitor"));
    }
}
