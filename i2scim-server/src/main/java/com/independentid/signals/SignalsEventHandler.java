/*
 * Copyright (c) 2021.
 *
 * Confidential and Proprietary
 *
 * This unpublished source code may not be distributed outside
 * “Independent Identity Org”. without express written permission of
 * Phillip Hunt.
 *
 * People at companies that have signed necessary non-disclosure
 * agreements may only distribute to others in the company that are
 * bound by the same confidentiality agreement and distribution is
 * subject to the terms of such agreement.
 */

package com.independentid.signals;

import com.independentid.scim.backend.BackendException;
import com.independentid.scim.backend.BackendHandler;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.FifoCache;
import com.independentid.scim.core.InjectionManager;
import com.independentid.scim.core.PoolManager;
import com.independentid.scim.events.IEventHandler;
import com.independentid.scim.op.Operation;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.set.SecurityEventToken;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Priority;
import jakarta.ejb.Startup;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

@Startup
@Singleton
@Priority(5)
@Named("SignalsEventHandler")
public class SignalsEventHandler implements IEventHandler {
    private final static Logger logger = LoggerFactory.getLogger(SignalsEventHandler.class);

    public static final String MONGO_PROVIDER_FQCN = "com.independentid.scim.backend.mongo.MongoProvider";

    @ConfigProperty(name = "scim.signals.enable", defaultValue = "false")
    boolean enabled;

    @ConfigProperty(name = "scim.event.enable", defaultValue = "true")
    boolean eventsEnabled;

    @ConfigProperty(name = "scim.signals.pub.enable", defaultValue = "true")
    boolean pubEnabled;

    @ConfigProperty(name = "scim.signals.rcv.enable", defaultValue = "true")
    boolean rcvEnabled;

    @ConfigProperty(name = "scim.signals.pub.types", defaultValue = "*")
    Optional<List<String>> pubTypes;

    @ConfigProperty(name = "scim.signals.rcv.types", defaultValue = "*")
    Optional<List<String>> rcvTypes;

    @ConfigProperty(name = "scim.signals.test", defaultValue = "false")
    boolean isTest;

    @ConfigProperty(name = "scim.prov.providerClass", defaultValue = "com.independentid.scim.backend.memory.MemoryProvider")
    String providerClassName;

    @ConfigProperty(name = "scim.prov.mongo.uri", defaultValue = "mongodb://localhost:27017")
    String mongoUri;

    @ConfigProperty(name = "scim.prov.mongo.dbname", defaultValue = "SCIM")
    String mongoDbName;

    @ConfigProperty(name = "scim.prov.memory.dir", defaultValue = "./scimdata")
    String memoryDir;

    SignalsEventReceiver receiverThread;

    protected static final List<Operation> acceptedOps = new CopyOnWriteArrayList<>();

    protected static final FifoCache<Operation> sendErrorOps = new FifoCache<>(1024);

    @Inject
    ConfigMgr configMgr;

    @Inject
    PoolManager pool;

    @Inject
    BackendHandler backendHandler;

    @Inject
    StreamConfigProps configProps;

    SsfHandler ssfClient;

    SignalsEventMapper mapper;

    StreamMaintenanceScheduler scheduler;

    PendingPushStore pendingPushStore;
    PendingAckStore pendingAckStore;
    MongoClient signalsMongoClient; // only set when scim.prov.providerClass=Mongo
    PushRetryWorker pushRetryWorker;

    boolean ready = false;

    public SignalsEventHandler() {
        logger.info("Signals Event Handler constructor called.");
    }

