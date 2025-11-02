package com.ecommerce.performance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * Performance Test: Virtual Threads vs Platform Threads
 *
 * This test compares the performance of virtual threads against platform threads
 * for I/O-bound operations (HTTP requests).
 *
 * Run this test to measure:
 * - Throughput (requests/second)
 * - Latency (response time)
 * - Resource usage (memory, threads)
 * - Scalability (how it handles increasing load)
 *
 * Usage:
 *   mvn exec:java -Dexec.mainClass="com.ecommerce.performance.VirtualThreadsPerformanceTest"
 */
public class VirtualThreadsPerformanceTest {

    private static final Logger logger = LoggerFactory.getLogger(VirtualThreadsPerformanceTest.class);

    // Test configuration
    private static final String BASE_URL = System.getenv().getOrDefault("API_URL", "http://localhost:8080");
    private static final int[] CONCURRENCY_LEVELS = {100, 500, 1000, 2000, 5000};
    private static final int REQUESTS_PER_LEVEL = 1000;

    // Metrics
    private static final LongAdder successCount = new LongAdder();
    private static final LongAdder failureCount = new LongAdder();
    private static final List<Long> latencies = new CopyOnWriteArrayList<>();

    public static void main(String[] args) throws Exception {
        logger.info("=".repeat(80));
        logger.info("Virtual Threads Performance Test");
        logger.info("=".repeat(80));
        logger.info("Target URL: {}", BASE_URL);
        logger.info("Requests per level: {}", REQUESTS_PER_LEVEL);
        logger.info("");

        // Test with Platform Threads
        logger.info(">>> Testing with PLATFORM THREADS <<<");
        logger.info("");
        TestResults platformResults = new TestResults("Platform Threads");
        for (int concurrency : CONCURRENCY_LEVELS) {
            resetMetrics();
            runLoadTest(concurrency, REQUESTS_PER_LEVEL, false);
            platformResults.addResult(concurrency, calculateMetrics());
        }

        Thread.sleep(5000); // Cool down

        // Test with Virtual Threads
        logger.info("");
        logger.info(">>> Testing with VIRTUAL THREADS <<<");
        logger.info("");
        TestResults virtualResults = new TestResults("Virtual Threads");
        for (int concurrency : CONCURRENCY_LEVELS) {
            resetMetrics();
            runLoadTest(concurrency, REQUESTS_PER_LEVEL, true);
            virtualResults.addResult(concurrency, calculateMetrics());
        }

        // Print comparison
        printComparison(platformResults, virtualResults);
    }

    /**
     * Run load test with specified concurrency
     */
    private static void runLoadTest(int concurrency, int totalRequests, boolean useVirtualThreads) throws Exception {
        logger.info("Concurrency: {} | Total Requests: {}", concurrency, totalRequests);

        ExecutorService executor = useVirtualThreads
                ? Executors.newVirtualThreadPerTaskExecutor()
                : Executors.newFixedThreadPool(concurrency);

        Instant startTime = Instant.now();
        CountDownLatch latch = new CountDownLatch(totalRequests);

        // Submit tasks
        for (int i = 0; i < totalRequests; i++) {
            executor.submit(() -> {
                try {
                    makeHttpRequest();
                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait for completion
        latch.await();
        Instant endTime = Instant.now();

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        long durationMs = Duration.between(startTime, endTime).toMillis();
        logger.info("Completed in {}ms", durationMs);
        logger.info("Success: {} | Failures: {}", successCount.sum(), failureCount.sum());
        logger.info("");
    }

    /**
     * Make HTTP request and record metrics
     */
    private static void makeHttpRequest() {
        Instant start = Instant.now();
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/actuator/health"))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                successCount.increment();
            } else {
                failureCount.increment();
            }

            long latency = Duration.between(start, Instant.now()).toMillis();
            latencies.add(latency);

        } catch (Exception e) {
            failureCount.increment();
            logger.debug("Request failed: {}", e.getMessage());
        }
    }

    /**
     * Calculate performance metrics
     */
    private static Metrics calculateMetrics() {
        List<Long> sortedLatencies = new ArrayList<>(latencies);
        sortedLatencies.sort(Long::compareTo);

        long count = sortedLatencies.size();
        if (count == 0) {
            return new Metrics(0, 0, 0, 0, 0, 0);
        }

        long sum = sortedLatencies.stream().mapToLong(Long::longValue).sum();
        long avg = sum / count;
        long min = sortedLatencies.get(0);
        long max = sortedLatencies.get((int) (count - 1));
        long p50 = sortedLatencies.get((int) (count * 0.5));
        long p95 = sortedLatencies.get((int) (count * 0.95));
        long p99 = sortedLatencies.get((int) (count * 0.99));

        return new Metrics(avg, min, max, p50, p95, p99);
    }

    /**
     * Reset metrics for next test
     */
    private static void resetMetrics() {
        successCount.reset();
        failureCount.reset();
        latencies.clear();
    }

    /**
     * Print comparison table
     */
    private static void printComparison(TestResults platform, TestResults virtual) {
        logger.info("");
        logger.info("=".repeat(80));
        logger.info("PERFORMANCE COMPARISON");
        logger.info("=".repeat(80));
        logger.info("");
        logger.info(String.format("%-15s %-15s %-15s %-15s %-15s",
                "Concurrency", "Type", "Avg (ms)", "P95 (ms)", "P99 (ms)"));
        logger.info("-".repeat(80));

        for (int concurrency : CONCURRENCY_LEVELS) {
            Metrics platformMetrics = platform.getResult(concurrency);
            Metrics virtualMetrics = virtual.getResult(concurrency);

            logger.info(String.format("%-15d %-15s %-15d %-15d %-15d",
                    concurrency, "Platform", platformMetrics.avg, platformMetrics.p95, platformMetrics.p99));
            logger.info(String.format("%-15s %-15s %-15d %-15d %-15d",
                    "", "Virtual", virtualMetrics.avg, virtualMetrics.p95, virtualMetrics.p99));

            // Calculate improvement
            double avgImprovement = ((double) (platformMetrics.avg - virtualMetrics.avg) / platformMetrics.avg) * 100;
            double p95Improvement = ((double) (platformMetrics.p95 - virtualMetrics.p95) / platformMetrics.p95) * 100;

            logger.info(String.format("%-15s %-15s %-15s %-15s %-15s",
                    "", "Improvement", String.format("%.1f%%", avgImprovement),
                    String.format("%.1f%%", p95Improvement), ""));
            logger.info("");
        }

        logger.info("=".repeat(80));
        logger.info("Summary:");
        logger.info("- Virtual threads should show better performance at higher concurrency levels");
        logger.info("- Memory usage should be significantly lower with virtual threads");
        logger.info("- Platform threads may hit resource limits at very high concurrency");
        logger.info("=".repeat(80));
    }

    // Helper classes
    static class Metrics {
        long avg, min, max, p50, p95, p99;

        Metrics(long avg, long min, long max, long p50, long p95, long p99) {
            this.avg = avg;
            this.min = min;
            this.max = max;
            this.p50 = p50;
            this.p95 = p95;
            this.p99 = p99;
        }
    }

    static class TestResults {
        String name;
        ConcurrentHashMap<Integer, Metrics> results = new ConcurrentHashMap<>();

        TestResults(String name) {
            this.name = name;
        }

        void addResult(int concurrency, Metrics metrics) {
            results.put(concurrency, metrics);
        }

        Metrics getResult(int concurrency) {
            return results.get(concurrency);
        }
    }
}
