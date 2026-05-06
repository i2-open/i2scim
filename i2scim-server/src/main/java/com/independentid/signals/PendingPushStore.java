package com.independentid.signals;

import java.time.Instant;
import java.util.List;

/**
 * SPI for durable storage of undelivered push events. Backends:
 * {@link MongoPendingPushStore} for Mongo-backed deployments, filesystem-backed
 * parity (slice #74) for memory deployments, and {@link NoOpPendingPushStore}
 * stub used while filesystem parity is pending.
 *
 * <p>All operations are scoped by {@code streamId}. The {@code (streamId, jti)}
 * pair is the natural key — implementations MUST treat repeated enqueues of the
 * same key as idempotent (upsert). Records are removed on successful delivery
 * via {@link #delete(String, String)} or operator-driven purge.
 */
public interface PendingPushStore {

    /**
     * Persist a record. Idempotent on {@code (streamId, jti)}: a second call
     * with the same key replaces the row in place — used when the producer's
     * inline attempt fails and the worker later re-enqueues after a transient
     * write loss, or when a worker drains and finds an existing row.
     */
    void enqueue(PendingPushRecord record);

    /**
     * Return the oldest {@code limit} records for {@code streamId} in the
     * given {@code state}, ordered by {@code queuedAt} ascending.
     */
    List<PendingPushRecord> peekOldest(String streamId, PendingPushState state, int limit);

    /**
     * Increment {@code attemptCount} and stamp {@code lastAttemptAt} +
     * {@code lastError} for the row keyed by {@code (streamId, jti)}. No-op if
     * the row has been deleted concurrently.
     */
    void markAttempted(String streamId, String jti, Instant attemptedAt, String errorMsg);

    /**
     * Move a row from one {@link PendingPushState} to another (e.g., flushing
     * pre-registration buffer to live pending after registration). No-op if
     * the row is already in {@code newState} or has been deleted.
     */
    void transitionState(String streamId, String jti, PendingPushState newState);

    /**
     * Remove the row keyed by {@code (streamId, jti)}. Called by the worker on
     * 2xx push success.
     */
    void delete(String streamId, String jti);

    /**
     * Count of rows for {@code streamId} in the given state. Used by /status,
     * worker drain checks, and storage monitoring.
     */
    long count(String streamId, PendingPushState state);

    /**
     * Sum of {@code bytes} across all states for {@code streamId}. Used by
     * the storage monitor (slice #77) to evaluate threshold breaches.
     */
    long totalBytes(String streamId);
}
