package com.independentid.scim.test.signals;

import com.independentid.signals.PollStream;
import com.independentid.signals.StreamStatus;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PollStreamFailureTest {

    @Test
    void pollOn403TransitionsToDisabled() throws Exception {
        PollStream stream = newStream();
        stream.maxRetries = 1;

        CloseableHttpClient mockClient = mock(CloseableHttpClient.class);
        CloseableHttpResponse mockResponse = mock(CloseableHttpResponse.class);
        when(mockResponse.getCode()).thenReturn(403);
        when(mockResponse.getReasonPhrase()).thenReturn("Forbidden");
        when(mockClient.execute(any(HttpPost.class))).thenReturn(mockResponse);
        stream.client = mockClient;

        stream.pollEvents(Collections.emptyList(), false, stream.maxRetries);

        assertThat(stream.state.getStatus()).isEqualTo(StreamStatus.DISABLED);
        assertThat(stream.state.getErrorMsg()).contains("403");
    }

    @Test
    void shutdownModeAckCallDoesNotTransitionStateOn5xx() throws Exception {
        PollStream stream = newStream();

        CloseableHttpClient mockClient = mock(CloseableHttpClient.class);
        CloseableHttpResponse mockResponse = mock(CloseableHttpResponse.class);
        when(mockResponse.getCode()).thenReturn(503);
        when(mockResponse.getReasonPhrase()).thenReturn("Service Unavailable");
        when(mockClient.execute(any(HttpPost.class))).thenReturn(mockResponse);
        stream.client = mockClient;

        // retries=0 signals shutdown-mode best-effort ack
        stream.pollEvents(Collections.singletonList("jti-1"), true, 0);

        assertThat(stream.state.getStatus()).isEqualTo(StreamStatus.ENABLED);
    }

    @Test
    void shutdownModeAckCallDoesNotTransitionStateOn403() throws Exception {
        PollStream stream = newStream();

        CloseableHttpClient mockClient = mock(CloseableHttpClient.class);
        CloseableHttpResponse mockResponse = mock(CloseableHttpResponse.class);
        when(mockResponse.getCode()).thenReturn(403);
        when(mockResponse.getReasonPhrase()).thenReturn("Forbidden");
        when(mockClient.execute(any(HttpPost.class))).thenReturn(mockResponse);
        stream.client = mockClient;

        stream.pollEvents(Collections.singletonList("jti-1"), true, 0);

        assertThat(stream.state.getStatus()).isEqualTo(StreamStatus.ENABLED);
    }

    private PollStream newStream() {
        PollStream stream = new PollStream();
        stream.endpointUrl = "https://example.com/poll";
        stream.authorization = "NONE";
        stream.iss = "test-issuer";
        stream.aud = "test-audience";
        stream.maxRetries = 0;
        stream.initialDelay = 0;
        stream.maxDelay = 0;
        return stream;
    }
}