    @Override
    @PostConstruct
    public void init() {
        if (!this.enabled) {
            logger.info("Signals Event Handler *disabled*");
            return;
        }

        try {
            this.ssfClient = SsfHandler.Open(configProps);
        } catch (IOException e) {
            logger.error("Problem opening event steam client: " + e.getMessage(), e);
            throw new RuntimeException(e);
        }

        List<String> rcvCfgTypes;
        if (rcvTypes.isPresent()) {
            rcvCfgTypes = rcvTypes.get();
        } else {
            rcvCfgTypes = new ArrayList<>();
            rcvCfgTypes.add("*");
        }

        List<String> pubCfgTypes;
        if (pubTypes.isPresent()) {
            pubCfgTypes = pubTypes.get();
        } else {
            pubCfgTypes = new ArrayList<>();
            pubCfgTypes.add("*");
        }

        this.mapper = new SignalsEventMapper(pubCfgTypes, rcvCfgTypes, InjectionManager.getInstance().getGenerator());

        try {
            if (isTest)
                Thread.sleep(100);
            else
                Thread.sleep(5000);
        } catch (InterruptedException ignore) {
        }
        SchemaManager mgr = configMgr.getSchemaManager();
        if (mgr == null) {
            logger.error("Signals event handler detected NULL schemamanager");
        }

        logger.info("Signals Event Handler STARTING....");

        Operation.initialize(configMgr);

        this.pendingPushStore = createPendingPushStore();
        this.pendingAckStore = createPendingAckStore();

        this.scheduler = new StreamMaintenanceScheduler();
        installStatusInterrogation();
        wireAckStoreToPollStream();

        startPushRetryWorker();

        if (rcvEnabled) {
            logger.debug("Starting SET Polling Receiver...");
            this.receiverThread = new SignalsEventReceiver(configMgr, this, ssfClient);
        }
        ready = true;
    }

    private PendingPushStore createPendingPushStore() {
        if (MONGO_PROVIDER_FQCN.equals(providerClassName)) {
            logger.info("Creating MongoPendingPushStore (uri={}, db={})", mongoUri, mongoDbName);
            this.signalsMongoClient = MongoClients.create(mongoUri);
            MongoPendingPushStore store = new MongoPendingPushStore(signalsMongoClient, mongoDbName);
            store.init();
            return store;
        }
        // Memory provider — pending-push queue is durable on disk under <scim.prov.memory.dir>/events/.
        // NOTE: SCIM resource state under the same memory.dir is NOT durable across crash/restart
        // for the in-memory provider; only the signals queue survives. See docs/Configuration.md.
        Path eventsRoot = Paths.get(memoryDir);
        logger.info("Creating FilePendingPushStore (memory provider) under {}/events", eventsRoot.toAbsolutePath());
        FilePendingPushStore store = new FilePendingPushStore(eventsRoot);
        store.init();
        return store;
    }

    /**
     * PRD-B slice #78: poll-side ack durability. Mirrors
     * {@link #createPendingPushStore} — Mongo when the SCIM backend is Mongo,
     * filesystem under {@code <scim.prov.memory.dir>/events/acks/} otherwise.
     * Reuses the same {@link MongoClient} as the push store when Mongo.
     */
    private PendingAckStore createPendingAckStore() {
        if (MONGO_PROVIDER_FQCN.equals(providerClassName)) {
            // signalsMongoClient was set by createPendingPushStore() above.
            if (this.signalsMongoClient == null) {
                this.signalsMongoClient = MongoClients.create(mongoUri);
            }
            MongoPendingAckStore store = new MongoPendingAckStore(signalsMongoClient, mongoDbName);
            store.init();
            return store;
        }
        Path eventsRoot = Paths.get(memoryDir);
        logger.info("Creating FilePendingAckStore (memory provider) under {}/events/acks", eventsRoot.toAbsolutePath());
        FilePendingAckStore store = new FilePendingAckStore(eventsRoot);
        store.init();
        return store;
    }

    private void wireAckStoreToPollStream() {
        if (ssfClient == null) return;
        PollStream poll = ssfClient.getPollStream();
        if (poll == null) return;
        poll.ackStore = this.pendingAckStore;
    }

    private void startPushRetryWorker() {
        if (!pubEnabled || ssfClient == null) return;
        PushStream pushStream = ssfClient.getPushStream();
        if (pushStream == null || !pushStream.enabled) return;
        this.pushRetryWorker = new PushRetryWorker(pushStream, pendingPushStore);
        this.pushRetryWorker.start();
    }

