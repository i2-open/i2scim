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
 * {@link PushStream#attemptOnce(String, String)} for each. PRD-B's
 * {@link ElapsedTimeRetryStrategy} (N4) decides backoff vs. terminal
 * disable using elapsed-time caps from each row's {@code queuedAt}; M2's
 * {@link PushFailureClassifier} unchanged.
 *
 * <p>On terminal failure the stream is transitioned to DISABLED via the
 * existing M1 holder and the queue is left intact. PRD-B slice #76: an
 * operator re-enable through the same M1 holder ({@code transitionTo(ENABLED,
 * null)}) wakes the worker — a transition listener registered in {@link #start()}
 * interrupts the idle-sleeping worker thread so the drain resumes promptly,
 * not after the next {@code idleSleepMs} window expires.
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
        // Wake the idle-sleeping worker on operator re-enable. The run() loop
        // treats InterruptedException as a wake-up signal unless shutdown was
        // requested, so re-entering the drain cycle is the natural response.
        stream.state.addTransitionListener((oldS, newS) -> {
            if (newS == StreamStatus.ENABLED && oldS != StreamStatus.ENABLED) {
                Thread cur = this.thread;
                if (cur != null) cur.interrupt();
            }
        });
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
                // Wake-up signal vs. shutdown request: only break when the latter.
                // Listeners on stream.state interrupt this thread to short-circuit
                // the idle sleep when re-enable happens; the loop should resume
                // immediately into the next drain cycle.
                if (shutdown.get() || stream.isShuttingDown()) {
                    Thread.currentThread().interrupt();
                    break;
                }
                // Otherwise: continue — interrupt status is intentionally cleared
                // so the next sleeper.sleep doesn't immediately re-throw.
            } catch (RuntimeException re) {
                if (isInterruptCause(re)) {
                    // Mongo (and other I/O) wrap InterruptedException as a RuntimeException
                    // (e.g. MongoInterruptedException). Treat the same as a direct
                    // InterruptedException: on shutdown break out, otherwise wake up.
                    if (shutdown.get() || stream.isShuttingDown()) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }
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

        ElapsedRetryConfig retryConfig = new ElapsedRetryConfig(
                Duration.ofMillis(stream.pubRetryElapsedLimit),
                Duration.ofMillis(stream.initialDelay),
                Duration.ofMillis(stream.maxDelay),
                stream.maxRetries, // PRD-A pubRetryMax legacy overlay (0 = disabled)
                stream.unauthorizedRetryMax,
                Duration.ofMillis(stream.unauthorizedRetryDelay)
        );

        for (PendingPushRecord record : batch) {
            if (shutdown.get() || stream.isShuttingDown()) return true;
            if (stream.state.getStatus() != StreamStatus.ENABLED) return true;

            AttemptResult result = stream.attemptOnce(record.jti(), record.payload());
            if (result instanceof AttemptResult.Success) {
                logger.info("Drained queued SET jti={} stream={} after {} prior attempts",
                        record.jti(), safeStreamId(), record.attemptCount());
                store.delete(record.streamId(), record.jti());
                continue;
            }
            if (result instanceof AttemptResult.StreamNotEnabled n) {
                logger.warn("Drain paused for jti={} stream={} streamStatus={} reason={}",
                        record.jti(), safeStreamId(), n.status(), n.reason());
                return true; // outer loop sleeps until state recovers
            }
            AttemptResult.Failure f = (AttemptResult.Failure) result;
            int newAttempt = record.attemptCount() + 1;
            store.markAttempted(record.streamId(), record.jti(), clock.instant(), f.errorMsg());

            RetryDecision decision = ElapsedTimeRetryStrategy.decide(
                    f.classification(), newAttempt, record.queuedAt(), clock.instant(), retryConfig);
            switch (decision) {
                case RetryDecision.Disable d -> {
                    logger.error("Push stream {} DISABLED by retry worker after jti={} attempt {}: {}",
                            safeStreamId(), record.jti(), newAttempt, d.reason());
                    stream.state.transitionTo(StreamStatus.DISABLED, d.reason());
                    return true;
                }
                case RetryDecision.SleepThenRetry s -> {
                    logger.warn("Drain attempt {} failed jti={} stream={} classification={} msg={} — retrying in {}ms",
                            newAttempt, record.jti(), safeStreamId(),
                            f.classification().getClass().getSimpleName(), f.errorMsg(),
                            s.delay().toMillis());
                    sleeper.sleep(s.delay().toMillis());
                }
                case RetryDecision.RetryNoCap n -> {
                    logger.warn("Drain attempt {} failed jti={} stream={} classification={} msg={} — retrying (no-cap) in {}ms",
                            newAttempt, record.jti(), safeStreamId(),
                            f.classification().getClass().getSimpleName(), f.errorMsg(),
                            n.delay().toMillis());
                    sleeper.sleep(n.delay().toMillis());
                }
            }
        }
        return true;
    }

    private String safeStreamId() {
        return stream.streamId == null ? "<unset>" : stream.streamId;
    }

    private static boolean isInterruptCause(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof InterruptedException) return true;
        }
        return false;
    }
}
