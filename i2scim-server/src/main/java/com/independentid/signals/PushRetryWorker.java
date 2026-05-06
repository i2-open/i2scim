package com.independentid.signals;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Per-stream daemon that drains rows from {@link PendingPushStore} where
 * {@code state == pending}, in queued order, calling
 * {@link PushStream#attemptOnce(String, String)} for each. PRD-A's
 * {@link RetryStrategy} (M6) decides backoff vs. terminal disable; M2's
 * {@link PushFailureClassifier} unchanged. On terminal failure the stream is
 * transitioned to DISABLED via the existing M1 holder and the queue is left
 * intact for operator-driven re-enable (slice #76 covers full DISABLED replay).
 */
public final class PushRetryWorker implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(PushRetryWorker.class);

    /** Test seam — replaces Thread.sleep so virtual-clock tests do not stall. */
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    public static final Sleeper REAL_SLEEPER = Thread::sleep;

    private final PushStream stream;
    private final PendingPushStore store;
    private final Sleeper sleeper;
    private final Clock clock;
    private final int batchSize;
    private final long idleSleepMs;

    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private Thread thread;

    public PushRetryWorker(PushStream stream, PendingPushStore store) {
        this(stream, store, REAL_SLEEPER, Clock.systemUTC(), 32, 1000L);
    }

    public PushRetryWorker(PushStream stream,
                           PendingPushStore store,
                           Sleeper sleeper,
                           Clock clock,
                           int batchSize,
                           long idleSleepMs) {
        this.stream = stream;
        this.store = store;
        this.sleeper = sleeper;
        this.clock = clock;
        this.batchSize = batchSize;
        this.idleSleepMs = idleSleepMs;
    }

    public synchronized void start() {
        if (thread != null && thread.isAlive()) return;
        Thread t = new Thread(this, "push-retry-" + safeStreamId());
        t.setDaemon(true);
        this.thread = t;
        t.start();
    }

    public void shutdown() {
        shutdown.set(true);
        Thread t = this.thread;
        if (t != null) t.interrupt();
    }

    public boolean isShutdown() {
        return shutdown.get();
    }

    @Override
    public void run() {
        logger.info("PushRetryWorker started for stream {}", safeStreamId());
        while (!shutdown.get() && !stream.isShuttingDown()) {
            try {
                boolean didWork = runDrainCycle();
                if (!didWork) {
                    sleeper.sleep(idleSleepMs);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (RuntimeException re) {
                logger.warn("PushRetryWorker drain cycle failed for stream {}: {}",
                        safeStreamId(), re.getMessage(), re);
                try {
                    sleeper.sleep(idleSleepMs);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        logger.info("PushRetryWorker stopped for stream {}", safeStreamId());
    }

    /**
     * One drain cycle. Returns true if any record was attempted (caller should
     * loop again immediately); false if the queue is empty or the stream is
     * not in a state to send (caller should idle-sleep before re-checking).
     * Visible for tests.
     */
    public boolean runDrainCycle() throws InterruptedException {
        if (stream.state.getStatus() != StreamStatus.ENABLED) return false;
        if (stream.streamId == null) return false;

        List<PendingPushRecord> batch = store.peekOldest(stream.streamId, PendingPushState.pending, batchSize);
        if (batch.isEmpty()) return false;

        RetryStrategyConfig retryConfig = new RetryStrategyConfig(
                stream.maxRetries,
                Duration.ofMillis(stream.initialDelay),
                Duration.ofMillis(stream.maxDelay),
                stream.unauthorizedRetryMax,
                Duration.ofMillis(stream.unauthorizedRetryDelay)
        );

        for (PendingPushRecord record : batch) {
            if (shutdown.get() || stream.isShuttingDown()) return true;
            if (stream.state.getStatus() != StreamStatus.ENABLED) return true;

            AttemptResult result = stream.attemptOnce(record.jti(), record.payload());
            if (result instanceof AttemptResult.Success) {
                store.delete(record.streamId(), record.jti());
                continue;
            }
            if (result instanceof AttemptResult.StreamNotEnabled) {
                return true; // outer loop sleeps until state recovers
            }
            AttemptResult.Failure f = (AttemptResult.Failure) result;
            int newAttempt = record.attemptCount() + 1;
            store.markAttempted(record.streamId(), record.jti(), clock.instant(), f.errorMsg());

            RetryDecision decision = RetryStrategy.decide(f.classification(), newAttempt, retryConfig);
            switch (decision) {
                case RetryDecision.Disable d -> {
                    logger.error("Push stream {} DISABLED by retry worker: {}",
                            safeStreamId(), d.reason());
                    stream.state.transitionTo(StreamStatus.DISABLED, d.reason());
                    return true;
                }
                case RetryDecision.SleepThenRetry s -> sleeper.sleep(s.delay().toMillis());
                case RetryDecision.RetryNoCap n -> sleeper.sleep(n.delay().toMillis());
            }
        }
        return true;
    }

    private String safeStreamId() {
        return stream.streamId == null ? "<unset>" : stream.streamId;
    }
}
