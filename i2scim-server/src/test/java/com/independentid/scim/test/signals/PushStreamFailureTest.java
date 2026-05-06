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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PushStreamFailureTest {

    @Test
    void pushEventOn403TransitionsToDisabledWithReasonInErrorMsg() throws Exception {
        PushStream stream = new PushStream();
        stream.endpointUrl = "https://example.com/events";
        stream.authorization = "NONE";
        stream.iss = "test-issuer";
        stream.aud = "test-audience";
        stream.issuerKey = null; // unsigned mode: JWS uses NONE algorithm
        stream.maxRetries = 0;

        CloseableHttpClient mockClient = mock(CloseableHttpClient.class);
        CloseableHttpResponse mockResponse = mock(CloseableHttpResponse.class);
        when(mockResponse.getCode()).thenReturn(403);
        when(mockResponse.getReasonPhrase()).thenReturn("Forbidden");
        when(mockClient.execute(any(HttpPost.class))).thenReturn(mockResponse);
        stream.client = mockClient;

        SecurityEventToken event = new SecurityEventToken();

        boolean pushed = stream.pushEvent(event);

        assertThat(pushed).isFalse();
        assertThat(stream.state.getStatus()).isEqualTo(StreamStatus.DISABLED);
        assertThat(stream.state.getErrorMsg()).contains("403").contains("Forbidden");
    }

    @Test
    void pushEventOn401ExhaustsUnauthorizedRetriesThenDisables() throws Exception {
        PushStream stream = newStream();
        stream.unauthorizedRetryMax = 2;
        stream.unauthorizedRetryDelay = 0;

        CloseableHttpClient mockClient = mock(CloseableHttpClient.class);
        CloseableHttpResponse mockResponse = mock(CloseableHttpResponse.class);
        when(mockResponse.getCode()).thenReturn(401);
        when(mockResponse.getReasonPhrase()).thenReturn("Unauthorized");
        when(mockClient.execute(any(HttpPost.class))).thenReturn(mockResponse);
        stream.client = mockClient;

        boolean pushed = stream.pushEvent(new SecurityEventToken());

        assertThat(pushed).isFalse();
        assertThat(stream.state.getStatus()).isEqualTo(StreamStatus.DISABLED);
        assertThat(stream.state.getErrorMsg()).contains("401").contains("exhausted");
        verify(mockClient, times(stream.unauthorizedRetryMax + 1)).execute(any(HttpPost.class));
    }

    @Test
    void pushEventOn5xxExhaustsTransportRetriesThenDisables() throws Exception {
        PushStream stream = newStream();
        stream.maxRetries = 2;
        stream.initialDelay = 0;
        stream.maxDelay = 0;

        CloseableHttpClient mockClient = mock(CloseableHttpClient.class);
        CloseableHttpResponse mockResponse = mock(CloseableHttpResponse.class);
        when(mockResponse.getCode()).thenReturn(503);
        when(mockResponse.getReasonPhrase()).thenReturn("Service Unavailable");
        when(mockClient.execute(any(HttpPost.class))).thenReturn(mockResponse);
        stream.client = mockClient;

        boolean pushed = stream.pushEvent(new SecurityEventToken());

        assertThat(pushed).isFalse();
        assertThat(stream.state.getStatus()).isEqualTo(StreamStatus.DISABLED);
        assertThat(stream.state.getErrorMsg()).contains("transport").contains("exceeded");
        verify(mockClient, times(stream.maxRetries + 1)).execute(any(HttpPost.class));
    }

    private PushStream newStream() {
        PushStream stream = new PushStream();
        stream.endpointUrl = "https://example.com/events";
        stream.authorization = "NONE";
        stream.iss = "test-issuer";
        stream.aud = "test-audience";
        stream.issuerKey = null;
        stream.maxRetries = 0;
        return stream;
    }
}
