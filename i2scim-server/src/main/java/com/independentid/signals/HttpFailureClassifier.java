package com.independentid.signals;

import java.io.IOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

final class HttpFailureClassifier {

    private HttpFailureClassifier() {
    }

    static FailureClassification classifyHttp(int httpCode, String reason, byte[] body, String retryAfterHeader) {
        if (httpCode >= 200 && httpCode < 300) {
            return new FailureClassification.Success();
        }
        if (httpCode == 401) {
            return new FailureClassification.Unauthorized401(httpCode + " " + reason);
        }
        if (httpCode == 403) {
            return new FailureClassification.Forbidden403(httpCode + " " + reason);
        }
        if (httpCode == 429) {
            return new FailureClassification.RateLimited429(httpCode, reason, parseRetryAfter(retryAfterHeader));
        }
        if (httpCode >= 500 && httpCode < 600) {
            return new FailureClassification.Server5xx(httpCode, reason);
        }
        if (httpCode >= 400 && httpCode < 500) {
            return new FailureClassification.OtherClient4xx(httpCode, body == null ? "" : new String(body));
        }
        throw new IllegalArgumentException("Unsupported HTTP code: " + httpCode);
    }

    static FailureClassification classifyTransport(IOException cause) {
        return new FailureClassification.Transport(cause);
    }

    static Optional<Duration> parseRetryAfter(String header) {
        if (header == null || header.isBlank()) {
            return Optional.empty();
        }
        String trimmed = header.trim();
        try {
            long seconds = Long.parseLong(trimmed);
            return Optional.of(Duration.ofSeconds(seconds));
        } catch (NumberFormatException ignored) {
            try {
                ZonedDateTime when = ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME);
                Duration delay = Duration.between(ZonedDateTime.now(), when);
                if (delay.isNegative()) {
                    return Optional.of(Duration.ZERO);
                }
                return Optional.of(delay);
            } catch (Exception alsoIgnored) {
                return Optional.empty();
            }
        }
    }
}
