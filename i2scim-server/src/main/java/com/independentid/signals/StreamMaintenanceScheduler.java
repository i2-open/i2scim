package com.independentid.signals;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class StreamMaintenanceScheduler {

    private static final Logger logger = LoggerFactory.getLogger(StreamMaintenanceScheduler.class);

    private final ScheduledExecutorService executor;
    private final Map<String, ScheduledFuture<?>> pausedTasks = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> idleTasks = new ConcurrentHashMap<>();

    public StreamMaintenanceScheduler() {
        this(Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "signals-stream-maintenance");
            t.setDaemon(true);
            return t;
        }));
    }

    StreamMaintenanceScheduler(ScheduledExecutorService executor) {
        this.executor = executor;
    }

    public void schedulePausedRecheck(String streamKey, Runnable task, Duration interval) {
        replaceTask(pausedTasks, streamKey, task, interval);
    }

    public void cancelPausedRecheck(String streamKey) {
        cancelTask(pausedTasks, streamKey);
    }

    public void scheduleIdleVerify(String streamKey, Runnable task, Duration interval) {
        replaceTask(idleTasks, streamKey, task, interval);
    }

    public void cancelIdleVerify(String streamKey) {
        cancelTask(idleTasks, streamKey);
    }

    public void shutdown() {
        pausedTasks.values().forEach(f -> f.cancel(false));
        pausedTasks.clear();
        idleTasks.values().forEach(f -> f.cancel(false));
        idleTasks.clear();
        executor.shutdownNow();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private void replaceTask(Map<String, ScheduledFuture<?>> registry, String streamKey, Runnable task, Duration interval) {
        if (executor.isShutdown()) {
            return;
        }
        ScheduledFuture<?> previous = registry.put(streamKey, executor.scheduleAtFixedRate(
                () -> safeRun(task, streamKey),
                interval.toMillis(),
                interval.toMillis(),
                TimeUnit.MILLISECONDS));
        if (previous != null) {
            previous.cancel(false);
        }
    }

    private void cancelTask(Map<String, ScheduledFuture<?>> registry, String streamKey) {
        ScheduledFuture<?> existing = registry.remove(streamKey);
        if (existing != null) {
            existing.cancel(false);
        }
    }

    private void safeRun(Runnable task, String streamKey) {
        try {
            task.run();
        } catch (Throwable t) {
            logger.warn("Maintenance task for stream {} threw {}: {}", streamKey, t.getClass().getSimpleName(), t.getMessage());
        }
    }
}
