package com.independentid.scim.test.signals;

import com.independentid.signals.ElapsedRetryConfig;
import com.independentid.signals.ElapsedTimeRetryStrategy;
import com.independentid.signals.FailureClassification;
import com.independentid.signals.RetryDecision;
import com.independentid.signals.Rfc8935Error;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PRD-B slice #75 — elapsed-time retry caps. Strategy is a pure function over
 * {@code (FailureClassification, attemptCount, queuedAt, now, config)}.
 */
class ElapsedTimeRetryStrategyTest {

    private static final Instant T0 = Instant.parse("2026-05-06T10:00:00Z");

    /** 6h elapsed cap, no legacy attempt-count overlay. */
    private final ElapsedRetryConfig defaults = new ElapsedRetryConfig(
            Duration.ofHours(6),         // elapsedLimit (operations.md RETRY_LIMIT)
            Duration.ofSeconds(2),       // transportInitialDelay
            Duration.ofMinutes(5),       // transportMaxDelay
            0,                           // legacyAttemptCap (0 = disabled)
            10,                          // unauthorizedRetryMax
            Duration.ofSeconds(15)       // unauthorizedDelay
    );

    @Test
    void transportFailureDisablesWhenElapsedLimitExceeded() {
        FailureClassification c = new FailureClassification.Server5xx(503, "Service Unavailable");
        Instant now = T0.plus(Duration.ofHours(6).plusSeconds(1));

        RetryDecision d = ElapsedTimeRetryStrategy.decide(c, 1, T0, now, defaults);

        assertThat(d).isInstanceOf(RetryDecision.Disable.class);
        assertThat(((RetryDecision.Disable) d).reason()).contains("transport recovery exceeded 6h");
    }

    @Test
    void transportFailureUnderElapsedLimitSleepsWithExpBackoff() {
        FailureClassification c = new FailureClassification.Server5xx(503, "Service Unavailable");
        Instant now = T0.plusSeconds(10); // way under 6h

        Duration d0 = ((RetryDecision.SleepThenRetry) ElapsedTimeRetryStrategy.decide(c, 0, T0, now, defaults)).delay();
        Duration d1 = ((RetryDecision.SleepThenRetry) ElapsedTimeRetryStrategy.decide(c, 1, T0, now, defaults)).delay();
        Duration d2 = ((RetryDecision.SleepThenRetry) ElapsedTimeRetryStrategy.decide(c, 2, T0, now, defaults)).delay();

        assertThat(d0).isEqualTo(Duration.ofSeconds(2));
        assertThat(d1).isEqualTo(Duration.ofSeconds(4));
        assertThat(d2).isEqualTo(Duration.ofSeconds(8));
    }

