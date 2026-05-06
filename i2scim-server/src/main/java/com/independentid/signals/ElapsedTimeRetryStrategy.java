package com.independentid.signals;

import java.time.Duration;
import java.time.Instant;

/**
 * PRD-B "N4". Pure function over {@code (FailureClassification, attemptCount,
 * queuedAt, now, config)} replacing PRD-A's attempt-count {@link RetryStrategy}
 * for the push side. operations.md uses an elapsed-time {@code RETRY_LIMIT}
 * (default 6h) for transport/5xx recovery; PRD-A's attempt-count cap is kept
 * as a deprecated {@link ElapsedRetryConfig#legacyAttemptCap()} overlay.
 *
 * <p>Non-transport failure classes preserve PRD-A semantics: 403 / other
 * 4xx / RFC8935 disable immediately; 401 uses an attempt-count cap; 429 is
 * never subject to either cap (Retry-After is authoritative).
 */
public final class ElapsedTimeRetryStrategy {

    private ElapsedTimeRetryStrategy() {
    }

    public static RetryDecision decide(FailureClassification c,
                                       int attemptCount,
                                       Instant queuedAt,
                                       Instant now,
                                       ElapsedRetryConfig config) {
        return switch (c) {
            case FailureClassification.Success ignored ->
                    throw new IllegalArgumentException("Success has no retry decision");
            case FailureClassification.Forbidden403 f -> new RetryDecision.Disable(f.reason());
            case FailureClassification.OtherClient4xx o -> new RetryDecision.Disable(o.code() + " " + o.body());
            case FailureClassification.Unauthorized401 u -> {
                if (attemptCount >= config.unauthorizedRetryMax()) {
                    yield new RetryDecision.Disable("401 attempts exhausted ("
                            + config.unauthorizedRetryMax() + " x " + config.unauthorizedDelay().toMillis() + "ms)");
                }
                yield new RetryDecision.SleepThenRetry(config.unauthorizedDelay());
            }
            case FailureClassification.Server5xx s -> transportLikeBackoff(attemptCount, queuedAt, now, config);
            case FailureClassification.Transport t -> transportLikeBackoff(attemptCount, queuedAt, now, config);
            case FailureClassification.RateLimited429 r -> {
                Duration delay = r.retryAfter().orElseGet(() -> expBackoffDelay(attemptCount, config));
                yield new RetryDecision.RetryNoCap(delay);
            }
            case FailureClassification.Rfc8935 r -> {
                Rfc8935Error err = r.error();
                String suffix = err.jti().map(j -> "; jti=" + j).orElse("");
                String desc = err.description() == null ? "" : ": " + err.description();
                yield new RetryDecision.Disable("RFC8935 " + err.code() + desc + suffix);
            }
        };
    }

    private static RetryDecision transportLikeBackoff(int attemptCount,
                                                      Instant queuedAt,
                                                      Instant now,
                                                      ElapsedRetryConfig config) {
        Duration elapsed = Duration.between(queuedAt, now);
        if (!elapsed.isNegative() && elapsed.compareTo(config.elapsedLimit()) >= 0) {
            return new RetryDecision.Disable("transport recovery exceeded "
                    + formatHours(config.elapsedLimit()));
        }
        if (config.legacyAttemptCap() > 0 && attemptCount >= config.legacyAttemptCap()) {
            return new RetryDecision.Disable("transport recovery exceeded "
                    + config.legacyAttemptCap() + " attempts (legacy pubRetryMax)");
        }
        return new RetryDecision.SleepThenRetry(expBackoffDelay(attemptCount, config));
    }

    private static Duration expBackoffDelay(int attemptCount, ElapsedRetryConfig config) {
        long initialMs = config.transportInitialDelay().toMillis();
        long maxMs = config.transportMaxDelay().toMillis();
        if (attemptCount >= 62) {
            return Duration.ofMillis(maxMs);
        }
        long shifted = initialMs << attemptCount;
        if (shifted < 0 || shifted > maxMs) {
            return Duration.ofMillis(maxMs);
        }
        return Duration.ofMillis(shifted);
    }

    private static String formatHours(Duration d) {
        long h = d.toHours();
        long remainderMin = d.minusHours(h).toMinutes();
        if (remainderMin == 0 && h > 0) return h + "h";
        if (h == 0) return d.toMinutes() + "m";
        return h + "h" + remainderMin + "m";
    }
}
