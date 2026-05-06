package com.independentid.signals;

import java.time.Duration;

public sealed interface RetryDecision {

    record SleepThenRetry(Duration delay) implements RetryDecision {}

    record Disable(String reason) implements RetryDecision {}

    record RetryNoCap(Duration delay) implements RetryDecision {}
}
