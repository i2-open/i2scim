package com.independentid.scim.test.signals;

import com.independentid.signals.FilePendingAckStore;
import com.independentid.signals.PendingAckRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class FilePendingAckStoreTest {

    private static final String STREAM_A = "stream-a";
    private static final String STREAM_B = "stream-b";

    @TempDir
    Path root;

    private FilePendingAckStore store;

    @BeforeEach
    void setup() {
        store = new FilePendingAckStore(root);
        store.init();
    }

    @Test
    void enqueueRoundtripsAndCountReflectsIt() {
        Instant t = Instant.parse("2026-05-06T10:00:00Z");
        store.enqueue(STREAM_A, "jti-1", t);

        assertThat(store.count(STREAM_A)).isEqualTo(1L);
        List<PendingAckRecord> records = store.peekAll(STREAM_A);
        assertThat(records).hasSize(1);
        PendingAckRecord r = records.get(0);
        assertThat(r.streamId()).isEqualTo(STREAM_A);
        assertThat(r.jti()).isEqualTo("jti-1");
        assertThat(r.appliedAt()).isEqualTo(t);
    }

    @Test
    void enqueueIsIdempotentOnStreamIdJti() {
        Instant t1 = Instant.parse("2026-05-06T10:00:00Z");
        Instant t2 = Instant.parse("2026-05-06T11:00:00Z");
        store.enqueue(STREAM_A, "jti-1", t1);
        store.enqueue(STREAM_A, "jti-1", t2);

        assertThat(store.count(STREAM_A)).isEqualTo(1L);
        assertThat(store.peekAll(STREAM_A).get(0).appliedAt()).isEqualTo(t2);
    }

    @Test
    void peekAllScopedByStreamId() {
        Instant t = Instant.parse("2026-05-06T10:00:00Z");
        store.enqueue(STREAM_A, "jti-A", t);
        store.enqueue(STREAM_B, "jti-B", t);

        assertThat(store.peekAll(STREAM_A)).extracting(PendingAckRecord::jti).containsExactly("jti-A");
        assertThat(store.peekAll(STREAM_B)).extracting(PendingAckRecord::jti).containsExactly("jti-B");
    }

    @Test
    void deleteRemovesRowAndCountReflectsIt() {
        Instant t = Instant.parse("2026-05-06T10:00:00Z");
        store.enqueue(STREAM_A, "jti-1", t);
        store.enqueue(STREAM_A, "jti-2", t);

        store.delete(STREAM_A, "jti-1");

        assertThat(store.count(STREAM_A)).isEqualTo(1L);
        assertThat(store.peekAll(STREAM_A)).extracting(PendingAckRecord::jti).containsExactly("jti-2");
    }

    @Test
    void rowsSurviveReloadOnFreshInstance() {
        Instant t = Instant.parse("2026-05-06T10:00:00Z");
        for (int i = 0; i < 25; i++) {
            store.enqueue(STREAM_A, "jti-" + i, t.plusSeconds(i));
        }

        FilePendingAckStore reopened = new FilePendingAckStore(root);
        reopened.init();
        assertThat(reopened.count(STREAM_A)).isEqualTo(25L);
        assertThat(reopened.peekAll(STREAM_A)).extracting(PendingAckRecord::jti).hasSize(25);
    }

    @Test
    void leftoverTmpFileIsIgnoredOnReload() throws Exception {
        Instant t = Instant.parse("2026-05-06T10:00:00Z");
        store.enqueue(STREAM_A, "jti-good", t);

        Path streamDir = root.resolve("events").resolve("acks").resolve(STREAM_A);
        Files.writeString(streamDir.resolve("jti-half.ack.tmp"), "<<<garbage half-written ack");

        FilePendingAckStore reopened = new FilePendingAckStore(root);
        reopened.init();
        assertThat(reopened.count(STREAM_A)).isEqualTo(1L);
        assertThat(reopened.peekAll(STREAM_A)).extracting(PendingAckRecord::jti).containsExactly("jti-good");
    }

    @Test
    void concurrentEnqueueAndDeleteIsSafe() throws Exception {
        Instant t = Instant.parse("2026-05-06T10:00:00Z");
        // Pre-populate so deleter has something to remove.
        for (int i = 0; i < 50; i++) store.enqueue(STREAM_A, "seed-" + i, t);

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            pool.submit(() -> IntStream.range(0, 100).forEach(i ->
                    store.enqueue(STREAM_A, "live-" + i, t.plusSeconds(i))));
            pool.submit(() -> IntStream.range(0, 50).forEach(i -> store.delete(STREAM_A, "seed-" + i)));
            pool.shutdown();
            assertThat(pool.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }
        assertThat(store.count(STREAM_A)).isEqualTo(100L);
    }
}
