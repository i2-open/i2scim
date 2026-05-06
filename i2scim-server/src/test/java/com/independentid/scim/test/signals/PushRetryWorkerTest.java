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
    void elapsedTimeCapDisablesStreamAndPreservesQueue() throws Exception {
        // PRD-B slice #75: elapsed-time cap, not attempt count, drives DISABLE.
        PushStream stream = newStream();
        stream.maxRetries = 0;             // legacy attempt cap disabled
        stream.initialDelay = 0;
        stream.maxDelay = 0;
        stream.pubRetryElapsedLimit = 60_000; // 60s elapsed budget for the test

        CloseableHttpClient client = mock(CloseableHttpClient.class);
        CloseableHttpResponse fail = mock(CloseableHttpResponse.class);
        when(fail.getCode()).thenReturn(503);
        when(fail.getReasonPhrase()).thenReturn("Service Unavailable");
        when(client.execute(any(HttpPost.class))).thenReturn(fail);
        stream.client = client;

        InMemoryStore store = new InMemoryStore();
        Instant queuedAt = Instant.parse("2026-05-06T12:00:00Z");
        store.enqueue(PendingPushRecord.ofNew(STREAM_ID, "jti-1", "p1", PendingPushState.pending, queuedAt));
        store.enqueue(PendingPushRecord.ofNew(STREAM_ID, "jti-2", "p2", PendingPushState.pending, queuedAt.plusSeconds(1)));

        // Virtual clock 61s after queuedAt — elapsed 61s > 60s budget.
        Clock past = Clock.fixed(queuedAt.plusSeconds(61), ZoneOffset.UTC);
        CapturingSleeper sleeper = new CapturingSleeper();
        PushRetryWorker worker = new PushRetryWorker(stream, store, sleeper, past, 10, 0);

        worker.runDrainCycle();

        assertThat(stream.state.getStatus()).isEqualTo(StreamStatus.DISABLED);
        assertThat(stream.state.getErrorMsg()).contains("transport recovery exceeded");
        // operations.md-style messaging: time-based, not attempt-based
        assertThat(stream.state.getErrorMsg()).doesNotContain("attempts");
        // Queue NOT drained: pending JTIs retained for re-enable
        assertThat(store.count(STREAM_ID, PendingPushState.pending)).isEqualTo(2L);
    }

    @Test
    void elapsedTimeCapNotYetReachedSleepsAndKeepsRetrying() throws Exception {
        PushStream stream = newStream();
        stream.maxRetries = 0;
        stream.initialDelay = 100;
        stream.maxDelay = 5000;
        stream.pubRetryElapsedLimit = 60_000;

        CloseableHttpClient client = mock(CloseableHttpClient.class);
        CloseableHttpResponse fail = mock(CloseableHttpResponse.class);
        when(fail.getCode()).thenReturn(503);
        when(fail.getReasonPhrase()).thenReturn("Service Unavailable");
        when(client.execute(any(HttpPost.class))).thenReturn(fail);
        stream.client = client;

        InMemoryStore store = new InMemoryStore();
        Instant queuedAt = Instant.parse("2026-05-06T12:00:00Z");
        store.enqueue(PendingPushRecord.ofNew(STREAM_ID, "jti-1", "p1", PendingPushState.pending, queuedAt));

        // Only 5s elapsed — well under the 60s budget.
        Clock recent = Clock.fixed(queuedAt.plusSeconds(5), ZoneOffset.UTC);
        CapturingSleeper sleeper = new CapturingSleeper();
        PushRetryWorker worker = new PushRetryWorker(stream, store, sleeper, recent, 10, 0);

        worker.runDrainCycle();

        assertThat(stream.state.getStatus()).isEqualTo(StreamStatus.ENABLED);
        assertThat(store.count(STREAM_ID, PendingPushState.pending)).isEqualTo(1L);
        assertThat(sleeper.sleeps).hasSize(1); // backoff sleep happened
    }

    @Test
    void operatorReEnableAfterDisableDrainsPendingInQueuedOrderAndKeepsStreamEnabled() throws Exception {
        // PRD-B slice #76: when the elapsed-time cap fires the worker DISABLES the
        // stream and retains pending JTIs. An operator re-enable through the
        // existing PRD-A control surface (StreamStateHolder.transitionTo) must
        // wake the worker, drain the queue in queuedAt order, and the first 2xx
        // must NOT cause the stream to fall back to DISABLED.
        PushStream stream = newStream();
        stream.maxRetries = 0;
        stream.initialDelay = 0;
        stream.maxDelay = 0;
        stream.pubRetryElapsedLimit = 60_000;

        CloseableHttpClient client = mock(CloseableHttpClient.class);
        CloseableHttpResponse fail = mock(CloseableHttpResponse.class);
        when(fail.getCode()).thenReturn(503);
        when(fail.getReasonPhrase()).thenReturn("Service Unavailable");
        CloseableHttpResponse ok = mock(CloseableHttpResponse.class);
        when(ok.getCode()).thenReturn(200);
        when(ok.getReasonPhrase()).thenReturn("OK");
        stream.client = client;

        InMemoryStore store = new InMemoryStore();
        Instant queuedAt = Instant.parse("2026-05-06T12:00:00Z");
        // Out-of-order enqueue to verify peekOldest sorts by queuedAt during drain.
        store.enqueue(PendingPushRecord.ofNew(STREAM_ID, "jti-c", "pc", PendingPushState.pending, queuedAt.plusSeconds(20)));
        store.enqueue(PendingPushRecord.ofNew(STREAM_ID, "jti-a", "pa", PendingPushState.pending, queuedAt));
        store.enqueue(PendingPushRecord.ofNew(STREAM_ID, "jti-b", "pb", PendingPushState.pending, queuedAt.plusSeconds(10)));

        // Phase 1: clock is past the elapsed budget; flap returns 503 → DISABLE.
        Clock past = Clock.fixed(queuedAt.plusSeconds(61), ZoneOffset.UTC);
        when(client.execute(any(HttpPost.class))).thenReturn(fail);
        CapturingSleeper sleeper = new CapturingSleeper();
        PushRetryWorker worker = new PushRetryWorker(stream, store, sleeper, past, 10, 0);

        worker.runDrainCycle();

        assertThat(stream.state.getStatus()).isEqualTo(StreamStatus.DISABLED);
        assertThat(stream.state.getErrorMsg()).contains("transport recovery exceeded");
        assertThat(store.count(STREAM_ID, PendingPushState.pending))
                .as("queue retained on DISABLE — slice #76 invariant")
                .isEqualTo(3L);

        // Phase 2: a runDrainCycle while DISABLED is a no-op (worker idle-sleeps
        // in the run loop; here we exercise runDrainCycle directly).
        boolean didWorkDisabled = worker.runDrainCycle();
        assertThat(didWorkDisabled).isFalse();
        assertThat(store.count(STREAM_ID, PendingPushState.pending)).isEqualTo(3L);

        // Phase 3: operator re-enables via the existing PRD-A control surface.
        // Receiver now returns 200 for every attempt.
        stream.state.transitionTo(StreamStatus.ENABLED, null);
        when(client.execute(any(HttpPost.class))).thenReturn(ok);

        // Order observation: capture the JTI of each successful execute() so we
        // can assert in-queued-order drain (jti-a, jti-b, jti-c).
        List<String> deliveredOrder = new ArrayList<>();
        when(client.execute(any(HttpPost.class))).thenAnswer(inv -> {
            HttpPost post = inv.getArgument(0);
            String body = new String(post.getEntity().getContent().readAllBytes());
            deliveredOrder.add(body);
            return ok;
        });

        boolean didWorkEnabled = worker.runDrainCycle();

        assertThat(didWorkEnabled).isTrue();
        assertThat(stream.state.getStatus())
                .as("first 2xx after re-enable keeps the stream ENABLED")
                .isEqualTo(StreamStatus.ENABLED);
        assertThat(store.count(STREAM_ID, PendingPushState.pending))
                .as("all pending JTIs drained after re-enable")
                .isZero();
        assertThat(deliveredOrder)
                .as("drain followed queuedAt order")
                .containsExactly("pa", "pb", "pc");
    }

    @Test
    void operatorReEnableInterruptsIdleSleepingWorker() throws Exception {
        // PRD-B slice #76: operator re-enable should "wake" the worker — it must
        // not sit out a multi-second idle sleep before noticing the new state.
        // The worker is configured with idleSleepMs=5_000; if wake-up is wired
        // correctly via a transition listener that interrupts the worker thread,
        // re-enable causes the queue to drain in well under that window.
        PushStream stream = newStream();
        stream.maxRetries = 0;
        stream.initialDelay = 0;
        stream.maxDelay = 0;
        stream.pubRetryElapsedLimit = 60_000;
        // Stream starts in DISABLED so the worker run loop immediately enters idle-sleep.
        stream.state.transitionTo(StreamStatus.DISABLED, "test setup");

        CloseableHttpClient client = mock(CloseableHttpClient.class);
        CloseableHttpResponse ok = mock(CloseableHttpResponse.class);
        when(ok.getCode()).thenReturn(200);
        when(ok.getReasonPhrase()).thenReturn("OK");
        when(client.execute(any(HttpPost.class))).thenReturn(ok);
        stream.client = client;

        InMemoryStore store = new InMemoryStore();
        Instant t0 = Instant.parse("2026-05-06T12:00:00Z");
        store.enqueue(PendingPushRecord.ofNew(STREAM_ID, "jti-1", "p1", PendingPushState.pending, t0));

        // idleSleepMs=5_000: without a wake-up signal the worker would sleep for
        // 5 seconds before reading the queue.
        PushRetryWorker worker = new PushRetryWorker(stream, store,
                PushRetryWorker.REAL_SLEEPER, FIXED, 10, 5_000L);
        worker.start();

        // Let the worker enter idle-sleep on the DISABLED stream.
        Thread.sleep(150);
        assertThat(store.count(STREAM_ID, PendingPushState.pending))
                .as("nothing drained while DISABLED")
                .isEqualTo(1L);

        // Operator re-enable via existing PRD-A control surface.
        long reEnableAt = System.currentTimeMillis();
        stream.state.transitionTo(StreamStatus.ENABLED, null);

        // Poll until drain completes; cap wait well under idleSleepMs to prove
        // the wake-up was prompt rather than waiting out the idle window.
        long deadline = System.currentTimeMillis() + 1_000L;
        while (System.currentTimeMillis() < deadline
                && store.count(STREAM_ID, PendingPushState.pending) > 0L) {
            Thread.sleep(20);
        }
        long drainedAt = System.currentTimeMillis();
        worker.shutdown();

        assertThat(store.count(STREAM_ID, PendingPushState.pending))
                .as("worker drained queue within 1s of re-enable (idleSleepMs=5_000)")
                .isZero();
        assertThat(drainedAt - reEnableAt)
                .as("wake-up promptness — should be sub-second, far below the 5s idle window")
                .isLessThan(1_000L);
        assertThat(stream.state.getStatus()).isEqualTo(StreamStatus.ENABLED);
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
