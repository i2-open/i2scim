package com.independentid.scim.test.signals;

import com.independentid.set.SecurityEventToken;
import com.independentid.signals.PushStream;
import com.independentid.signals.StreamStatus;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for the silent-401 bug: every push attempt now updates
 * pushSuccessCount/pushFailureCount/lastPushFailureMessage on PushStream, and
 * those are surfaced through the readiness health endpoint. Asserting on the
 * counters is the stable signal — log capture under JBoss LogManager is brittle.
 */
class PushStreamMetricsTest {

    @Test
    void successfulPushIncrementsSuccessCountAndLeavesFailuresAtZero() throws Exception {
        PushStream s = newStream();
        s.client = mockClient(200, "OK");

        s.attemptOnce(new SecurityEventToken());

        assertThat(s.getPushSuccessCount()).isEqualTo(1);
        assertThat(s.getPushFailureCount()).isZero();
        assertThat(s.getLastPushFailureMessage()).isNull();
        assertThat(s.getLastPushFailureAt()).isNull();
        assertThat(s.state.getStatus()).isEqualTo(StreamStatus.ENABLED);
    }

    @Test
    void failedPushOn401IncrementsFailureCountAndRecordsMessage() throws Exception {
        PushStream s = newStream();
        s.client = mockClient(401, "Unauthorized");

        s.attemptOnce(new SecurityEventToken());

        assertThat(s.getPushSuccessCount()).isZero();
        assertThat(s.getPushFailureCount()).isEqualTo(1);
        assertThat(s.getLastPushFailureMessage()).contains("401").contains("Unauthorized");
        assertThat(s.getLastPushFailureAt()).isNotNull();
    }

    @Test
    void failedPushOn403IncrementsFailureCountAndRecordsMessage() throws Exception {
        PushStream s = newStream();
        s.client = mockClient(403, "Forbidden");

        s.attemptOnce(new SecurityEventToken());

        assertThat(s.getPushFailureCount()).isEqualTo(1);
        assertThat(s.getLastPushFailureMessage()).contains("403").contains("Forbidden");
    }

    @Test
    void countersAccumulateAcrossMultipleAttempts() throws Exception {
        PushStream s = newStream();
        s.client = mockClient(401, "Unauthorized");

        s.attemptOnce(new SecurityEventToken());
        s.attemptOnce(new SecurityEventToken());
        s.attemptOnce(new SecurityEventToken());

        assertThat(s.getPushFailureCount()).isEqualTo(3);
        assertThat(s.getPushSuccessCount()).isZero();
    }

    @Test
    void maskAuthHidesBearerCredential() {
        assertThat(PushStream.maskAuth("Bearer eyJhbGciOiJIUzI1NiJ9.payload.sig"))
                .startsWith("Bearer ")
                .endsWith("…")
                .doesNotContain("payload")
                .doesNotContain("sig");
        assertThat(PushStream.maskAuth("NONE")).isEqualTo("<none>");
        assertThat(PushStream.maskAuth(null)).isEqualTo("<empty>");
        assertThat(PushStream.maskAuth("")).isEqualTo("<empty>");
        // Scheme-less token (rare but possible): mask the leading prefix.
        assertThat(PushStream.maskAuth("abcdefghijkl")).isEqualTo("abcdefgh…");
    }

    private static PushStream newStream() {
        PushStream s = new PushStream();
        s.streamId = "metrics-stream";
        s.endpointUrl = "https://example.com/events";
        s.authorization = "NONE";
        s.iss = "test-issuer";
        s.aud = "test-audience";
        s.issuerKey = null; // unsigned (NONE alg)
        s.maxRetries = 0;
        s.unauthorizedRetryMax = 0;
        s.initialDelay = 0;
        s.maxDelay = 0;
        return s;
    }

    private static CloseableHttpClient mockClient(int code, String reason) throws Exception {
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        CloseableHttpResponse resp = mock(CloseableHttpResponse.class);
        when(resp.getCode()).thenReturn(code);
        when(resp.getReasonPhrase()).thenReturn(reason);
        when(client.execute(any(HttpPost.class))).thenReturn(resp);
        return client;
    }
}
