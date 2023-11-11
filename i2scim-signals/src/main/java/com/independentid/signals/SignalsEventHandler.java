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
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Priority;
import jakarta.ejb.Startup;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jose4j.jwt.MalformedClaimException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

//@ApplicationScoped
@Startup
@Singleton
@Priority(5)
@Named("SignalsEventHandler")
public class SignalsEventHandler implements IEventHandler {
    private final static Logger logger = LoggerFactory.getLogger(SignalsEventHandler.class);

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

    SignalsEventReceiver receiverThread;

    protected static final List<String> acksPending = Collections.synchronizedList(new ArrayList<>());
    protected static final List<Operation> pendingPubOps = Collections.synchronizedList(new ArrayList<>());

    protected static final List<Operation> acceptedOps = Collections.synchronizedList(new ArrayList<>());

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

    static boolean isErrorState = false;

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
            Thread.sleep(5000); // wait for server to settle
        } catch (InterruptedException ignore) {
        }
        SchemaManager mgr = configMgr.getSchemaManager();
        if (mgr == null) {
            logger.error("Signals event handler detected NULL schemamanager");
        }

        logger.info("Signals Event Handler STARTING....");

        // ensure that config,schema managers are initialized.
        Operation.initialize(configMgr);

        if (rcvEnabled) {
            logger.debug("Starting SET Polling Receiver...");
            this.receiverThread = new SignalsEventReceiver(configMgr, this, ssfClient);
        }
        ready = true;
    }

    public boolean notEnabled() {
        return !enabled || !eventsEnabled;
    }

    /*
    consume is called by SignalsEventReceiver for each event it receives. The event is mapped to an operation for processing.
     */
    public void consume(Object txn) {

        // This won't get called if rcvEnabled is false (because SignalsEventReceiver is not started)

        if (txn == null) {
            logger.warn("Ignoring invalid replication message.");
            return;
        }

        if (txn instanceof SecurityEventToken) {
            SecurityEventToken event = (SecurityEventToken) txn;
            Operation op = mapper.MapSetToOperation(event, configMgr.getSchemaManager());
            if (logger.isDebugEnabled())
                logger.debug("\tReceived SCIM Event:\n" + event.toPrettyString());
            try {
                ScimResource txnResource = backendHandler.getTransactionRecord(event.getTxn());
                if (txnResource != null) {
                    // Even though we've seen this, we want to ack it so we don't get it again.
                    acksPending.add(event.getJti());
                    logger.warn("Duplicate transaction detected, ignoring.");
                    return;
                }
            } catch (BackendException e) {
                logger.error("Backend error fetching transaction: " + e.getMessage());
                return;
            } catch (MalformedClaimException e) {
                // We want to ack the event so we don't get it again.
                acksPending.add(event.getJti());
                logger.error("Invalid txn value. Ignoring event");
                return;
            }
            acceptedOps.add(op);
            pool.addJobAndWait(op);
            acksPending.add(event.getJti());

        }
    }


    public FifoCache<Operation> getSendErrorOps() { return sendErrorOps; }

    public int getSendErrorCnt() { return sendErrorOps.size(); }

    private synchronized void produce(final Operation op) {
        if (pubEnabled) {
            RequestCtx ctx = op.getRequestCtx();
            if (ctx != null && ctx.isReplicaOp()) {
                if (logger.isDebugEnabled())
                    logger.debug("Ignoring internal event: " + op);
                return; // This is a replication op and should not be re-replicated!
            }
            if (logger.isDebugEnabled())
                logger.debug("Processing event: " + op);

            List<SecurityEventToken> events = mapper.MapOperationToSet(op);
            if (events != null && !events.isEmpty()) {
                for (SecurityEventToken token : events) {
                    if (!ssfClient.getPushStream().pushEvent(token))
                        sendErrorOps.add(op);
                }
                return;
            }
            sendErrorOps.add(op);
        }
    }

    /**
     * This method takes an operation and produces a stream.
     * @param op The {@link Operation} to be published
     */
    @Override
    public void publish(Operation op) {
        // Ignore search and get requests
        pendingPubOps.add(op);
        processBuffer();

    }

    private synchronized void processBuffer() {
        while (!pendingPubOps.isEmpty() && isProducing())
            produce(pendingPubOps.remove(0));
    }

    @Override
    public boolean isProducing() {
        return !isErrorState;
    }

    @Override
    @PreDestroy
    public void shutdown() {
        //repEmitter.complete();
        if (notEnabled())
            return;
        this.receiverThread.shutdown();

        processBuffer(); //Ensure all trans sent!

        try {
            this.ssfClient.getPushStream().Close();
        } catch (IOException ignore) {

        }
    }

}


