package com.independentid.signals;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Stub used when the runtime backend is the in-memory provider. Filesystem-backed
 * parity is the responsibility of slice #74 — until then, this implementation
 * silently drops queue operations and warns once on first use so that a memory
 * deployment that exercises the push retry path still starts cleanly.
 *
 * <p>TODO(slice #74): replace with FilePendingPushStore writing under
 * {@code <scim.prov.memory.dir>/events/{pending,preregister}/<streamId>/<jti>.set}.
 */
public final class NoOpPendingPushStore implements PendingPushStore {

    private static final Logger logger = LoggerFactory.getLogger(NoOpPendingPushStore.class);

    private final AtomicBoolean warned = new AtomicBoolean(false);

    private void warnOnce() {
        if (warned.compareAndSet(false, true)) {
            logger.warn("PendingPushStore=NoOp (memory backend). Undelivered events are NOT durable until slice #74 ships.");
        }
    }

    @Override
    public void enqueue(PendingPushRecord record) {
        warnOnce();
    }

    @Override
    public List<PendingPushRecord> peekOldest(String streamId, PendingPushState state, int limit) {
        return Collections.emptyList();
    }

    @Override
    public void markAttempted(String streamId, String jti, Instant attemptedAt, String errorMsg) {
        warnOnce();
    }

    @Override
    public void transitionState(String streamId, String jti, PendingPushState newState) {
        warnOnce();
    }

    @Override
    public void delete(String streamId, String jti) {
        // no-op
    }

    @Override
    public long count(String streamId, PendingPushState state) {
        return 0L;
    }

    @Override
    public long totalBytes(String streamId) {
        return 0L;
    }
}
