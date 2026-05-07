package com.independentid.scim.test.signals;

import com.independentid.set.SecurityEventToken;
import com.independentid.signals.AttemptResult;
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

class PushStreamAttemptOnceTest {

    private static PushStream newStream() {
        PushStream s = new PushStream();
        s.streamId = "stream-1";
        s.endpointUrl = "https://example.com/events";
        s.authorization = "NONE";
        s.iss = "test-issuer";
        s.aud = "test-audience";
        s.issuerKey = null; // unsigned
        s.maxRetries = 0;
        s.unauthorizedRetryMax = 0;
        s.initialDelay = 0;
        s.maxDelay = 0;
        return s;
    }

    @Test
    void attemptOnceReturnsSuccessAfterOneHttpCallOn2xx() throws Exception {
        PushStream s = newStream();
        CloseableHttpClient mockClient = mock(CloseableHttpClient.class);
        CloseableHttpResponse mockResponse = mock(CloseableHttpResponse.class);
        when(mockResponse.getCode()).thenReturn(200);
        when(mockResponse.getReasonPhrase()).thenReturn("OK");
        when(mockClient.execute(any(HttpPost.class))).thenReturn(mockResponse);
        s.client = mockClient;

        AttemptResult r = s.attemptOnce(new SecurityEventToken());

        assertThat(r).isInstanceOf(AttemptResult.Success.class);
        verify(mockClient, times(1)).execute(any(HttpPost.class));
        assertThat(s.state.getStatus()).isEqualTo(StreamStatus.ENABLED);
    }

    @Test
    void attemptOnceReturnsFailureOn503AfterOneHttpCall() throws Exception {
        PushStream s = newStream();
        CloseableHttpClient mockClient = mock(CloseableHttpClient.class);
        CloseableHttpResponse mockResponse = mock(CloseableHttpResponse.class);
        when(mockResponse.getCode()).thenReturn(503);
        when(mockResponse.getReasonPhrase()).thenReturn("Service Unavailable");
        when(mockClient.execute(any(HttpPost.class))).thenReturn(mockResponse);
        s.client = mockClient;

        AttemptResult r = s.attemptOnce(new SecurityEventToken());

        assertThat(r).isInstanceOf(AttemptResult.Failure.class);
        AttemptResult.Failure f = (AttemptResult.Failure) r;
        assertThat(f.errorMsg()).contains("503");
        verify(mockClient, times(1)).execute(any(HttpPost.class));
        assertThat(s.state.getStatus()).isEqualTo(StreamStatus.ENABLED); // attemptOnce does not disable
    }

    @Test
    void attemptOnceReturnsStreamNotEnabledWhenStateIsDisabled() throws Exception {
        PushStream s = newStream();
        s.state.transitionTo(StreamStatus.DISABLED, "test setup");
        CloseableHttpClient mockClient = mock(CloseableHttpClient.class);
        s.client = mockClient;

        AttemptResult r = s.attemptOnce(new SecurityEventToken());

        assertThat(r).isInstanceOf(AttemptResult.StreamNotEnabled.class);
        AttemptResult.StreamNotEnabled n = (AttemptResult.StreamNotEnabled) r;
        assertThat(n.status()).isEqualTo(StreamStatus.DISABLED);
        verify(mockClient, times(0)).execute(any(HttpPost.class));
    }

    @Test
    void attemptOnceReturnsStreamNotEnabledWhenEndpointNotSet() throws Exception {
        PushStream s = newStream();
        s.endpointUrl = "NONE";
        CloseableHttpClient mockClient = mock(CloseableHttpClient.class);
        s.client = mockClient;

        AttemptResult r = s.attemptOnce(new SecurityEventToken());

        assertThat(r).isInstanceOf(AttemptResult.StreamNotEnabled.class);
        verify(mockClient, times(0)).execute(any(HttpPost.class));
    }

    @Test
    void attemptOnceDoesNotInvokeRetryStrategy() throws Exception {
        // 503 is the canonical "should-retry" classification — verify that
        // attemptOnce does NOT loop, sleep, or call the retry strategy itself;
        // it returns a single Failure and lets the caller decide.
        PushStream s = newStream();
        s.maxRetries = 99; // would normally retry many times in pushEvent
        s.initialDelay = 100_000; // would normally sleep a long time

        CloseableHttpClient mockClient = mock(CloseableHttpClient.class);
        CloseableHttpResponse mockResponse = mock(CloseableHttpResponse.class);
        when(mockResponse.getCode()).thenReturn(503);
        when(mockResponse.getReasonPhrase()).thenReturn("Service Unavailable");
        when(mockClient.execute(any(HttpPost.class))).thenReturn(mockResponse);
        s.client = mockClient;

        long start = System.nanoTime();
        AttemptResult r = s.attemptOnce(new SecurityEventToken());
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(r).isInstanceOf(AttemptResult.Failure.class);
        verify(mockClient, times(1)).execute(any(HttpPost.class));
        assertThat(elapsedMs).as("attemptOnce must not sleep").isLessThan(1_000);
    }
}
