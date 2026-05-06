package com.independentid.scim.test.signals;

import com.independentid.signals.FailureClassification;
import com.independentid.signals.PushFailureClassifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class PushFailureClassifierTest {

    @Test
    void classify2xxIsSuccess() {
        FailureClassification result = PushFailureClassifier.classify(202, "Accepted", null, null);

        assertThat(result).isInstanceOf(FailureClassification.Success.class);
    }

    @Test
    void classify403IsForbidden() {
        FailureClassification result = PushFailureClassifier.classify(403, "Forbidden", null, null);

        assertThat(result).isInstanceOf(FailureClassification.Forbidden403.class);
        assertThat(((FailureClassification.Forbidden403) result).reason()).isEqualTo("403 Forbidden");
    }

    @Test
    void classify401IsUnauthorized() {
        FailureClassification result = PushFailureClassifier.classify(401, "Unauthorized", null, null);

        assertThat(result).isInstanceOf(FailureClassification.Unauthorized401.class);
        assertThat(((FailureClassification.Unauthorized401) result).reason()).isEqualTo("401 Unauthorized");
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 503, 504})
    void classify5xxIsServer5xx(int code) {
        FailureClassification result = PushFailureClassifier.classify(code, "Server Error", null, null);

        assertThat(result).isInstanceOf(FailureClassification.Server5xx.class);
        FailureClassification.Server5xx s = (FailureClassification.Server5xx) result;
        assertThat(s.code()).isEqualTo(code);
        assertThat(s.reason()).isEqualTo("Server Error");
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 404, 410, 422})
    void classifyOther4xxIsOtherClient4xxWithBody(int code) {
        FailureClassification result = PushFailureClassifier.classify(code, "Bad", "details".getBytes(), null);

        assertThat(result).isInstanceOf(FailureClassification.OtherClient4xx.class);
        FailureClassification.OtherClient4xx c = (FailureClassification.OtherClient4xx) result;
        assertThat(c.code()).isEqualTo(code);
        assertThat(c.body()).isEqualTo("details");
    }

    @Test
    void classify429WithNumericRetryAfterParsesDuration() {
        FailureClassification result = PushFailureClassifier.classify(429, "Too Many Requests", null, "30");

        assertThat(result).isInstanceOf(FailureClassification.RateLimited429.class);
        FailureClassification.RateLimited429 r = (FailureClassification.RateLimited429) result;
        assertThat(r.retryAfter()).contains(Duration.ofSeconds(30));
    }

    @Test
    void classify429WithoutRetryAfterReturnsEmptyOptional() {
        FailureClassification result = PushFailureClassifier.classify(429, "Too Many Requests", null, null);

        assertThat(result).isInstanceOf(FailureClassification.RateLimited429.class);
        FailureClassification.RateLimited429 r = (FailureClassification.RateLimited429) result;
        assertThat(r.retryAfter()).isEmpty();
    }

    @Test
    void classifyIOExceptionIsTransport() {
        IOException cause = new IOException("connection refused");

        FailureClassification result = PushFailureClassifier.classify(cause);

        assertThat(result).isInstanceOf(FailureClassification.Transport.class);
        assertThat(((FailureClassification.Transport) result).cause()).isSameAs(cause);
    }
}
