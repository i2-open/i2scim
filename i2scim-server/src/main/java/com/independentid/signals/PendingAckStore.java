package com.independentid.signals;

import java.time.Instant;
import java.util.List;

/**
 * SPI for durable storage of poll-side acknowledgements awaiting delivery to the
 * SSF transmitter. Backends: {@link MongoPendingAckStore} for Mongo-backed
 * deployments and {@link FilePendingAckStore} for memory-backed deployments.
 *
 * <p>All operations are scoped by {@code streamId}. The {@code (streamId, jti)}
 * pair is the natural key — implementations MUST treat repeated enqueues as
 * idempotent (upsert). Records are removed on successful ack delivery via
 * {@link #delete(String, String)}.
 */
public interface PendingAckStore {

    /**
     * Persist a pending ack. Idempotent on {@code (streamId, jti)}: a second
     * call with the same key replaces the row in place.
     */
    void enqueue(String streamId, String jti, Instant appliedAt);

    /**
     * Return all pending acks for {@code streamId}. Order is unspecified — the
     * SSF poll request body carries the JTI list as a set, not a sequence.
     */
    List<PendingAckRecord> peekAll(String streamId);

    /**
     * Remove the row keyed by {@code (streamId, jti)}. Called after the
     * transmitter accepts the ack with a 2xx response.
     */
    void delete(String streamId, String jti);

    /** Count of pending acks for {@code streamId}. */
    long count(String streamId);
}
