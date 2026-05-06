package com.independentid.signals;

import java.time.Duration;

/**
 * Config for {@link ElapsedTimeRetryStrategy}. PRD-B replaces PRD-A's
 * attempt-count caps for transport/5xx failures with an elapsed-time budget
 * (operations.md {@code RETRY_LIMIT}). The legacy attempt-count cap is kept
 * as a deprecated overlay: when {@code legacyAttemptCap > 0}, retries also
 * stop once the cap is reached, even if the elapsed budget has time left.
 *
 * @param elapsedLimit            transport/5xx total recovery budget; when
 *                                {@code now - queuedAt >= elapsedLimit} the
 *                                stream is disabled
 * @param transportInitialDelay   exp-backoff initial delay
 * @param transportMaxDelay       exp-backoff cap (per-attempt sleep ceiling)
 * @param legacyAttemptCap        deprecated PRD-A {@code pubRetryMax} overlay;
 *                                {@code 0} (or negative) disables the overlay
 * @param unauthorizedRetryMax    401 attempt-count cap (unchanged from PRD-A —
 *                                401 is not subject to elapsed-time semantics)
 * @param unauthorizedDelay       fixed per-401 sleep
 */
public record ElapsedRetryConfig(
        Duration elapsedLimit,
        Duration transportInitialDelay,
        Duration transportMaxDelay,
        int legacyAttemptCap,
        int unauthorizedRetryMax,
        Duration unauthorizedDelay
) {
}
