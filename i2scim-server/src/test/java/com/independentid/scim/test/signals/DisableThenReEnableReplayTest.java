package com.independentid.scim.test.signals;

import com.independentid.signals.FilePendingPushStore;
import com.independentid.signals.PendingPushRecord;
import com.independentid.signals.PendingPushState;
import com.independentid.signals.PushRetryWorker;
import com.independentid.signals.PushStream;
import com.independentid.signals.StreamStatus;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PRD-B slice #76 integration tests 9 + 10: an elapsed-time-cap DISABLE
 * preserves pending JTIs in the durable store; an operator re-enable through
 * the existing PRD-A control surface ({@code state.transitionTo(ENABLED, null)})
 * wakes the worker, which drains the queue in queued order and the stream
 * remains {@code ENABLED} after the first 2xx.
 *
 * <p>Time is scaled — operations.md uses 6h elapsed budget; here a 2s budget
 * proves the same disable + replay behavior in seconds of wall-clock.
 */
class DisableThenReEnableReplayTest {

    private static final String STREAM_ID = "disable-replay";
    private static final int BURST = 8;
    private static final long ELAPSED_LIMIT_MS = 2_000L;

    @TempDir
    Path memoryRoot;

    private HttpServer server;
    private final AtomicBoolean receiverHealthy = new AtomicBoolean(false);
    private final AtomicLong fivexxCount = new AtomicLong(0);
    private final List<String> orderedAcks = new CopyOnWriteArrayList<>();
    private PushStream stream;
    private PushRetryWorker worker;

    @BeforeEach
    void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.createContext("/events", this::handle);
        server.start();

        stream = new PushStream();
        stream.streamId = STREAM_ID;
        stream.endpointUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/events";
        stream.authorization = "NONE";
        stream.iss = "test-issuer";
        stream.aud = "test-audience";
        // Pure elapsed-time semantics; no legacy attempt-cap overlay.
        stream.maxRetries = 0;
        stream.initialDelay = 50;
        stream.maxDelay = 200;
        stream.pubRetryElapsedLimit = ELAPSED_LIMIT_MS;
        stream.unauthorizedRetryMax = 10;
        stream.unauthorizedRetryDelay = 50;
        stream.setSslContext(null);
    }

    @AfterEach
    void teardown() {
        if (worker != null) worker.shutdown();
        if (server != null) server.stop(0);
    }

    private void handle(HttpExchange exchange) throws java.io.IOException {
        String body;
        try (var in = exchange.getRequestBody()) {
            body = new String(in.readAllBytes());
        }
        if (!receiverHealthy.get()) {
            fivexxCount.incrementAndGet();
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
            return;
        }
        int idx = body.indexOf("jti-");
        if (idx >= 0) {
            int end = body.indexOf("-payload", idx);
            if (end > idx) orderedAcks.add(body.substring(idx, end));
        }
        exchange.sendResponseHeaders(202, 0);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(new byte[0]);
        }
        exchange.close();
    }

    private static void seed(FilePendingPushStore store, String jti, Instant queuedAt) {
        store.enqueue(PendingPushRecord.ofNew(STREAM_ID, jti, jti + "-payload", PendingPushState.pending, queuedAt));
    }

    @Test
    void elapsedCapDisablesStreamAndOperatorReEnableReplaysAllPendingInOrder() throws Exception {
        FilePendingPushStore store = new FilePendingPushStore(memoryRoot);
        store.init();

        Instant t0 = Instant.now();
        for (int i = 0; i < BURST; i++) {
            seed(store, "jti-" + i, t0.plusMillis(i));
        }
        assertThat(store.count(STREAM_ID, PendingPushState.pending)).isEqualTo((long) BURST);

        // Long idleSleepMs proves the wake-up listener (slice #76) actually fires
        // — without it, the worker would sit out a 5s sleep between cycles.
        worker = new PushRetryWorker(stream, store, PushRetryWorker.REAL_SLEEPER,
                Clock.systemUTC(), 32, 5_000L);
        worker.start();

        // Phase 1: receiver 503-flaps; elapsed cap fires; stream DISABLED.
        waitUntilStatus(stream, StreamStatus.DISABLED, Duration.ofSeconds(10));
        assertThat(stream.state.getStatus()).isEqualTo(StreamStatus.DISABLED);
        assertThat(stream.state.getErrorMsg())
                .as("PRD-A operational vocabulary preserved through DISABLE")
                .contains("transport recovery exceeded");
        assertThat(store.count(STREAM_ID, PendingPushState.pending))
                .as("pending JTIs retained after DISABLE — slice #76 invariant")
                .isEqualTo((long) BURST);
        assertThat(fivexxCount.get()).isGreaterThan(0);

        // Phase 2: receiver recovers; operator re-enables via PRD-A control surface.
        receiverHealthy.set(true);
        long reEnableAt = System.currentTimeMillis();
        stream.state.transitionTo(StreamStatus.ENABLED, null);

        waitUntilPendingIsZero(store, Duration.ofSeconds(10));
        long drainedAt = System.currentTimeMillis();

        // Wake-up was prompt (well below idleSleepMs=5_000) — worker resumed
        // drain because of the transition listener, not the next idle wake.
        assertThat(drainedAt - reEnableAt)
                .as("re-enable woke the worker; drain completed under idle window")
                .isLessThan(5_000L);

        assertThat(stream.state.getStatus())
                .as("first 2xx after re-enable transitioned stream back to ENABLED via PRD-A's M1 holder")
                .isEqualTo(StreamStatus.ENABLED);
        assertThat(orderedAcks)
                .as("all JTIs drained in queuedAt order after re-enable")
                .containsExactly("jti-0", "jti-1", "jti-2", "jti-3",
                        "jti-4", "jti-5", "jti-6", "jti-7");
    }

    private static void waitUntilStatus(PushStream s, StreamStatus expected, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (s.state.getStatus() == expected) return;
            Thread.sleep(50);
        }
    }

    private static void waitUntilPendingIsZero(FilePendingPushStore s, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (s.count(STREAM_ID, PendingPushState.pending) == 0L) return;
            Thread.sleep(50);
        }
    }
}
