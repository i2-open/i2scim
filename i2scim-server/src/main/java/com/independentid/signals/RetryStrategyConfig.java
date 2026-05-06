package com.independentid.signals;

import java.time.Duration;

public record RetryStrategyConfig(
        int transportRetryMax,
        Duration transportInitialDelay,
        Duration transportMaxDelay,
        int unauthorizedRetryMax,
        Duration unauthorizedDelay
) {
}
