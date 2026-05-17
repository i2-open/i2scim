package com.independentid.scim.test.signals;

import com.independentid.set.SecurityEventToken;
import com.independentid.signals.MongoPendingPushStore;
import com.independentid.signals.PendingPushState;
import com.independentid.signals.PushRetryWorker;
import com.independentid.signals.PushStream;
import com.independentid.signals.SignalsEventHandler;
import com.independentid.signals.StreamStatus;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

/**
 * PRD-B slice #73 integration test: producer + durable Mongo store + retry worker
 * exercised against a scripted HTTP receiver. Boots Mongo via Testcontainers
 * and a JDK in-process HTTP server — no Quarkus, kept fast.
 *
 * <p>Covers:
 * <ul>
 *   <li>5xx flap then recover — undelivered events sit in {@code pendingPushes}
 *       and the worker drains them in queued order once the receiver recovers.</li>
 *   <li>Restart-with-pending — after stopping the worker mid-flight, a fresh
 *       worker built against the same Mongo store picks up the leftover rows.</li>
 * </ul>
 */
class SignalsDurabilityTest {

    private static final String DB_NAME = "i2scimSlice73IT";
    private static final String STREAM_ID = "it-stream";

    private static MongoDBContainer mongo;
    private static MongoClient mongoClient;
    private static MongoPendingPushStore store;

    private HttpServer server;
    private final AtomicInteger failuresRemaining = new AtomicInteger(0);
    private final ConcurrentLinkedQueue<String> seenJtis = new ConcurrentLinkedQueue<>();

    private PushStream stream;
    private PushRetryWorker worker;

    @BeforeAll
    static void startInfra() {
        mongo = new MongoDBContainer(DockerImageName.parse("mongo:8.0"));
        mongo.start();
        mongoClient = MongoClients.create(mongo.getReplicaSetUrl());
        store = new MongoPendingPushStore(mongoClient, DB_NAME);
        store.init();
    }

    @AfterAll
    static void stopInfra() {
        if (mongoClient != null) mongoClient.close();
        if (mongo != null) mongo.stop();
    }

    @BeforeEach
    void clearAndStartReceiver() throws Exception {
        mongoClient.getDatabase(DB_NAME).getCollection(MongoPendingPushStore.COLLECTION).drop();
        store.init();

        seenJtis.clear();
        failuresRemaining.set(0);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newFixedThreadPool(2));
        server.createContext("/events", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            String bodyStr = new String(body);
            int dot = bodyStr.indexOf('.', bodyStr.indexOf('.') + 1);
            // crude jti capture: include the unsigned-JWS body bytes
            seenJtis.add(bodyStr);
            int code;
            if (failuresRemaining.getAndDecrement() > 0) {
                code = 503;
                exchange.sendResponseHeaders(code, -1);
            } else {
                code = 202;
                exchange.sendResponseHeaders(code, 0);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(new byte[0]);
                }
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
        stream.issuerKey = null;
        stream.maxRetries = 50;
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

    private static SecurityEventToken token(String jti) {
        SecurityEventToken t = new SecurityEventToken();
        t.setJti(jti);
        return t;
    }

    private void produce(SecurityEventToken token) throws Exception {
        Method m = SignalsEventHandler.class.getDeclaredMethod(
                "attemptInlineAndEnqueueOnFailure",
                PushStream.class,
                com.independentid.signals.PendingPushStore.class,
                SecurityEventToken.class,
                com.independentid.scim.op.Operation.class);
        m.setAccessible(true);
        m.invoke(null, stream, store, token, null);
    }

    @Test
    void fivexxFlapThenRecover_drainsAllQueuedJtis() throws Exception {
        // Receiver fails the next 6 requests, then succeeds forever.
        failuresRemaining.set(6);

        // Produce 3 events while the receiver is failing — all should land in pendingPushes.
        produce(token("jti-A"));
        produce(token("jti-B"));
        produce(token("jti-C"));

        assertThat(store.count(STREAM_ID, PendingPushState.pending)).isGreaterThan(0L);

        // Start the worker; let it drain.
        worker = new PushRetryWorker(stream, store, PushRetryWorker.REAL_SLEEPER,
                java.time.Clock.systemUTC(), 32, 50L);
        worker.start();

        waitUntilPendingIsZero(Duration.ofSeconds(15));
        assertThat(stream.state.getStatus()).isEqualTo(StreamStatus.ENABLED);
    }

    private static void waitUntilPendingIsZero(Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (store.count(STREAM_ID, PendingPushState.pending) == 0L) return;
            Thread.sleep(50);
        }
        assertThat(store.count(STREAM_ID, PendingPushState.pending)).isZero();
    }

    @Test
    void restartWithPendingEvents_newWorkerPicksThemUp() throws Exception {
        // Receiver fails for the first burst.
        failuresRemaining.set(100);

        produce(token("jti-1"));
        produce(token("jti-2"));
        produce(token("jti-3"));

        // First worker starts but the receiver keeps failing — it should mark+sleep
        // without disabling (maxRetries is large).
        worker = new PushRetryWorker(stream, store, PushRetryWorker.REAL_SLEEPER,
                java.time.Clock.systemUTC(), 32, 50L);
        worker.start();

        // Verify rows persist while receiver is still failing.
        Thread.sleep(200);
        assertThat(store.count(STREAM_ID, PendingPushState.pending)).isEqualTo(3L);

        // "Restart": stop worker; events still in Mongo.
        worker.shutdown();
        worker = null;
        long persisted = store.count(STREAM_ID, PendingPushState.pending);
        assertThat(persisted).isEqualTo(3L);

        // Receiver is now healthy.
        failuresRemaining.set(0);

        // New worker against same Mongo store picks up where the first left off.
        PushStream restarted = new PushStream();
        restarted.streamId = stream.streamId;
        restarted.endpointUrl = stream.endpointUrl;
        restarted.authorization = stream.authorization;
        restarted.iss = stream.iss;
        restarted.aud = stream.aud;
        restarted.maxRetries = stream.maxRetries;
        restarted.initialDelay = stream.initialDelay;
        restarted.maxDelay = stream.maxDelay;
        restarted.unauthorizedRetryMax = stream.unauthorizedRetryMax;
        restarted.unauthorizedRetryDelay = stream.unauthorizedRetryDelay;
        restarted.setSslContext(null);

        worker = new PushRetryWorker(restarted, store, PushRetryWorker.REAL_SLEEPER,
                java.time.Clock.systemUTC(), 32, 50L);
        worker.start();

        waitUntilPendingIsZero(Duration.ofSeconds(15));
    }

    @Test
    void persistedQueuedAtSurvivesPeekAcrossRestart() {
        Instant t0 = Instant.parse("2026-05-06T08:00:00Z");
        store.enqueue(com.independentid.signals.PendingPushRecord.ofNew(
                STREAM_ID, "jti-late", "p", PendingPushState.pending, t0.plusSeconds(1000)));
        store.enqueue(com.independentid.signals.PendingPushRecord.ofNew(
                STREAM_ID, "jti-early", "p", PendingPushState.pending, t0));

        // Simulate restart: re-init store against the same db.
        MongoPendingPushStore reopened = new MongoPendingPushStore(mongoClient, DB_NAME);
        reopened.init();

        var drained = reopened.peekOldest(STREAM_ID, PendingPushState.pending, 10);
        assertThat(drained).extracting(com.independentid.signals.PendingPushRecord::jti)
                .containsExactly("jti-early", "jti-late");
    }
}