    private void installStatusInterrogation() {
        PushStream push = ssfClient.getPushStream();
        if (push != null && push.enabled) {
            if (push.client == null) push.setSslContext(null);
            push.statusResolver = new StatusEndpointResolver(push.client);
            push.issuerKeyReloader = configProps::getIssuerPrivateKey;
            String key = "push:" + (push.streamId == null ? "default" : push.streamId);
            Runnable idleVerifyRun = () -> runIdleVerify(push);
            push.state.addTransitionListener((oldS, newS) -> {
                if (newS == StreamStatus.PAUSED) {
                    scheduler.schedulePausedRecheck(key, push::pausedRecheck,
                            Duration.ofMillis(push.statusCheckInterval));
                    scheduler.cancelIdleVerify(key);
                } else if (oldS == StreamStatus.PAUSED) {
                    scheduler.cancelPausedRecheck(key);
                    if (newS == StreamStatus.ENABLED) {
                        scheduler.scheduleIdleVerify(key, idleVerifyRun,
                                Duration.ofMillis(push.idleVerifyInterval));
                    }
                } else if (newS == StreamStatus.DISABLED) {
                    scheduler.cancelIdleVerify(key);
                }
            });
            if (push.state.getStatus() == StreamStatus.ENABLED) {
                scheduler.scheduleIdleVerify(key, idleVerifyRun,
                        Duration.ofMillis(push.idleVerifyInterval));
            }
        }
        PollStream poll = ssfClient.getPollStream();
        if (poll != null && poll.enabled) {
            if (poll.client == null) poll.setSslContext(null);
            poll.statusResolver = new StatusEndpointResolver(poll.client);
            String key = "poll:" + (poll.streamId == null ? "default" : poll.streamId);
            poll.state.addTransitionListener((oldS, newS) -> {
                if (newS == StreamStatus.PAUSED) {
                    scheduler.schedulePausedRecheck(key, poll::pausedRecheck,
                            Duration.ofMillis(poll.statusCheckInterval));
                } else if (oldS == StreamStatus.PAUSED) {
                    scheduler.cancelPausedRecheck(key);
                }
            });
        }
    }

    private void runIdleVerify(PushStream push) {
        if (push.state.getStatus() != StreamStatus.ENABLED) return;
        Instant last = push.getLastSuccessfulPush();
        if (last == null) return;
        long idleMs = Duration.between(last, Instant.now()).toMillis();
        if (idleMs < push.idleVerifyInterval) return;
        SecurityEventToken verify = VerifyEventBuilder.build(push);
        logger.info("Idle threshold reached on push stream; sending verify event jti={}", verify.getJti());
        push.pushEvent(verify);
    }

    public boolean notEnabled() {
        return !enabled || !eventsEnabled;
    }

    public void consume(Object txn) {
        if (txn == null) {
            logger.warn("Ignoring invalid replication message.");
            return;
        }

        if (txn instanceof SecurityEventToken) {
            SecurityEventToken event = (SecurityEventToken) txn;
            Operation op = mapper.MapSetToOperation(event, configMgr.getSchemaManager());
            if (logger.isDebugEnabled())
                logger.debug("\tReceived SCIM Event:\n" + event.toPrettyString());
            if (op == null) {
                if (logger.isDebugEnabled())
                    logger.debug("Acking non-provisioning event jti={}", event.getJti());
                recordAck(this.pendingAckStore, ssfClient.getPollStream(), event.getJti());
                return;
            }
            try {
                String tranId = event.getTxn();
                if (tranId != null) {
                    ScimResource txnResource = backendHandler.getTransactionRecord(tranId);
                    if (txnResource != null) {
                        recordAck(this.pendingAckStore, ssfClient.getPollStream(), event.getJti());
                        logger.warn("Duplicate transaction detected, ignoring.");
                        return;
                    }
                }
            } catch (BackendException e) {
                logger.error("Backend error fetching transaction: " + e.getMessage());
                return;
            } catch (MalformedClaimException e) {
                recordAck(this.pendingAckStore, ssfClient.getPollStream(), event.getJti());
                logger.error("Invalid txn value. Ignoring event");
                return;
            }
            acceptedOps.add(op);
            pool.addJobAndWait(op);
            recordAck(this.pendingAckStore, ssfClient.getPollStream(), event.getJti());
        }
    }

    public FifoCache<Operation> getSendErrorOps() { return sendErrorOps; }

