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

package com.independentid.scim.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.op.Operation;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * This class processes incoming events for application against the local server. It is started by EventHandler.
 */
public class KafkaEventReceiver implements Runnable{
    private final static Logger logger = LoggerFactory.getLogger(KafkaEventReceiver.class);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final KafkaConsumer<String, JsonNode> consumer;
    KafkaRepEventHandler eventHandler;
    List<Operation> repErrorOps = Collections.synchronizedList(new ArrayList<>());
    List<Operation> received = Collections.synchronizedList(new ArrayList<>());

    public KafkaEventReceiver(KafkaConsumer<String, JsonNode> consumer, KafkaRepEventHandler eventHandler) {
        this.consumer = consumer;
        this.repErrorOps = KafkaRepEventHandler.repErrorOps;
        this.received = KafkaRepEventHandler.pendingOps;
        this.eventHandler = eventHandler;
    }

    @Override
    public void run() {
        try {
            while (!closed.get()) {
                ConsumerRecords<String,JsonNode> records = consumer.poll(Duration.ofMillis(1000));

                for (ConsumerRecord<String, JsonNode> record : records) {
                    if (logger.isDebugEnabled())
                       logger.debug("Received events for key: "+record.key());
                    if (record.value() == null || record.value().toString().equals("null")) {
                        logger.warn("Received NULL event:"+record);
                        continue;
                    }
                    eventHandler.consume(record.value());
                }
            }
        } catch (WakeupException e) {
            // Ignore exception if closing
            if (!closed.get()) throw e;
        } finally {
            consumer.close();
        }
    }

    // Shutdown hook which can be called from a separate thread
    public void shutdown() {
        closed.set(true);
        consumer.wakeup();
    }

}