    @Test
    void transportFailureExpBackoffCapsAtMaxDelay() {
        FailureClassification c = new FailureClassification.Transport(new IOException("connection refused"));
        Instant now = T0.plus(Duration.ofMinutes(1));

        RetryDecision d = ElapsedTimeRetryStrategy.decide(c, 12, T0, now, defaults);

        assertThat(d).isInstanceOf(RetryDecision.SleepThenRetry.class);
        assertThat(((RetryDecision.SleepThenRetry) d).delay()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void legacyAttemptCapDisablesWhenAttemptCountReachesIt() {
        ElapsedRetryConfig withLegacyCap = new ElapsedRetryConfig(
                Duration.ofHours(6), Duration.ofSeconds(2), Duration.ofMinutes(5),
                3, // legacy attempt cap (PRD-A pubRetryMax overlay)
                10, Duration.ofSeconds(15));
        FailureClassification c = new FailureClassification.Server5xx(503, "Service Unavailable");
        Instant now = T0.plus(Duration.ofMinutes(1)); // way under 6h

        // attemptCount = 2: under cap, sleeps
        RetryDecision under = ElapsedTimeRetryStrategy.decide(c, 2, T0, now, withLegacyCap);
        assertThat(under).isInstanceOf(RetryDecision.SleepThenRetry.class);

        // attemptCount = 3: at cap, disables (with legacy-flavoured reason)
        RetryDecision atCap = ElapsedTimeRetryStrategy.decide(c, 3, T0, now, withLegacyCap);
        assertThat(atCap).isInstanceOf(RetryDecision.Disable.class);
        assertThat(((RetryDecision.Disable) atCap).reason())
                .contains("3").contains("attempts").contains("legacy");
    }

    @Test
    void legacyAttemptCapZeroIsTreatedAsDisabled() {
        FailureClassification c = new FailureClassification.Server5xx(503, "Service Unavailable");
        Instant now = T0.plus(Duration.ofMinutes(1));

        // High attempt count + legacyAttemptCap=0 → still SleepThenRetry (only elapsed cap matters)
        RetryDecision d = ElapsedTimeRetryStrategy.decide(c, 1000, T0, now, defaults);

        assertThat(d).isInstanceOf(RetryDecision.SleepThenRetry.class);
    }

    @Test
    void elapsedLimitTakesPrecedenceOverLegacyAttemptCap() {
        ElapsedRetryConfig bothCaps = new ElapsedRetryConfig(
                Duration.ofHours(6), Duration.ofSeconds(2), Duration.ofMinutes(5),
                100, 10, Duration.ofSeconds(15));
        FailureClassification c = new FailureClassification.Server5xx(503, "Service Unavailable");
        Instant now = T0.plus(Duration.ofHours(7));

        // Elapsed exceeded, attempt count tiny → elapsed reason wins
        RetryDecision d = ElapsedTimeRetryStrategy.decide(c, 5, T0, now, bothCaps);
        assertThat(d).isInstanceOf(RetryDecision.Disable.class);
        assertThat(((RetryDecision.Disable) d).reason()).contains("transport recovery exceeded 6h");
    }

    @Test
    void transportFailureExactlyAtElapsedLimitDisables() {
        FailureClassification c = new FailureClassification.Server5xx(503, "Service Unavailable");
        Instant now = T0.plus(Duration.ofHours(6));

        RetryDecision d = ElapsedTimeRetryStrategy.decide(c, 0, T0, now, defaults);

        assertThat(d).isInstanceOf(RetryDecision.Disable.class);
    }

    @Test
    void forbidden403DisablesImmediatelyRegardlessOfElapsedTime() {
        FailureClassification c = new FailureClassification.Forbidden403("403 Forbidden");
        Instant now = T0.plusSeconds(1);

        RetryDecision d = ElapsedTimeRetryStrategy.decide(c, 0, T0, now, defaults);

        assertThat(d).isInstanceOf(RetryDecision.Disable.class);
        assertThat(((RetryDecision.Disable) d).reason()).isEqualTo("403 Forbidden");
    }

    @Test
    void otherClient4xxDisablesImmediately() {
        FailureClassification c = new FailureClassification.OtherClient4xx(404, "not found");

        RetryDecision d = ElapsedTimeRetryStrategy.decide(c, 0, T0, T0, defaults);

        assertThat(d).isInstanceOf(RetryDecision.Disable.class);
        assertThat(((RetryDecision.Disable) d).reason()).contains("404").contains("not found");
    }

    @Test
    void rfc8935DisablesImmediately() {
        Rfc8935Error err = new Rfc8935Error("invalid_key", "key not trusted", Optional.of("the-jti"));
        FailureClassification c = new FailureClassification.Rfc8935(err);

        RetryDecision d = ElapsedTimeRetryStrategy.decide(c, 0, T0, T0, defaults);

        assertThat(d).isInstanceOf(RetryDecision.Disable.class);
        assertThat(((RetryDecision.Disable) d).reason())
                .contains("RFC8935 invalid_key").contains("key not trusted").contains("the-jti");
    }

    @Test
    void unauthorized401UsesAttemptCountCapNotElapsedCap() {
        FailureClassification c = new FailureClassification.Unauthorized401("401 Unauthorized");
        Instant under = T0.plus(Duration.ofHours(7));

        // Even past elapsed cap, 401 below attempt cap retries — 401 is operator-recoverable
        // (key/trust fix) and operations.md does not include it in the 6h transport budget.
        RetryDecision under401 = ElapsedTimeRetryStrategy.decide(c, 5, T0, under, defaults);
        assertThat(under401).isInstanceOf(RetryDecision.SleepThenRetry.class);
        assertThat(((RetryDecision.SleepThenRetry) under401).delay()).isEqualTo(Duration.ofSeconds(15));

        RetryDecision atCap = ElapsedTimeRetryStrategy.decide(c, 10, T0, under, defaults);
        assertThat(atCap).isInstanceOf(RetryDecision.Disable.class);
        assertThat(((RetryDecision.Disable) atCap).reason()).contains("401").contains("exhausted");
    }

    @Test
    void rateLimited429ReturnsRetryNoCapEvenPastElapsedLimit() {
        FailureClassification c = new FailureClassification.RateLimited429(
                429, "Too Many", Optional.of(Duration.ofSeconds(45)));
        Instant farFuture = T0.plus(Duration.ofDays(1));

        RetryDecision d = ElapsedTimeRetryStrategy.decide(c, 0, T0, farFuture, defaults);

        assertThat(d).isInstanceOf(RetryDecision.RetryNoCap.class);
        assertThat(((RetryDecision.RetryNoCap) d).delay()).isEqualTo(Duration.ofSeconds(45));
    }

    @Test
    void rateLimited429WithoutRetryAfterUsesTransportInitialDelay() {
        FailureClassification c = new FailureClassification.RateLimited429(429, "Too Many", Optional.empty());

        RetryDecision d = ElapsedTimeRetryStrategy.decide(c, 0, T0, T0, defaults);

        assertThat(d).isInstanceOf(RetryDecision.RetryNoCap.class);
        assertThat(((RetryDecision.RetryNoCap) d).delay()).isEqualTo(Duration.ofSeconds(2));
    }
}