    public int getSendErrorCnt() { return sendErrorOps.size(); }

    /**
     * PRD-B slice #73: single inline {@link PushStream#attemptOnce} per SET; on
     * any non-Success outcome the SET is enqueued to {@link PendingPushStore}
     * and the worker drives retry. The producer thread MUST NOT block on push
     * health, throw, or drop events — those invariants are the whole point of
     * the durability slice.
     */
    @Override
    public void publish(Operation op) {
        if (!pubEnabled || ssfClient == null) return;
        RequestCtx ctx = op.getRequestCtx();
        if (ctx != null && ctx.isReplicaOp()) return;
        PushStream push = ssfClient.getPushStream();
        if (push == null) return;

        List<SecurityEventToken> events = mapper.MapOperationToSet(op);
        if (events == null || events.isEmpty()) return;

        for (SecurityEventToken token : events) {
            attemptInlineAndEnqueueOnFailure(push, pendingPushStore, token, op);
        }
    }

    /**
     * Visible for testing. Performs one {@link PushStream#attemptOnce} and, on
     * any non-Success result, signs the SET with the stream's current issuer
     * key (which {@code attemptOnce} may have just reloaded) and enqueues it
     * for the retry worker.
     */
    static void attemptInlineAndEnqueueOnFailure(PushStream push,
                                                 PendingPushStore store,
                                                 SecurityEventToken token,
                                                 Operation originatingOp) {
        AttemptResult result = push.attemptOnce(token);
        if (result instanceof AttemptResult.Success) return;

        String signed = signForStorage(push, token);
        if (signed == null) {
            sendErrorOps.add(originatingOp);
            return;
        }
        try {
            store.enqueue(PendingPushRecord.ofNew(
                    push.streamId,
                    token.getJti(),
                    signed,
                    PendingPushState.pending,
                    Instant.now()
            ));
        } catch (RuntimeException re) {
            logger.error("Failed to enqueue pending push (streamId={}, jti={}): {}",
                    push.streamId, token.getJti(), re.getMessage(), re);
            sendErrorOps.add(originatingOp);
        }
    }

    /**
     * PRD-B slice #78: write a poll-side ack into the durable {@link PendingAckStore}
     * under the receiving stream's id. No-op if either the store or the stream's id
     * is unavailable (e.g., pre-registration window) — receivers fall back to
     * delivering acks on the next poll once the store is wired and the stream id is
     * known.
     */
    static void recordAck(PendingAckStore store, PollStream poll, String jti) {
        if (store == null || poll == null || poll.streamId == null) return;
        try {
            store.enqueue(poll.streamId, jti, Instant.now());
        } catch (RuntimeException re) {
            logger.error("Failed to enqueue pending ack (streamId={}, jti={}): {}",
                    poll.streamId, jti, re.getMessage(), re);
        }
    }

    private static String signForStorage(PushStream push, SecurityEventToken token) {
        if (push.aud != null) token.setAud(push.aud);
        token.setIssuer(push.iss);
        try {
            return token.JWS(push.issuerKey);
        } catch (JoseException | MalformedClaimException e) {
            logger.error("Cannot sign SET for storage (streamId={}, jti={}): {}",
                    push.streamId, token.getJti(), e.getMessage());
            return null;
        }
    }

    @Override
    public boolean isProducing() {
        if (ssfClient == null || ssfClient.getPushStream() == null)
            return true;
        return ssfClient.getPushStream().state.getStatus() == StreamStatus.ENABLED;
    }

    @Override
    @PreDestroy
    public void shutdown() {
        if (notEnabled())
            return;
        if (this.receiverThread != null) this.receiverThread.shutdown();
        if (this.pushRetryWorker != null) this.pushRetryWorker.shutdown();

        try {
            if (this.ssfClient != null && this.ssfClient.getPushStream() != null)
                this.ssfClient.getPushStream().Close();
        } catch (IOException ignore) {
        }
        if (this.scheduler != null) {
            this.scheduler.shutdown();
        }
        if (this.signalsMongoClient != null) {
            try { this.signalsMongoClient.close(); } catch (RuntimeException ignore) {}
        }
        acceptedOps.clear();
    }
}
