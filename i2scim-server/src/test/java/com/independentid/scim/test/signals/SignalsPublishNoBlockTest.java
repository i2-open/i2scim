package com.independentid.scim.test.signals;

import com.independentid.set.SecurityEventToken;
import com.independentid.signals.PendingPushRecord;
import com.independentid.signals.PendingPushState;
import com.independentid.signals.PendingPushStore;
import com.independentid.signals.PushStream;
import com.independentid.signals.SignalsEventHandler;
import com.independentid.signals.StreamStatus;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the slice #73 invariant: the producer thread completes a single
 * inline {@link PushStream#attemptOnce} and, on any non-2xx result, enqueues
 * to the {@link PendingPushStore} and returns. It must not block, throw, or
 * drop — even when the receiver is hard-failing.
 */
class SignalsPublishNoBlockTest {

    private static final String STREAM_ID = "stream-1";

    private static class CapturingStore implements PendingPushStore {
        final Map<String, PendingPushRecord> rows = new HashMap<>();
        final List<String> enqueued = new ArrayList<>();

        @Override
        public void enqueue(PendingPushRecord r) {
            enqueued.add(r.jti());
            rows.put(r.streamId() + ":" + r.jti(), r);
        }

        @Override public List<PendingPushRecord> peekOldest(String s, PendingPushState st, int l) {
            return rows.values().stream()
                    .filter(r -> r.streamId().equals(s) && r.state() == st)
                    .toList();
        }
        @Override public void markAttempted(String s, String j, Instant t, String e) {}
        @Override public void transitionState(String s, String j, PendingPushState st) {}
        @Override public void delete(String s, String j) { rows.remove(s + ":" + j); }
        @Override public long count(String s, PendingPushState st) {
            return rows.values().stream().filter(r -> r.streamId().equals(s) && r.state() == st).count();
        }
        @Override public long totalBytes(String s) {
            return rows.values().stream().filter(r -> r.streamId().equals(s)).mapToLong(PendingPushRecord::bytes).sum();
        }
    }

    private static PushStream newStream() {
        PushStream s = new PushStream();
        s.streamId = STREAM_ID;
        s.endpointUrl = "https://example.com/events";
        s.authorization = "NONE";
        s.iss = "test-issuer";
        s.aud = "test-audience";
        s.issuerKey = null;
        s.maxRetries = 99;          // would normally retry many times
        s.initialDelay = 60_000;    // would normally sleep 60s+
        s.maxDelay = 60_000;
        s.unauthorizedRetryMax = 99;
        s.unauthorizedRetryDelay = 60_000;
        return s;
    }

    private static SecurityEventToken token(String jti) {
        SecurityEventToken t = new SecurityEventToken();
        t.setJti(jti);
        return t;
    }

    private static void invokeProducer(PushStream push, PendingPushStore store, SecurityEventToken token) throws Exception {
        Method m = SignalsEventHandler.class.getDeclaredMethod(
                "attemptInlineAndEnqueueOnFailure",
                PushStream.class, PendingPushStore.class, SecurityEventToken.class,
                com.independentid.scim.op.Operation.class
        );
        m.setAccessible(true);
        m.invoke(null, push, store, token, null);
    }

    @Test
    void producerReturnsImmediatelyOn503AndEnqueuesPayload() throws Exception {
        PushStream stream = newStream();
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        CloseableHttpResponse fail = mock(CloseableHttpResponse.class);
        when(fail.getCode()).thenReturn(503);
        when(fail.getReasonPhrase()).thenReturn("Service Unavailable");
        when(client.execute(any(HttpPost.class))).thenReturn(fail);
        stream.client = client;

        CapturingStore store = new CapturingStore();

        long start = System.nanoTime();
        invokeProducer(stream, store, token("jti-1"));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMs).as("publish must not block on retry").isLessThan(1_000);
        assertThat(store.enqueued).containsExactly("jti-1");
        verify(client, times(1)).execute(any(HttpPost.class));
        // Producer side does not disable the stream — that is the worker's job
        assertThat(stream.state.getStatus()).isEqualTo(StreamStatus.ENABLED);
    }

    @Test
    void producerDoesNotEnqueueOnSuccess() throws Exception {
        PushStream stream = newStream();
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        CloseableHttpResponse ok = mock(CloseableHttpResponse.class);
        when(ok.getCode()).thenReturn(200);
        when(ok.getReasonPhrase()).thenReturn("OK");
        when(client.execute(any(HttpPost.class))).thenReturn(ok);
        stream.client = client;

        CapturingStore store = new CapturingStore();
        invokeProducer(stream, store, token("jti-1"));

        assertThat(store.enqueued).isEmpty();
        verify(client, times(1)).execute(any(HttpPost.class));
    }

    @Test
    void producerEnqueuesEvenWhenStreamIsDisabled() throws Exception {
        PushStream stream = newStream();
        stream.state.transitionTo(StreamStatus.DISABLED, "test setup");
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        stream.client = client;

        CapturingStore store = new CapturingStore();
        invokeProducer(stream, store, token("jti-disabled"));

        // No HTTP attempt while disabled, but event MUST be persisted (PRD-B invariant)
        verify(client, times(0)).execute(any(HttpPost.class));
        assertThat(store.enqueued).containsExactly("jti-disabled");
    }

    @Test
    void producerSwallowsTransportException() throws Exception {
        PushStream stream = newStream();
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        when(client.execute(any(HttpPost.class))).thenThrow(new IOException("connection refused"));
        stream.client = client;

        CapturingStore store = new CapturingStore();

        long start = System.nanoTime();
        invokeProducer(stream, store, token("jti-tx"));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        // Must not propagate the IOException; must enqueue and return promptly
        assertThat(elapsedMs).isLessThan(1_000);
        assertThat(store.enqueued).containsExactly("jti-tx");
    }
}
