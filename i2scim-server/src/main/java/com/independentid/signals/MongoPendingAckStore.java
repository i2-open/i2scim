package com.independentid.signals;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Mongo-backed implementation of {@link PendingAckStore}. The {@code pendingAcks}
 * collection holds undelivered ack JTIs keyed by {@code _id = "<streamId>:<jti>"}
 * so repeated enqueues are upserts. {@link #init()} creates a per-stream index
 * for the read path used at poll time and at restart.
 */
public final class MongoPendingAckStore implements PendingAckStore {

    private static final Logger logger = LoggerFactory.getLogger(MongoPendingAckStore.class);

    public static final String COLLECTION = "pendingAcks";
    public static final String IDX_STREAM_ID = "idx_streamId";

    private static final String F_ID = "_id";
    private static final String F_STREAM_ID = "streamId";
    private static final String F_JTI = "jti";
    private static final String F_APPLIED_AT = "appliedAt";

    private final MongoClient client;
    private final String dbName;
    private MongoCollection<Document> collection;

    public MongoPendingAckStore(MongoClient client, String dbName) {
        this.client = client;
        this.dbName = dbName;
    }

    public synchronized void init() {
        MongoDatabase db = client.getDatabase(dbName);
        this.collection = db.getCollection(COLLECTION);
        this.collection.createIndex(Indexes.ascending(F_STREAM_ID),
                new IndexOptions().name(IDX_STREAM_ID));
        logger.info("MongoPendingAckStore initialized on db={} collection={}", dbName, COLLECTION);
    }

    @Override
    public void enqueue(String streamId, String jti, Instant appliedAt) {
        Document doc = new Document()
                .append(F_ID, idOf(streamId, jti))
                .append(F_STREAM_ID, streamId)
                .append(F_JTI, jti)
                .append(F_APPLIED_AT, Date.from(appliedAt));
        collection.replaceOne(
                Filters.eq(F_ID, idOf(streamId, jti)),
                doc,
                new ReplaceOptions().upsert(true)
        );
    }

    @Override
    public List<PendingAckRecord> peekAll(String streamId) {
        List<PendingAckRecord> out = new ArrayList<>();
        collection.find(Filters.eq(F_STREAM_ID, streamId))
                .forEach(d -> out.add(new PendingAckRecord(
                        d.getString(F_STREAM_ID),
                        d.getString(F_JTI),
                        d.getDate(F_APPLIED_AT).toInstant()
                )));
        return out;
    }

    @Override
    public void delete(String streamId, String jti) {
        collection.deleteOne(Filters.eq(F_ID, idOf(streamId, jti)));
    }

    @Override
    public long count(String streamId) {
        return collection.countDocuments(Filters.eq(F_STREAM_ID, streamId));
    }

    private static String idOf(String streamId, String jti) {
        return streamId + ":" + jti;
    }
}
