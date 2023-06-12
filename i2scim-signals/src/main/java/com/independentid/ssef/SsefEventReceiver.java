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

package com.independentid.ssef;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.op.Operation;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Iterator;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * This class processes incoming events for application against the local server. It is started by EventHandler.
 */
@Singleton
@Priority(20)
@Default
@Startup // required to esnure independent startup
public class SsefEventReceiver implements Runnable {
    private final static Logger logger = LoggerFactory.getLogger(SsefEventReceiver.class);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public final static String MODE_REPLICATE = "replicas";
    public final static String MODE_SHARD = "sharded";


    //@Inject
    ConfigMgr cmgr = ConfigMgr.getConfig();

    @Inject
    SsefEventHandler eventHandler;

    Properties clIdProps = null;
    long lastTime = 0, offset = 0;

    CloseableHttpClient client = HttpClients.createDefault();

    public SsefEventReceiver() {

    }

    @PostConstruct
    void init() {

        logger.info("SCIM SSEF Event Receiver initializing");

        //Ensure Operation class parser ready to go...
        Operation.initialize(cmgr);

        Thread t = new Thread(this);
        t.start();  // Start the receiver thread!
    }

    @Override
    public void run() {
        logger.debug("SSEF replication receiver running.");

        while (!closed.get()) {
            if (eventHandler.ready) {
                if (logger.isDebugEnabled())
                    logger.debug("...polling Kafka for events");
                HttpPost eventPost = new HttpPost(eventHandler.postPollEventsUri);
                eventPost.setHeader(HttpHeaders.AUTHORIZATION, eventHandler.setStreamToken);

                SsefEventModels.PollRequest req = new SsefEventModels.PollRequest();
                req.ReturnImmediately = false;  // Set up for long polling.


                req.PrepareAcknowledgments(eventHandler.acksPending);

                try {
                    eventPost.setEntity(req.toEntity());
                } catch (JsonProcessingException | UnsupportedEncodingException e) {
                    logger.error("Error serializing request: " + e.getMessage());
                    req.RestoreAcks(eventHandler.acksPending);
                }
                try {
                    CloseableHttpResponse resp = this.client.execute(eventPost);

                    SsefEventModels.PollResponse pr = SsefEventModels.Parse(resp.getEntity());
                    if (pr.Sets.size() > 0) {
                        Iterator<String> setIter = pr.Sets.listIterator();
                        while (setIter.hasNext()) {
                            String setStr = setIter.next();


                            JsonNode opNode = EventMapper.ParseJwt(setStr);

                            eventHandler.consume(opNode);
                        }
                    }
                } catch (IOException e) {
                    logger.error("Error executing poll request: " + e.getMessage());
                    //Put the acks back so they can be acknowledged later.
                    req.RestoreAcks(eventHandler.acksPending);
                }
            }
        }

    }


    // Shutdown hook which can be called from a separate thread

    public void shutdown() {

        closed.set(true);
        try {
            this.client.close();
        } catch (IOException ignore) {

        }
        // store the current state on shut down offset and lasteventdate can be used for recovery


    }

}
