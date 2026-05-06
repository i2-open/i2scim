package com.independentid.signals;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

public sealed interface FailureClassification {

    record Success() implements FailureClassification {}

    record Transport(IOException cause) implements FailureClassification {}

    record Server5xx(int code, String reason) implements FailureClassification {}

    record Unauthorized401(String reason) implements FailureClassification {}

    record Forbidden403(String reason) implements FailureClassification {}

    record RateLimited429(int code, String reason, Optional<Duration> retryAfter) implements FailureClassification {}

    record OtherClient4xx(int code, String body) implements FailureClassification {}
}
