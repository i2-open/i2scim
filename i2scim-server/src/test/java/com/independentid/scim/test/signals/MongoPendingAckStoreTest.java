package com.independentid.scim.test.signals;

import com.independentid.signals.MongoPendingAckStore;
import com.independentid.signals.PendingAckRecord;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MongoPendingAckStoreTest {

    private static final String DB_NAME = "i2scimPendingAckTest";
    private static final String STREAM_A = "stream-a";
    private static final String STREAM_B = "stream-b";

    private static MongoDBContainer mongo;
    private static MongoClient client;
    private static MongoPendingAckStore store;

    @BeforeAll
    static void start() {
        mongo = new MongoDBContainer(DockerImageName.parse("mongo:8.0"));
        mongo.start();
        client = MongoClients.create(mongo.getReplicaSetUrl());
        store = new MongoPendingAckStore(client, DB_NAME);
        store.init();
    }

    @AfterAll
    static void stop() {
        if (client != null) client.close();
        if (mongo != null) mongo.stop();
    }

    @BeforeEach
    void clearCollection() {
        client.getDatabase(DB_NAME).getCollection(MongoPendingAckStore.COLLECTION).deleteMany(new Document());
    }

    @Test
    void enqueueIsIdempotentOnStreamIdJti() {
        Instant t1 = Instant.parse("2026-05-06T10:00:00Z");
        Instant t2 = Instant.parse("2026-05-06T11:00:00Z");
        store.enqueue(STREAM_A, "jti-1", t1);
        store.enqueue(STREAM_A, "jti-1", t2);

        assertThat(store.count(STREAM_A)).isEqualTo(1L);
        List<PendingAckRecord> records = store.peekAll(STREAM_A);
        assertThat(records).hasSize(1);
        assertThat(records.get(0).appliedAt()).isEqualTo(t2);
    }

    @Test
    void peekAllAndCountAreScopedByStreamId() {
        Instant t = Instant.parse("2026-05-06T10:00:00Z");
        store.enqueue(STREAM_A, "jti-A", t);
        store.enqueue(STREAM_B, "jti-B1", t);
        store.enqueue(STREAM_B, "jti-B2", t);

        assertThat(store.count(STREAM_A)).isEqualTo(1L);
        assertThat(store.count(STREAM_B)).isEqualTo(2L);
        assertThat(store.peekAll(STREAM_A)).extracting(PendingAckRecord::jti).containsExactly("jti-A");
        assertThat(store.peekAll(STREAM_B)).extracting(PendingAckRecord::jti)
                .containsExactlyInAnyOrder("jti-B1", "jti-B2");
    }

    @Test
    void deleteRemovesOnlyTheTargetedRow() {
        Instant t = Instant.parse("2026-05-06T10:00:00Z");
        store.enqueue(STREAM_A, "jti-keep-1", t);
        store.enqueue(STREAM_A, "jti-delete", t);
        store.enqueue(STREAM_A, "jti-keep-2", t);

        store.delete(STREAM_A, "jti-delete");

        assertThat(store.count(STREAM_A)).isEqualTo(2L);
        assertThat(store.peekAll(STREAM_A)).extracting(PendingAckRecord::jti)
                .containsExactlyInAnyOrder("jti-keep-1", "jti-keep-2");
    }

    @Test
    void deleteOfMissingRowIsNoOp() {
        store.delete(STREAM_A, "never-existed");
        assertThat(store.count(STREAM_A)).isZero();
    }

    @Test
    void initCreatesStreamIdIndex() {
        java.util.Set<String> indexNames = new java.util.HashSet<>();
        client.getDatabase(DB_NAME).getCollection(MongoPendingAckStore.COLLECTION)
                .listIndexes()
                .forEach(d -> indexNames.add(d.getString("name")));
        assertThat(indexNames).contains(MongoPendingAckStore.IDX_STREAM_ID);
    }

    @Test
    void rowsSurviveFreshClientAgainstSameDb() {
        Instant t = Instant.parse("2026-05-06T10:00:00Z");
        for (int i = 0; i < 25; i++) {
            store.enqueue(STREAM_A, "jti-" + i, t.plusSeconds(i));
        }

        // Simulate a restart by opening a fresh client + store against the same Mongo.
        try (MongoClient reopenedClient = MongoClients.create(mongo.getReplicaSetUrl())) {
            MongoPendingAckStore reopened = new MongoPendingAckStore(reopenedClient, DB_NAME);
            reopened.init();
            assertThat(reopened.count(STREAM_A)).isEqualTo(25L);
            assertThat(reopened.peekAll(STREAM_A)).extracting(PendingAckRecord::jti).hasSize(25);
        }
    }
}
