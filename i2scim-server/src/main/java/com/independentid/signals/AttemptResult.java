package com.independentid.signals;

/**
 * Outcome of a single push attempt. Returned by {@link PushStream#attemptOnce}
 * and consumed by both the producer's inline path ({@code SignalsEventHandler.publish})
 * and the per-stream {@link PushRetryWorker}. Producer treats anything other
 * than {@link Success} as "enqueue and move on"; the worker drives retry/disable
 * decisions through the existing {@link RetryStrategy}.
 */
public sealed interface AttemptResult {

    record Success() implements AttemptResult {}

    record Failure(FailureClassification classification, String errorMsg) implements AttemptResult {}

    /**
     * The stream is not in a state that allows sending: shutting down, endpoint
     * not yet configured, or a remote /status probe transitioned the stream to
     * PAUSED or DISABLED while attemptOnce was running. The producer enqueues;
     * the worker stops draining and waits for the next tick.
     */
    record StreamNotEnabled(StreamStatus status, String reason) implements AttemptResult {}
}
