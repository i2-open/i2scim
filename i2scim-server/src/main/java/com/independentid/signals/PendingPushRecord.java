package com.independentid.signals;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

public record PendingPushRecord(
        String streamId,
        String jti,
        String payload,
        PendingPushState state,
        Instant queuedAt,
        int attemptCount,
        Instant lastAttemptAt,
        String lastError,
        long bytes
) {
    public static PendingPushRecord ofNew(String streamId, String jti, String payload, PendingPushState state, Instant queuedAt) {
        long size = payload == null ? 0L : payload.getBytes(StandardCharsets.UTF_8).length;
        return new PendingPushRecord(streamId, jti, payload, state, queuedAt, 0, null, null, size);
    }
}
