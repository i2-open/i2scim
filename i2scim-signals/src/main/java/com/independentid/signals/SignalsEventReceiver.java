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

import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.op.Operation;
import com.independentid.set.SecurityEventToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * This class processes incoming events for application against the local server. It is started by EventHandler.
 */
// required to ensure independent startup
public class SignalsEventReceiver implements Runnable {
    private final static Logger logger = LoggerFactory.getLogger(SignalsEventReceiver.class);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean closeRequest = new AtomicBoolean(false);

    ConfigMgr cmgr;

    SignalsEventHandler eventHandler;

    SsfHandler ssfClient;

    public SignalsEventReceiver(ConfigMgr config, SignalsEventHandler handler, SsfHandler client) {
        this.cmgr = config;
        this.eventHandler = handler;
        this.ssfClient = client;
        Operation.initialize(config);
        init();
    }

    void init() {

        logger.info("scim.signals.Event Receiver initializing");

        //Ensure Operation class parser ready to go...
        Operation.initialize(cmgr);

        Thread t = new Thread(this);
        t.start();  // Start the receiver thread!
    }

    @Override
    public void run() {
        logger.info("Signals polling receiver running.");
        try {
            // When in test mode, give the web server a chance to start.
            Thread.sleep(2000);
        } catch (InterruptedException ignore) {
        }

        while (!closeRequest.get() && !this.ssfClient.getPollStream().errorState) {
            if (eventHandler.ready) {
                Map<String, SecurityEventToken> events = this.ssfClient.getPollStream().pollEvents(SignalsEventHandler.acksPending, false);

                for (SecurityEventToken event : events.values()) {
                    eventHandler.consume(event);
                }
            }
        }
        closed.set(true);

    }


    // Shutdown hook which can be called from a separate thread

    public void shutdown() {
        closeRequest.set(true);
        int i = 0;
        while (!closed.get()) {
            i++;
            if (i > 10) {
                logger.info("Waiting for polling thread to close...");
                i = 0;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignore) {
            }
        }

        // Now ack any processed events....
        if (!SignalsEventHandler.acksPending.isEmpty()) {
            this.ssfClient.getPollStream().pollEvents(SignalsEventHandler.acksPending, true);
        }

        try {
            this.ssfClient.getPollStream().Close();
        } catch (IOException ignore) {

        }
        // store the current state on shut down offset and lasteventdate can be used for recovery


    }

}
