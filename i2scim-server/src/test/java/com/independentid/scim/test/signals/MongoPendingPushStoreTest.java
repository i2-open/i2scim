package com.independentid.scim.test.signals;

import com.independentid.signals.MongoPendingPushStore;
import com.independentid.signals.PendingPushRecord;
import com.independentid.signals.PendingPushState;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MongoPendingPushStoreTest {

    private static final String DB_NAME = "i2scimPendingPushTest";
    private static final String STREAM_A = "stream-a";
    private static final String STREAM_B = "stream-b";

    private static MongoDBContainer mongo;
    private static MongoClient client;
    private static MongoPendingPushStore store;

    @BeforeAll
    static void start() {
        mongo = new MongoDBContainer(DockerImageName.parse("mongo:8.0"));
        mongo.start();
        client = MongoClients.create(mongo.getReplicaSetUrl());
        store = new MongoPendingPushStore(client, DB_NAME);
        store.init();
    }

    @AfterAll
    static void stop() {
        if (client != null) client.close();
        if (mongo != null) mongo.stop();
    }

    @BeforeEach
    void clearCollection() {
        client.getDatabase(DB_NAME).getCollection(MongoPendingPushStore.COLLECTION).deleteMany(new Document());
    }

    @Test
    void enqueueIsIdempotentOnStreamIdJti() {
        Instant t = Instant.parse("2026-05-06T10:00:00Z");
        store.enqueue(PendingPushRecord.ofNew(STREAM_A, "jti-1", "payload-v1", PendingPushState.pending, t));
        store.enqueue(PendingPushRecord.ofNew(STREAM_A, "jti-1", "payload-v2", PendingPushState.pending, t));

        assertThat(store.count(STREAM_A, PendingPushState.pending)).isEqualTo(1L);
        List<PendingPushRecord> drained = store.peekOldest(STREAM_A, PendingPushState.pending, 10);
        assertThat(drained).hasSize(1);
        assertThat(drained.get(0).payload()).isEqualTo("payload-v2");
    }

    @Test
    void peekOldestReturnsRecordsOrderedByQueuedAt() {
        Instant t0 = Instant.parse("2026-05-06T10:00:00Z");
        store.enqueue(PendingPushRecord.ofNew(STREAM_A, "jti-3", "p3", PendingPushState.pending, t0.plusSeconds(30)));
        store.enqueue(PendingPushRecord.ofNew(STREAM_A, "jti-1", "p1", PendingPushState.pending, t0));
        store.enqueue(PendingPushRecord.ofNew(STREAM_A, "jti-2", "p2", PendingPushState.pending, t0.plusSeconds(15)));

        List<PendingPushRecord> drained = store.peekOldest(STREAM_A, PendingPushState.pending, 10);
        assertThat(drained).extracting(PendingPushRecord::jti).containsExactly("jti-1", "jti-2", "jti-3");
    }

    @Test
    void peekOldestRespectsLimitAndState() {
        Instant t0 = Instant.parse("2026-05-06T10:00:00Z");
        for (int i = 0; i < 5; i++) {
            store.enqueue(PendingPushRecord.ofNew(STREAM_A, "p-" + i, "x", PendingPushState.pending, t0.plusSeconds(i)));
        }
        for (int i = 0; i < 3; i++) {
            store.enqueue(PendingPushRecord.ofNew(STREAM_A, "r-" + i, "x", PendingPushState.preregister, t0.plusSeconds(i)));
        }

        assertThat(store.peekOldest(STREAM_A, PendingPushState.pending, 2)).extracting(PendingPushRecord::jti)
                .containsExactly("p-0", "p-1");
        assertThat(store.peekOldest(STREAM_A, PendingPushState.preregister, 10)).extracting(PendingPushRecord::jti)
                .containsExactly("r-0", "r-1", "r-2");
    }

    @Test
    void peekOldestIsScopedByStreamId() {
        Instant t = Instant.parse("2026-05-06T10:00:00Z");
        store.enqueue(PendingPushRecord.ofNew(STREAM_A, "jti-A", "a", PendingPushState.pending, t));
        store.enqueue(PendingPushRecord.ofNew(STREAM_B, "jti-B", "b", PendingPushState.pending, t));

        assertThat(store.peekOldest(STREAM_A, PendingPushState.pending, 10)).extracting(PendingPushRecord::jti)
                .containsExactly("jti-A");
        assertThat(store.peekOldest(STREAM_B, PendingPushState.pending, 10)).extracting(PendingPushRecord::jti)
                .containsExactly("jti-B");
    }

    @Test
    void markAttemptedIncrementsCountAndStampsError() {
        Instant t0 = Instant.parse("2026-05-06T10:00:00Z");
        store.enqueue(PendingPushRecord.ofNew(STREAM_A, "jti-1", "p", PendingPushState.pending, t0));

        Instant a1 = t0.plusSeconds(5);
        store.markAttempted(STREAM_A, "jti-1", a1, "503 Service Unavailable");
        Instant a2 = t0.plusSeconds(10);
        store.markAttempted(STREAM_A, "jti-1", a2, "503 Service Unavailable");

        PendingPushRecord r = store.peekOldest(STREAM_A, PendingPushState.pending, 1).get(0);
        assertThat(r.attemptCount()).isEqualTo(2);
        assertThat(r.lastAttemptAt()).isEqualTo(a2.truncatedTo(ChronoUnit.MILLIS));
        assertThat(r.lastError()).isEqualTo("503 Service Unavailable");
    }

    @Test
    void markAttemptedOnMissingRowIsNoOp() {
        store.markAttempted(STREAM_A, "missing", Instant.now(), "x"); // no throw
        assertThat(store.count(STREAM_A, PendingPushState.pending)).isZero();
    }

    @Test
    void transitionStateMovesBetweenPendingAndPreregister() {
        Instant t = Instant.parse("2026-05-06T10:00:00Z");
        store.enqueue(PendingPushRecord.ofNew(STREAM_A, "jti-1", "p", PendingPushState.preregister, t));
        assertThat(store.count(STREAM_A, PendingPushState.preregister)).isEqualTo(1L);
        assertThat(store.count(STREAM_A, PendingPushState.pending)).isZero();

        store.transitionState(STREAM_A, "jti-1", PendingPushState.pending);
        assertThat(store.count(STREAM_A, PendingPushState.preregister)).isZero();
        assertThat(store.count(STREAM_A, PendingPushState.pending)).isEqualTo(1L);
    }

    @Test
    void deleteRemovesRowAndCountReflectsIt() {
        Instant t = Instant.parse("2026-05-06T10:00:00Z");
        store.enqueue(PendingPushRecord.ofNew(STREAM_A, "jti-1", "p", PendingPushState.pending, t));
        store.enqueue(PendingPushRecord.ofNew(STREAM_A, "jti-2", "p", PendingPushState.pending, t.plusSeconds(1)));

        store.delete(STREAM_A, "jti-1");

        assertThat(store.count(STREAM_A, PendingPushState.pending)).isEqualTo(1L);
        assertThat(store.peekOldest(STREAM_A, PendingPushState.pending, 10))
                .extracting(PendingPushRecord::jti).containsExactly("jti-2");
    }

    @Test
    void totalBytesSumsAcrossAllStates() {
        Instant t = Instant.parse("2026-05-06T10:00:00Z");
        store.enqueue(PendingPushRecord.ofNew(STREAM_A, "p-1", "abcdef", PendingPushState.pending, t));      // 6
        store.enqueue(PendingPushRecord.ofNew(STREAM_A, "p-2", "xyz", PendingPushState.pending, t));         // 3
        store.enqueue(PendingPushRecord.ofNew(STREAM_A, "r-1", "qq", PendingPushState.preregister, t));      // 2
        store.enqueue(PendingPushRecord.ofNew(STREAM_B, "p-1", "ignore", PendingPushState.pending, t));      // unrelated

        assertThat(store.totalBytes(STREAM_A)).isEqualTo(11L);
    }

    @Test
    void initCreatesCompoundAndStreamIndexes() {
        MongoCollection<Document> coll = client.getDatabase(DB_NAME).getCollection(MongoPendingPushStore.COLLECTION);
        Set<String> indexNames = new HashSet<>();
        coll.listIndexes().forEach(d -> indexNames.add(d.getString("name")));

        // _id_ + at least the two named indexes the SPI requires
        assertThat(indexNames).contains(
                MongoPendingPushStore.IDX_STREAM_STATE_QUEUED_AT,
                MongoPendingPushStore.IDX_STREAM_ID
        );
    }
}
