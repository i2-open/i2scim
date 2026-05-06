package com.independentid.scim.test.signals;

import com.independentid.signals.FilePendingPushStore;
import com.independentid.signals.PendingPushRecord;
import com.independentid.signals.PendingPushState;
import com.independentid.signals.PushRetryWorker;
import com.independentid.signals.PushStream;
import com.independentid.signals.StreamStatus;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PRD-B slice #74 integration test: the memory-backend filesystem queue survives a
 * "restart" (fresh {@link FilePendingPushStore} instance against the same on-disk
 * root) and a fresh {@link PushRetryWorker} drains the events once the receiver
 * recovers. Mirrors {@code SignalsDurabilityTest} (Mongo) for the memory case
 * without a Mongo dependency.
 *
 * <p>Producer-no-block invariants are covered by {@code SignalsPublishNoBlockTest}
 * with mocked HTTP — this test focuses on on-disk durability + worker drain.
 */
class MemoryDurabilityTest {

    private static final String STREAM_ID = "mem-stream";
    private static final int BURST = 50;

    @TempDir
    Path memoryRoot;

    private HttpServer server;
    private final AtomicInteger acks = new AtomicInteger(0);
    private PushStream stream;
    private PushRetryWorker worker;

    @BeforeEach
    void setup() throws Exception {
        acks.set(0);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newFixedThreadPool(2));
        server.createContext("/events", exchange -> {
            exchange.getRequestBody().readAllBytes();
            acks.incrementAndGet();
            exchange.sendResponseHeaders(202, 0);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(new byte[0]);
            }
            exchange.close();
        });
        server.start();

        stream = new PushStream();
        stream.streamId = STREAM_ID;
        stream.endpointUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/events";
        stream.authorization = "NONE";
        stream.iss = "test-issuer";
        stream.aud = "test-audience";
        stream.maxRetries = 100;
        stream.initialDelay = 10;
        stream.maxDelay = 50;
        stream.unauthorizedRetryMax = 10;
        stream.unauthorizedRetryDelay = 10;
        stream.setSslContext(null);
    }

    @AfterEach
    void teardown() {
        if (worker != null) worker.shutdown();
        if (server != null) server.stop(0);
    }

    /** Stash a pre-signed payload under events/pending/<streamId>/<jti>.set, just as the producer would. */
    private static void seed(FilePendingPushStore s, String jti, String payload, Instant queuedAt) {
        s.enqueue(PendingPushRecord.ofNew(STREAM_ID, jti, payload, PendingPushState.pending, queuedAt));
    }

    @Test
    void fiftyPendingEventsSurviveRestartAndAreDrainedByFreshWorker() throws Exception {
        // Stage 1: producer crashed mid-run; 50 pending .set files on disk.
        FilePendingPushStore producerStore = new FilePendingPushStore(memoryRoot);
        producerStore.init();
        Instant t0 = Instant.parse("2026-05-06T10:00:00Z");
        for (int i = 0; i < BURST; i++) {
            seed(producerStore, "jti-" + i, "payload-" + i, t0.plusMillis(i));
        }
        assertThat(producerStore.count(STREAM_ID, PendingPushState.pending)).isEqualTo((long) BURST);

        // On-disk verification: each event is a real .set file under events/pending/<streamId>/
        Path streamDir = memoryRoot.resolve("events").resolve(PendingPushState.pending.name()).resolve(STREAM_ID);
        try (var paths = Files.list(streamDir)) {
            long setFiles = paths.filter(p -> p.getFileName().toString().endsWith(".set")).count();
            assertThat(setFiles).isEqualTo((long) BURST);
        }

        // Stage 2: restart — fresh store + fresh worker against the same on-disk root.
        FilePendingPushStore reopened = new FilePendingPushStore(memoryRoot);
        reopened.init();
        assertThat(reopened.count(STREAM_ID, PendingPushState.pending))
                .as("fresh store sees the persisted queue")
                .isEqualTo((long) BURST);

        worker = new PushRetryWorker(stream, reopened, PushRetryWorker.REAL_SLEEPER,
                Clock.systemUTC(), 32, 50L);
        worker.start();

        waitUntilPendingIsZero(reopened, Duration.ofSeconds(15));
        assertThat(stream.state.getStatus()).isEqualTo(StreamStatus.ENABLED);
        assertThat(acks.get())
                .as("receiver acks one POST per drained event")
                .isEqualTo(BURST);
    }

    @Test
    void leftoverTmpFromCrashedEnqueueIsIgnoredOnRestart() throws Exception {
        FilePendingPushStore producerStore = new FilePendingPushStore(memoryRoot);
        producerStore.init();
        Instant t0 = Instant.parse("2026-05-06T10:00:00Z");
        seed(producerStore, "jti-good", "payload-good", t0);

        // Simulate a crashed mid-write: a leftover .set.tmp in the same stream dir.
        Path streamDir = memoryRoot.resolve("events").resolve(PendingPushState.pending.name()).resolve(STREAM_ID);
        Files.writeString(streamDir.resolve("jti-half.set.tmp"), "<<<half-written");

        // Restart: only the committed jti-good is visible.
        FilePendingPushStore reopened = new FilePendingPushStore(memoryRoot);
        reopened.init();
        assertThat(reopened.count(STREAM_ID, PendingPushState.pending)).isEqualTo(1L);

        worker = new PushRetryWorker(stream, reopened, PushRetryWorker.REAL_SLEEPER,
                Clock.systemUTC(), 32, 50L);
        worker.start();
        waitUntilPendingIsZero(reopened, Duration.ofSeconds(10));
        assertThat(acks.get()).isEqualTo(1);
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
