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
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PRD-B slice #75 integration test 8 (time-scaled): the receiver 5xx-flaps for
 * almost the full elapsed-time budget and recovers just before the cap. The
 * worker keeps retrying through the flap and drains all queued JTIs in order
 * once the receiver recovers. The stream remains {@code ENABLED}.
 *
 * <p>Time is scaled — the operations.md default is 6h, but using a 4-second
 * elapsed budget here proves the same behavior in test wall-clock without
 * burning real hours.
 */
class ElapsedRetryFlapRecoveryTest {

    private static final String STREAM_ID = "elapsed-flap";
    private static final int BURST = 10;
    /** Receiver returns 503 until this wall-clock instant, then 202s. */
    private static final long FLAP_FOR_MILLIS = 3_000L;
    private static final long ELAPSED_LIMIT_MS = 8_000L;

    @TempDir
    Path memoryRoot;

    private HttpServer server;
    private final AtomicLong recoveryAt = new AtomicLong(Long.MAX_VALUE);
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
        // Pure elapsed-time semantics: no legacy attempt-cap overlay.
        stream.maxRetries = 0;
        stream.initialDelay = 100;
        stream.maxDelay = 400;
        stream.pubRetryElapsedLimit = ELAPSED_LIMIT_MS;
        stream.unauthorizedRetryMax = 10;
        stream.unauthorizedRetryDelay = 100;
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
        long now = System.currentTimeMillis();
        if (now < recoveryAt.get()) {
            fivexxCount.incrementAndGet();
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
            return;
        }
        // The payload is exactly "jti-N-payload" (see seed()).
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

    /** Seed a record with the jti embedded as a plain marker in the payload (no real signing). */
    private static void seed(FilePendingPushStore store, String jti, Instant queuedAt) {
        store.enqueue(PendingPushRecord.ofNew(STREAM_ID, jti, jti + "-payload", PendingPushState.pending, queuedAt));
    }

    @Test
    void receiverFlapsThenRecoversBeforeElapsedCapAndAllJtisDrainInOrder() throws Exception {
        FilePendingPushStore store = new FilePendingPushStore(memoryRoot);
        store.init();

        Instant t0 = Instant.now();
        for (int i = 0; i < BURST; i++) {
            seed(store, "jti-" + i, t0.plusMillis(i));
        }
        assertThat(store.count(STREAM_ID, PendingPushState.pending)).isEqualTo((long) BURST);

        recoveryAt.set(System.currentTimeMillis() + FLAP_FOR_MILLIS);
        worker = new PushRetryWorker(stream, store, PushRetryWorker.REAL_SLEEPER,
                Clock.systemUTC(), 32, 50L);
        worker.start();

        waitUntilPendingIsZero(store, Duration.ofSeconds(15));

        assertThat(stream.state.getStatus())
                .as("stream stays ENABLED — the recovery happened before the elapsed cap")
                .isEqualTo(StreamStatus.ENABLED);
        assertThat(fivexxCount.get())
                .as("at least one 5xx was observed during the flap")
                .isGreaterThan(0);
        assertThat(orderedAcks)
                .as("all 10 JTIs eventually delivered (mid-batch flap may reorder, but every JTI lands)")
                .containsExactlyInAnyOrder("jti-0", "jti-1", "jti-2", "jti-3", "jti-4",
                        "jti-5", "jti-6", "jti-7", "jti-8", "jti-9");
    }

    private static void waitUntilPendingIsZero(FilePendingPushStore s, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (s.count(STREAM_ID, PendingPushState.pending) == 0L) return;
            Thread.sleep(50);
        }
        assertThat(s.count(STREAM_ID, PendingPushState.pending)).isZero();
    }
}
