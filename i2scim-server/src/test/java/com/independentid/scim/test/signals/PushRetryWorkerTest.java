package com.independentid.scim.test.signals;

import com.independentid.signals.PendingPushRecord;
import com.independentid.signals.PendingPushState;
import com.independentid.signals.PendingPushStore;
import com.independentid.signals.PushRetryWorker;
import com.independentid.signals.PushStream;
import com.independentid.signals.StreamStatus;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PushRetryWorkerTest {

    private static final String STREAM_ID = "stream-1";
    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-05-06T12:00:00Z"), ZoneOffset.UTC);

    private static PushStream newStream() {
        PushStream s = new PushStream();
        s.streamId = STREAM_ID;
        s.endpointUrl = "https://example.com/events";
        s.authorization = "NONE";
        s.iss = "test-issuer";
        s.aud = "test-audience";
        s.maxRetries = 5;
        s.initialDelay = 0;
        s.maxDelay = 0;
        s.unauthorizedRetryMax = 5;
        s.unauthorizedRetryDelay = 0;
        return s;
    }

    private static class CapturingSleeper implements PushRetryWorker.Sleeper {
        final List<Long> sleeps = new ArrayList<>();

        @Override
        public void sleep(long millis) {
            sleeps.add(millis);
        }
    }

    private static class InMemoryStore implements PendingPushStore {
        final Map<String, PendingPushRecord> rows = new HashMap<>();

        private static String key(String s, String j) { return s + ":" + j; }

        @Override
        public void enqueue(PendingPushRecord record) {
            rows.put(key(record.streamId(), record.jti()), record);
        }

        @Override
        public List<PendingPushRecord> peekOldest(String streamId, PendingPushState state, int limit) {
            return rows.values().stream()
                    .filter(r -> r.streamId().equals(streamId))
                    .filter(r -> r.state() == state)
                    .sorted(Comparator.comparing(PendingPushRecord::queuedAt))
                    .limit(limit)
                    .toList();
        }

        @Override
        public void markAttempted(String streamId, String jti, Instant attemptedAt, String errorMsg) {
            PendingPushRecord existing = rows.get(key(streamId, jti));
            if (existing == null) return;
            rows.put(key(streamId, jti), new PendingPushRecord(
                    existing.streamId(), existing.jti(), existing.payload(), existing.state(),
                    existing.queuedAt(), existing.attemptCount() + 1, attemptedAt, errorMsg, existing.bytes()
            ));
        }

        @Override
        public void transitionState(String streamId, String jti, PendingPushState newState) {
            PendingPushRecord existing = rows.get(key(streamId, jti));
            if (existing == null) return;
            rows.put(key(streamId, jti), new PendingPushRecord(
                    existing.streamId(), existing.jti(), existing.payload(), newState,
                    existing.queuedAt(), existing.attemptCount(), existing.lastAttemptAt(),
                    existing.lastError(), existing.bytes()
            ));
        }

        @Override
        public void delete(String streamId, String jti) {
            rows.remove(key(streamId, jti));
        }

        @Override
        public long count(String streamId, PendingPushState state) {
            return rows.values().stream()
                    .filter(r -> r.streamId().equals(streamId) && r.state() == state)
                    .count();
        }

        @Override
        public long totalBytes(String streamId) {
            return rows.values().stream()
                    .filter(r -> r.streamId().equals(streamId))
                    .mapToLong(PendingPushRecord::bytes)
                    .sum();
        }
    }

    @Test
    void drainsRecordsInQueuedOrderAndDeletesOnSuccess() throws Exception {
        PushStream stream = newStream();
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        CloseableHttpResponse ok = mock(CloseableHttpResponse.class);
        when(ok.getCode()).thenReturn(200);
        when(ok.getReasonPhrase()).thenReturn("OK");
        when(client.execute(any(HttpPost.class))).thenReturn(ok);
        stream.client = client;

        InMemoryStore store = new InMemoryStore();
        Instant t0 = Instant.parse("2026-05-06T12:00:00Z");
        store.enqueue(PendingPushRecord.ofNew(STREAM_ID, "jti-c", "pc", PendingPushState.pending, t0.plusSeconds(20)));
        store.enqueue(PendingPushRecord.ofNew(STREAM_ID, "jti-a", "pa", PendingPushState.pending, t0));
        store.enqueue(PendingPushRecord.ofNew(STREAM_ID, "jti-b", "pb", PendingPushState.pending, t0.plusSeconds(10)));

        PushRetryWorker worker = new PushRetryWorker(stream, store, new CapturingSleeper(), FIXED, 10, 0);
        boolean didWork = worker.runDrainCycle();

        assertThat(didWork).isTrue();
        assertThat(store.count(STREAM_ID, PendingPushState.pending)).isZero();
        verify(client, times(3)).execute(any(HttpPost.class));
    }

    @Test
    void onTransientFailureMarksAttemptAndSleepsBackoff() throws Exception {
        PushStream stream = newStream();
        stream.maxRetries = 5;
        stream.initialDelay = 100;
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        CloseableHttpResponse fail = mock(CloseableHttpResponse.class);
        when(fail.getCode()).thenReturn(503);
        when(fail.getReasonPhrase()).thenReturn("Service Unavailable");
        when(client.execute(any(HttpPost.class))).thenReturn(fail);
        stream.client = client;

        InMemoryStore store = new InMemoryStore();
        Instant t0 = Instant.parse("2026-05-06T12:00:00Z");
        store.enqueue(PendingPushRecord.ofNew(STREAM_ID, "jti-1", "p1", PendingPushState.pending, t0));

        CapturingSleeper sleeper = new CapturingSleeper();
        PushRetryWorker worker = new PushRetryWorker(stream, store, sleeper, FIXED, 10, 0);
        worker.runDrainCycle();

        assertThat(store.count(STREAM_ID, PendingPushState.pending)).isEqualTo(1L);
        PendingPushRecord r = store.peekOldest(STREAM_ID, PendingPushState.pending, 1).get(0);
        assertThat(r.attemptCount()).isEqualTo(1);
        assertThat(r.lastError()).contains("503");
        assertThat(sleeper.sleeps).hasSize(1);
        assertThat(stream.state.getStatus()).isEqualTo(StreamStatus.ENABLED);
    }

    @Test
    void onTerminalFailureDisablesStreamAndPreservesQueue() throws Exception {
        PushStream stream = newStream();
        stream.maxRetries = 1; // first attempt yields newAttempt=1, second yields newAttempt=2 -> Disable
        stream.initialDelay = 0;
        stream.maxDelay = 0;

        CloseableHttpClient client = mock(CloseableHttpClient.class);
        CloseableHttpResponse fail = mock(CloseableHttpResponse.class);
        when(fail.getCode()).thenReturn(503);
        when(fail.getReasonPhrase()).thenReturn("Service Unavailable");
        when(client.execute(any(HttpPost.class))).thenReturn(fail);
        stream.client = client;

        InMemoryStore store = new InMemoryStore();
        Instant t0 = Instant.parse("2026-05-06T12:00:00Z");
        store.enqueue(PendingPushRecord.ofNew(STREAM_ID, "jti-1", "p1", PendingPushState.pending, t0));
        store.enqueue(PendingPushRecord.ofNew(STREAM_ID, "jti-2", "p2", PendingPushState.pending, t0.plusSeconds(1)));

        CapturingSleeper sleeper = new CapturingSleeper();
        PushRetryWorker worker = new PushRetryWorker(stream, store, sleeper, FIXED, 10, 0);

        // Cycle 1: attempts jti-1, gets 503, attemptCount->1; RetryStrategy at attempt=1 with maxRetries=1 -> Disable
        worker.runDrainCycle();

        assertThat(stream.state.getStatus()).isEqualTo(StreamStatus.DISABLED);
        assertThat(stream.state.getErrorMsg()).contains("transport recovery exceeded");
        // Queue NOT drained: pending JTIs retained
        assertThat(store.count(STREAM_ID, PendingPushState.pending)).isEqualTo(2L);
    }

    @Test
    void stopsDrainingWhenStreamPaused() throws Exception {
        PushStream stream = newStream();
        stream.state.transitionTo(StreamStatus.PAUSED, "test");
        InMemoryStore store = new InMemoryStore();
        Instant t = Instant.parse("2026-05-06T12:00:00Z");
        store.enqueue(PendingPushRecord.ofNew(STREAM_ID, "jti-1", "p", PendingPushState.pending, t));

        CapturingSleeper sleeper = new CapturingSleeper();
        PushRetryWorker worker = new PushRetryWorker(stream, store, sleeper, FIXED, 10, 0);
        boolean didWork = worker.runDrainCycle();

        assertThat(didWork).isFalse();
        assertThat(store.count(STREAM_ID, PendingPushState.pending)).isEqualTo(1L);
    }

    @Test
    void recoveryAfterTransientFailure() throws Exception {
        PushStream stream = newStream();
        stream.maxRetries = 5;
        stream.initialDelay = 0;

        CloseableHttpClient client = mock(CloseableHttpClient.class);
        CloseableHttpResponse fail = mock(CloseableHttpResponse.class);
        when(fail.getCode()).thenReturn(503);
        when(fail.getReasonPhrase()).thenReturn("Service Unavailable");
        CloseableHttpResponse ok = mock(CloseableHttpResponse.class);
        when(ok.getCode()).thenReturn(200);
        when(ok.getReasonPhrase()).thenReturn("OK");
        // first execute returns fail, second returns ok
        when(client.execute(any(HttpPost.class))).thenReturn(fail, ok);
        stream.client = client;

        InMemoryStore store = new InMemoryStore();
        Instant t0 = Instant.parse("2026-05-06T12:00:00Z");
        store.enqueue(PendingPushRecord.ofNew(STREAM_ID, "jti-1", "p1", PendingPushState.pending, t0));

        CapturingSleeper sleeper = new CapturingSleeper();
        PushRetryWorker worker = new PushRetryWorker(stream, store, sleeper, FIXED, 10, 0);

        // Cycle 1: attempt -> 503, marked, sleep
        worker.runDrainCycle();
        assertThat(store.count(STREAM_ID, PendingPushState.pending)).isEqualTo(1L);

        // Cycle 2: attempt -> 200, deleted
        worker.runDrainCycle();
        assertThat(store.count(STREAM_ID, PendingPushState.pending)).isZero();
        assertThat(stream.state.getStatus()).isEqualTo(StreamStatus.ENABLED);
    }
}
