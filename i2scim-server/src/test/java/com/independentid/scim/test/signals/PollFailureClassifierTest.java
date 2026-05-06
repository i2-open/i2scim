package com.independentid.scim.test.signals;

import com.independentid.signals.FailureClassification;
import com.independentid.signals.PollFailureClassifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class PollFailureClassifierTest {

    @Test
    void classify2xxIsSuccess() {
        FailureClassification result = PollFailureClassifier.classify(200, "OK", null, null);
        assertThat(result).isInstanceOf(FailureClassification.Success.class);
    }

    @Test
    void classify401IsUnauthorized() {
        FailureClassification result = PollFailureClassifier.classify(401, "Unauthorized", null, null);
        assertThat(result).isInstanceOf(FailureClassification.Unauthorized401.class);
    }

    @Test
    void classify403IsForbidden() {
        FailureClassification result = PollFailureClassifier.classify(403, "Forbidden", null, null);
        assertThat(result).isInstanceOf(FailureClassification.Forbidden403.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 503, 504})
    void classify5xxIsServer5xx(int code) {
        FailureClassification result = PollFailureClassifier.classify(code, "Server Error", null, null);
        assertThat(result).isInstanceOf(FailureClassification.Server5xx.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 404, 410, 422})
    void classifyOther4xxIsOtherClient4xx(int code) {
        FailureClassification result = PollFailureClassifier.classify(code, "Bad", "body".getBytes(), null);
        assertThat(result).isInstanceOf(FailureClassification.OtherClient4xx.class);
    }

    @Test
    void classify429WithRetryAfterParsesDuration() {
        FailureClassification result = PollFailureClassifier.classify(429, "Too Many", null, "30");
        assertThat(result).isInstanceOf(FailureClassification.RateLimited429.class);
        assertThat(((FailureClassification.RateLimited429) result).retryAfter()).contains(Duration.ofSeconds(30));
    }

    @Test
    void classifyIOExceptionIsTransport() {
        FailureClassification result = PollFailureClassifier.classify(new IOException("refused"));
        assertThat(result).isInstanceOf(FailureClassification.Transport.class);
    }
}
