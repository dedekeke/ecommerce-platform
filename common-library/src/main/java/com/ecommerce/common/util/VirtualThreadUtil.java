package com.ecommerce.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Utility class for working with Virtual Threads in Java 21+
 *
 * This class provides helper methods for:
 * - Creating and managing virtual threads
 * - Executing tasks concurrently with virtual threads
 * - Handling timeouts and exceptions
 * - Monitoring virtual thread performance
 *
 * Best Practices:
 * - Use virtual threads for I/O-bound operations
 * - Avoid synchronized blocks (use ReentrantLock instead)
 * - Don't use virtual threads for CPU-intensive tasks
 * - Monitor for thread pinning
 */
public class VirtualThreadUtil {

    private static final Logger logger = LoggerFactory.getLogger(VirtualThreadUtil.class);

    private VirtualThreadUtil() {
        // Utility class - prevent instantiation
    }

    /**
     * Execute a task asynchronously using a virtual thread
     *
     * @param task the task to execute
     * @param <T>  the return type
     * @return CompletableFuture containing the result
     */
    public static <T> CompletableFuture<T> executeAsync(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, Executors.newVirtualThreadPerTaskExecutor());
    }

    /**
     * Execute a task asynchronously using a virtual thread with timeout
     *
     * @param task    the task to execute
     * @param timeout the maximum time to wait
     * @param <T>     the return type
     * @return the result of the task
     * @throws TimeoutException     if the task doesn't complete within timeout
     * @throws ExecutionException   if the task throws an exception
     * @throws InterruptedException if the thread is interrupted
     */
    public static <T> T executeWithTimeout(Supplier<T> task, Duration timeout)
            throws TimeoutException, ExecutionException, InterruptedException {
        CompletableFuture<T> future = executeAsync(task);
        return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * Execute multiple tasks concurrently using virtual threads
     * This is ideal for parallel I/O operations like multiple database queries
     *
     * @param tasks list of tasks to execute
     * @param <T>   the return type
     * @return list of results in the same order as tasks
     */
    public static <T> List<T> executeAllConcurrently(List<Supplier<T>> tasks) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<T>> futures = tasks.stream()
                    .map(task -> executor.submit(task::get))
                    .toList();

            return futures.stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (InterruptedException | ExecutionException e) {
                            logger.error("Error executing concurrent task", e);
                            throw new RuntimeException("Error executing concurrent task", e);
                        }
                    })
                    .toList();
        }
    }

    /**
     * Execute multiple tasks concurrently with a timeout
     *
     * @param tasks   list of tasks to execute
     * @param timeout maximum time to wait for all tasks
     * @param <T>     the return type
     * @return list of results
     * @throws TimeoutException if any task doesn't complete within timeout
     */
    public static <T> List<T> executeAllWithTimeout(List<Supplier<T>> tasks, Duration timeout)
            throws TimeoutException, ExecutionException, InterruptedException {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<T>> futures = tasks.stream()
                    .map(task -> executor.submit(task::get))
                    .toList();

            List<T> results = new CopyOnWriteArrayList<>();
            long deadline = System.currentTimeMillis() + timeout.toMillis();

            for (Future<T> future : futures) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    throw new TimeoutException("Tasks did not complete within timeout");
                }
                results.add(future.get(remaining, TimeUnit.MILLISECONDS));
            }

            return results;
        }
    }

    /**
     * Check if the current thread is a virtual thread
     *
     * @return true if running on a virtual thread
     */
    public static boolean isVirtualThread() {
        return Thread.currentThread().isVirtual();
    }

    /**
     * Log current thread information (useful for debugging)
     */
    public static void logThreadInfo(String context) {
        Thread currentThread = Thread.currentThread();
        logger.debug("Context: {} | Thread: {} | Virtual: {} | ID: {}",
                context,
                currentThread.getName(),
                currentThread.isVirtual(),
                currentThread.threadId());
    }

    /**
     * Execute a runnable task on a virtual thread
     *
     * @param task the task to execute
     * @return the started Thread
     */
    public static Thread startVirtualThread(Runnable task) {
        return Thread.startVirtualThread(task);
    }

    /**
     * Create a virtual thread builder for more control over thread creation
     *
     * @param name the name prefix for threads
     * @return Thread.Builder for virtual threads
     */
    public static Thread.Builder.OfVirtual virtualThreadBuilder(String name) {
        return Thread.ofVirtual().name(name, 0);
    }
}
