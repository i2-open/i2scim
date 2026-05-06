package com.independentid.signals;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Mongo-backed implementation of {@link PendingPushStore}. The {@code pendingPushes}
 * collection holds undelivered SETs keyed by {@code _id = "<streamId>:<jti>"} so
 * repeated enqueues are upserts. {@link #init()} creates the compound index used
 * by ordered drain plus the per-stream index used by storage monitoring.
 */
public final class MongoPendingPushStore implements PendingPushStore {

    private static final Logger logger = LoggerFactory.getLogger(MongoPendingPushStore.class);

    public static final String COLLECTION = "pendingPushes";
    public static final String IDX_STREAM_STATE_QUEUED_AT = "idx_stream_state_queuedAt";
    public static final String IDX_STREAM_ID = "idx_streamId";

    private static final String F_ID = "_id";
    private static final String F_STREAM_ID = "streamId";
    private static final String F_JTI = "jti";
    private static final String F_PAYLOAD = "payload";
    private static final String F_STATE = "state";
    private static final String F_QUEUED_AT = "queuedAt";
    private static final String F_ATTEMPT_COUNT = "attemptCount";
    private static final String F_LAST_ATTEMPT_AT = "lastAttemptAt";
    private static final String F_LAST_ERROR = "lastError";
    private static final String F_BYTES = "bytes";

    private final MongoClient client;
    private final String dbName;
    private MongoCollection<Document> collection;

    public MongoPendingPushStore(MongoClient client, String dbName) {
        this.client = client;
        this.dbName = dbName;
    }

    public synchronized void init() {
        MongoDatabase db = client.getDatabase(dbName);
        this.collection = db.getCollection(COLLECTION);

        IndexOptions compoundOpts = new IndexOptions().name(IDX_STREAM_STATE_QUEUED_AT);
        this.collection.createIndex(
                Indexes.compoundIndex(
                        Indexes.ascending(F_STREAM_ID),
                        Indexes.ascending(F_STATE),
                        Indexes.ascending(F_QUEUED_AT)
                ),
                compoundOpts
        );

        IndexOptions streamOpts = new IndexOptions().name(IDX_STREAM_ID);
        this.collection.createIndex(Indexes.ascending(F_STREAM_ID), streamOpts);

        logger.info("MongoPendingPushStore initialized on db={} collection={}", dbName, COLLECTION);
    }

    @Override
    public void enqueue(PendingPushRecord record) {
        Document doc = toDocument(record);
        collection.replaceOne(
                Filters.eq(F_ID, idOf(record.streamId(), record.jti())),
                doc,
                new ReplaceOptions().upsert(true)
        );
    }

    @Override
    public List<PendingPushRecord> peekOldest(String streamId, PendingPushState state, int limit) {
        Bson filter = Filters.and(
                Filters.eq(F_STREAM_ID, streamId),
                Filters.eq(F_STATE, state.name())
        );
        List<PendingPushRecord> out = new ArrayList<>(Math.min(limit, 32));
        collection.find(filter)
                .sort(Sorts.ascending(F_QUEUED_AT))
                .limit(limit)
                .forEach(d -> out.add(fromDocument(d)));
        return out;
    }

    @Override
    public void markAttempted(String streamId, String jti, Instant attemptedAt, String errorMsg) {
        Bson filter = Filters.eq(F_ID, idOf(streamId, jti));
        Bson update = Updates.combine(
                Updates.inc(F_ATTEMPT_COUNT, 1),
                Updates.set(F_LAST_ATTEMPT_AT, Date.from(attemptedAt)),
                Updates.set(F_LAST_ERROR, errorMsg)
        );
        collection.updateOne(filter, update, new UpdateOptions().upsert(false));
    }

    @Override
    public void transitionState(String streamId, String jti, PendingPushState newState) {
        Bson filter = Filters.eq(F_ID, idOf(streamId, jti));
        collection.updateOne(filter, Updates.set(F_STATE, newState.name()));
    }

    @Override
    public void delete(String streamId, String jti) {
        collection.deleteOne(Filters.eq(F_ID, idOf(streamId, jti)));
    }

    @Override
    public long count(String streamId, PendingPushState state) {
        return collection.countDocuments(
                Filters.and(
                        Filters.eq(F_STREAM_ID, streamId),
                        Filters.eq(F_STATE, state.name())
                )
        );
    }

    @Override
    public long totalBytes(String streamId) {
        long total = 0L;
        for (Document d : collection.find(Filters.eq(F_STREAM_ID, streamId))
                .projection(new Document(F_BYTES, 1))) {
            Number n = d.get(F_BYTES, Number.class);
            if (n != null) total += n.longValue();
        }
        return total;
    }

    private static String idOf(String streamId, String jti) {
        return streamId + ":" + jti;
    }

    private static Document toDocument(PendingPushRecord r) {
        Document d = new Document()
                .append(F_ID, idOf(r.streamId(), r.jti()))
                .append(F_STREAM_ID, r.streamId())
                .append(F_JTI, r.jti())
                .append(F_PAYLOAD, r.payload())
                .append(F_STATE, r.state().name())
                .append(F_QUEUED_AT, Date.from(r.queuedAt()))
                .append(F_ATTEMPT_COUNT, r.attemptCount())
                .append(F_BYTES, r.bytes());
        if (r.lastAttemptAt() != null) d.append(F_LAST_ATTEMPT_AT, Date.from(r.lastAttemptAt()));
        if (r.lastError() != null) d.append(F_LAST_ERROR, r.lastError());
        return d;
    }

    private static PendingPushRecord fromDocument(Document d) {
        Date last = d.getDate(F_LAST_ATTEMPT_AT);
        Number bytes = d.get(F_BYTES, Number.class);
        Number attempts = d.get(F_ATTEMPT_COUNT, Number.class);
        return new PendingPushRecord(
                d.getString(F_STREAM_ID),
                d.getString(F_JTI),
                d.getString(F_PAYLOAD),
                PendingPushState.valueOf(d.getString(F_STATE)),
                d.getDate(F_QUEUED_AT).toInstant(),
                attempts == null ? 0 : attempts.intValue(),
                last == null ? null : last.toInstant(),
                d.getString(F_LAST_ERROR),
                bytes == null ? 0L : bytes.longValue()
        );
    }
}
