package com.independentid.scim.test.signals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.independentid.signals.FilePendingAckStore;
import com.independentid.signals.PollStream;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PRD-B slice #78 integration test: poll-side ack list survives a "restart"
 * (fresh {@link FilePendingAckStore} + fresh {@link PollStream} against the
 * same on-disk root) and the next poll cycle delivers every pre-applied JTI to
 * the transmitter exactly once. Mirrors {@code MemoryDurabilityTest} (push
 * side) for the ack case.
 */
class AckDurabilityTest {

    private static final String STREAM_ID = "ack-stream";
    private static final int BURST = 25;

    @TempDir
    Path memoryRoot;

    private HttpServer server;
    private final Set<String> ackedJtis = ConcurrentHashMap.newKeySet();

    @BeforeEach
    void setupReceiver() throws Exception {
        ackedJtis.clear();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newFixedThreadPool(2));
        server.createContext("/poll", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            JsonNode req = new ObjectMapper().readTree(body);
            JsonNode acks = req.get("ack");
            if (acks != null && acks.isArray()) {
                acks.forEach(n -> ackedJtis.add(n.asText()));
            }
            byte[] resp = "{\"sets\":{}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(resp); }
        });
        server.start();
    }

    @AfterEach
    void stopReceiver() {
        if (server != null) server.stop(0);
    }

    @Test
    void pendingAcksOnDiskAreDeliveredAfterRestart() {
        // First "instance": populate the store with 25 pending acks under the stream id.
        FilePendingAckStore first = new FilePendingAckStore(memoryRoot);
        first.init();
        Instant t = Instant.parse("2026-05-06T10:00:00Z");
        for (int i = 0; i < BURST; i++) {
            first.enqueue(STREAM_ID, "jti-" + i, t.plusSeconds(i));
        }
        assertThat(first.count(STREAM_ID)).isEqualTo(BURST);

        // Second "instance": fresh store + fresh PollStream against the same on-disk
        // root. Calling pollEvents(true, 0) sources acks from the store and posts
        // them to the in-process transmitter; on 2xx the store rows are deleted.
        FilePendingAckStore second = new FilePendingAckStore(memoryRoot);
        second.init();
        assertThat(second.count(STREAM_ID)).isEqualTo(BURST); // confirm survives "restart"

        PollStream stream = new PollStream();
        stream.streamId = STREAM_ID;
        stream.endpointUrl = "http://" + server.getAddress().getHostString()
                + ":" + server.getAddress().getPort() + "/poll";
        stream.authorization = "NONE";
        stream.iss = "test-issuer";
        stream.aud = "test-audience";
        stream.maxRetries = 0;
        stream.initialDelay = 0;
        stream.maxDelay = 0;
        stream.ackStore = second;
        stream.setSslContext(null);

        stream.pollEvents(true, 0);

        // Transmitter received every JTI; durable store is empty.
        assertThat(ackedJtis).hasSize(BURST);
        for (int i = 0; i < BURST; i++) {
            assertThat(ackedJtis).contains("jti-" + i);
        }
        assertThat(second.count(STREAM_ID)).isZero();
    }
}
