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

    SignalsEventReceiver receiverThread;

    protected static final List<String> acksPending = Collections.synchronizedList(new ArrayList<>());
    protected static final List<Operation> pendingPubOps = Collections.synchronizedList(new ArrayList<>());

    protected static final List<Operation> acceptedOps = Collections.synchronizedList(new ArrayList<>());

    protected static final FifoCache<Operation> sendErrorOps = new FifoCache<>(1024);

    @Inject
    ConfigMgr configMgr;

    @Inject
    SchemaManager schemaManager;

    @Inject
    PoolManager pool;

    @Inject
    StreamHandler streams;

    @Inject
    BackendHandler backendHandler;

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

        logger.info("Signals Event Handler STARTING....");

        // ensure that config,schema managers are initialized.
        Operation.initialize(configMgr);

        logger.debug("Starting SET Polling Receiver...");
        this.receiverThread = new SignalsEventReceiver(configMgr, this, streams);

        ready = true;
    }

    public boolean notEnabled() {
        return !enabled || !eventsEnabled;
    }

    /*
    consume is called by SignalsEventReceiver for each event it receives. The event is mapped to an operation for processing.
     */
    public void consume(Object txn) {

        if (txn == null) {
            logger.warn("Ignoring invalid replication message.");
            return;
        }

        if (txn instanceof SecurityEventToken) {
            SecurityEventToken event = (SecurityEventToken) txn;
            Operation op = SignalsEventMapper.MapSetToOperation(event, schemaManager);
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
        RequestCtx ctx = op.getRequestCtx();
        if (ctx != null && ctx.isReplicaOp()) {
            if (logger.isDebugEnabled())
                logger.debug("Ignoring internal event: " + op);
            return; // This is a replication op and should not be re-replicated!
        }
        if (logger.isDebugEnabled())
            logger.debug("Processing event: " + op);

        SecurityEventToken token = SignalsEventMapper.MapOperationToSet(op);
        if (token != null) {
            if (streams.pushStream.pushEvent(token)) return;
        }
        sendErrorOps.add(op);
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
        while(pendingPubOps.size()>0 && isProducing())
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
            this.streams.pushStream.Close();
        } catch (IOException ignore) {

        }
    }

}


