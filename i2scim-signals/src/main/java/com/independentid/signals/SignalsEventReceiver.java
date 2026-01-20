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

    Thread pollingThread;

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

        pollingThread = new Thread(this);
        pollingThread.start();  // Start the receiver thread!
    }

    @Override
    public void run() {
        logger.info("Signals polling receiver running.");
        try {
            // When in test mode, give the web server a chance to start.
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            // Only exit if shutdown was actually requested
            if (closeRequest.get()) {
                logger.info("Polling receiver interrupted during startup - shutdown requested");
                closed.set(true);
                return;
            }
            // Otherwise, clear interrupt status and continue
            logger.debug("Polling receiver interrupted during startup sleep, continuing");
            Thread.interrupted(); // Clear the interrupt status
        }

        while (!closeRequest.get() && !this.ssfClient.getPollStream().errorState) {
            if (eventHandler.ready) {
                try {
                    Map<String, SecurityEventToken> events = this.ssfClient.getPollStream().pollEvents(SignalsEventHandler.acksPending, false);

                    for (SecurityEventToken event : events.values()) {
                        eventHandler.consume(event);
                    }
                } catch (Exception e) {
                    if (closeRequest.get()) {
                        logger.info("Polling interrupted during event processing - shutdown requested");
                        break;
                    }
                    // Check if interrupted but shutdown not requested - clear and continue
                    if (Thread.currentThread().isInterrupted()) {
                        logger.debug("Thread interrupted but no shutdown requested, clearing interrupt status");
                        Thread.interrupted(); // Clear interrupt status
                    }
                    logger.warn("Error processing events: " + e.getMessage());
                }
            }
        }
        logger.info("Polling receiver shutting down");
        closed.set(true);

    }


    // Shutdown hook which can be called from a separate thread

    public void shutdown() {
        logger.info("Shutdown requested for polling receiver");
        closeRequest.set(true);

        // Interrupt the polling thread to break out of any blocking operations
        if (pollingThread != null && pollingThread.isAlive()) {
            pollingThread.interrupt();
        }

        // Wait for the thread to finish with a timeout
        int maxWaitSeconds = 10;
        int waitCount = 0;
        while (!closed.get() && waitCount < maxWaitSeconds * 10) {
            waitCount++;
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignore) {
            }
        }

        if (!closed.get()) {
            logger.warn("Polling thread did not shut down within " + maxWaitSeconds + " seconds. Forcing shutdown.");
        } else {
            logger.info("Polling receiver shut down successfully");
        }

        // Now ack any processed events....
        if (!SignalsEventHandler.acksPending.isEmpty()) {
            try {
                this.ssfClient.getPollStream().pollEvents(SignalsEventHandler.acksPending, true);
            } catch (Exception e) {
                logger.warn("Error acknowledging pending events during shutdown: " + e.getMessage());
            }
        }

        try {
            this.ssfClient.getPollStream().Close();
        } catch (IOException ignore) {

        }
        // store the current state on shut down offset and lasteventdate can be used for recovery


    }

}
