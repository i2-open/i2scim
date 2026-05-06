package com.independentid.scim.test.signals;

import com.independentid.signals.FailureClassification;
import com.independentid.signals.RetryDecision;
import com.independentid.signals.RetryStrategy;
import com.independentid.signals.RetryStrategyConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RetryStrategyTest {

    private final RetryStrategyConfig config = new RetryStrategyConfig(
            10,                          // transportRetryMax
            Duration.ofSeconds(2),       // transportInitialDelay
            Duration.ofMinutes(5),       // transportMaxDelay
            10,                          // unauthorizedRetryMax
            Duration.ofSeconds(15)       // unauthorizedDelay
    );

    @Test
    void forbidden403DisablesImmediately() {
        FailureClassification c = new FailureClassification.Forbidden403("403 Forbidden");

        RetryDecision d = RetryStrategy.decide(c, 0, config);

        assertThat(d).isInstanceOf(RetryDecision.Disable.class);
        assertThat(((RetryDecision.Disable) d).reason()).isEqualTo("403 Forbidden");
    }

    @Test
    void otherClient4xxDisablesImmediately() {
        FailureClassification c = new FailureClassification.OtherClient4xx(404, "not found");

        RetryDecision d = RetryStrategy.decide(c, 0, config);

        assertThat(d).isInstanceOf(RetryDecision.Disable.class);
        assertThat(((RetryDecision.Disable) d).reason()).contains("404").contains("not found");
    }

    @Test
    void unauthorized401UnderCapSleepsForUnauthorizedDelay() {
        FailureClassification c = new FailureClassification.Unauthorized401("401 Unauthorized");

        RetryDecision d = RetryStrategy.decide(c, 5, config);

        assertThat(d).isInstanceOf(RetryDecision.SleepThenRetry.class);
        assertThat(((RetryDecision.SleepThenRetry) d).delay()).isEqualTo(Duration.ofSeconds(15));
    }

    @Test
    void unauthorized401AtCapDisables() {
        FailureClassification c = new FailureClassification.Unauthorized401("401 Unauthorized");

        RetryDecision d = RetryStrategy.decide(c, 10, config);

        assertThat(d).isInstanceOf(RetryDecision.Disable.class);
        assertThat(((RetryDecision.Disable) d).reason()).contains("401").contains("exhausted");
    }

    @Test
    void server5xxExpBackoffGrowsWithAttempts() {
        FailureClassification c = new FailureClassification.Server5xx(503, "Service Unavailable");

        Duration d0 = ((RetryDecision.SleepThenRetry) RetryStrategy.decide(c, 0, config)).delay();
        Duration d1 = ((RetryDecision.SleepThenRetry) RetryStrategy.decide(c, 1, config)).delay();
        Duration d2 = ((RetryDecision.SleepThenRetry) RetryStrategy.decide(c, 2, config)).delay();

        assertThat(d0).isEqualTo(Duration.ofSeconds(2));
        assertThat(d1).isEqualTo(Duration.ofSeconds(4));
        assertThat(d2).isEqualTo(Duration.ofSeconds(8));
    }

    @Test
    void server5xxExpBackoffCapsAtMaxDelay() {
        FailureClassification c = new FailureClassification.Server5xx(503, "Service Unavailable");

        RetryDecision d = RetryStrategy.decide(c, 8, config);

        assertThat(d).isInstanceOf(RetryDecision.SleepThenRetry.class);
        assertThat(((RetryDecision.SleepThenRetry) d).delay()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void server5xxAboveAttemptCapDisables() {
        FailureClassification c = new FailureClassification.Server5xx(503, "Service Unavailable");

        RetryDecision d = RetryStrategy.decide(c, 10, config);

        assertThat(d).isInstanceOf(RetryDecision.Disable.class);
        assertThat(((RetryDecision.Disable) d).reason()).contains("transport").contains("exceeded");
    }

    @Test
    void transportFailureBackoffMirrorsServer5xx() {
        FailureClassification c = new FailureClassification.Transport(new IOException("connection refused"));

        Duration d0 = ((RetryDecision.SleepThenRetry) RetryStrategy.decide(c, 0, config)).delay();
        RetryDecision dCap = RetryStrategy.decide(c, 10, config);

        assertThat(d0).isEqualTo(Duration.ofSeconds(2));
        assertThat(dCap).isInstanceOf(RetryDecision.Disable.class);
    }

    @Test
    void rateLimited429WithRetryAfterReturnsNoCap() {
        FailureClassification c = new FailureClassification.RateLimited429(429, "Too Many", Optional.of(Duration.ofSeconds(45)));

        RetryDecision d = RetryStrategy.decide(c, 0, config);

        assertThat(d).isInstanceOf(RetryDecision.RetryNoCap.class);
        assertThat(((RetryDecision.RetryNoCap) d).delay()).isEqualTo(Duration.ofSeconds(45));
    }

    @Test
    void rateLimited429WithoutRetryAfterUsesTransportInitialDelay() {
        FailureClassification c = new FailureClassification.RateLimited429(429, "Too Many", Optional.empty());

        RetryDecision d = RetryStrategy.decide(c, 0, config);

        assertThat(d).isInstanceOf(RetryDecision.RetryNoCap.class);
        assertThat(((RetryDecision.RetryNoCap) d).delay()).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void rateLimited429NeverDisablesEvenAtHighAttemptCount() {
        FailureClassification c = new FailureClassification.RateLimited429(429, "Too Many", Optional.empty());

        RetryDecision d = RetryStrategy.decide(c, 10000, config);

        assertThat(d).isInstanceOf(RetryDecision.RetryNoCap.class);
    }
}
