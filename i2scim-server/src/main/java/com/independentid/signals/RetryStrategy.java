package com.independentid.signals;

import java.time.Duration;

public final class RetryStrategy {

    private RetryStrategy() {
    }

    public static RetryDecision decide(FailureClassification c, int attemptCount, RetryStrategyConfig config) {
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
            case FailureClassification.Server5xx s -> transportLikeBackoff(attemptCount, config);
            case FailureClassification.Transport t -> transportLikeBackoff(attemptCount, config);
            case FailureClassification.RateLimited429 r -> {
                Duration delay = r.retryAfter().orElseGet(() -> expBackoffDelay(attemptCount, config));
                yield new RetryDecision.RetryNoCap(delay);
            }
        };
    }

    private static RetryDecision transportLikeBackoff(int attemptCount, RetryStrategyConfig config) {
        if (attemptCount >= config.transportRetryMax()) {
            return new RetryDecision.Disable("transport recovery exceeded "
                    + config.transportRetryMax() + " attempts");
        }
        return new RetryDecision.SleepThenRetry(expBackoffDelay(attemptCount, config));
    }

    private static Duration expBackoffDelay(int attemptCount, RetryStrategyConfig config) {
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
}
