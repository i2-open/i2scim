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
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.FifoCache;
import com.independentid.scim.op.Operation;
import io.quarkus.runtime.Startup;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.header.Header;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Priority;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * This class processes incoming events for application against the local server. It is started by EventHandler.
 */
@Singleton
@Priority(20)
@Startup // required to esnure independent startup
public class KafkaEventReceiver implements Runnable{
    private final static Logger logger = LoggerFactory.getLogger(KafkaEventReceiver.class);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private  KafkaConsumer<String, JsonNode> consumer;

    FifoCache<Operation> repErrorOps;
    List<Operation> received;

    @Inject
    ConfigMgr cmgr;

    @Inject
    KafkaRepEventHandler eventHandler;

    @Inject
    Config sysconf;

    @ConfigProperty(name = "scim.kafka.rep.enable", defaultValue = "false")
    boolean enabled;

    @ConfigProperty (name="scim.event.enable", defaultValue = "true")
    boolean eventsEnabled;

    @ConfigProperty (name = "scim.kafka.rep.bootstrap",defaultValue="localhost:9092")
    String bootstrapServers;

    @ConfigProperty (name= "scim.kafka.rep.client.id")
    String clientId;

    public KafkaEventReceiver() {

    }

    @PostConstruct
    void init() {
        if (notEnabled()) // exit if events or replication disabled
            return;
        logger.debug("Kafka Replication receiver initializing...");
        this.repErrorOps = eventHandler.sendErrorOps;
        this.received = KafkaRepEventHandler.pendingPubOps;

        Iterable<String> iter = sysconf.getPropertyNames();
        Properties conProps = new Properties();
        List<String> ctopics = null;
        conProps.put(ConsumerConfig.CLIENT_ID_CONFIG,clientId);
        conProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,bootstrapServers);

        for (String name : iter) {

            if (name.startsWith(KafkaRepEventHandler.KAFKA_CON_PREFIX)) {
                String cprop = name.substring(KafkaRepEventHandler.KAFKA_CON_PREFIX.length());
                if (cprop.equals("topics"))
                    ctopics = Arrays.asList(sysconf.getValue(name,String.class).split(", "));
                else
                    conProps.put(cprop, sysconf.getValue(name,String.class));
            }
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Kafka replication receiver properties...");
            for (String key : conProps.stringPropertyNames())
                logger.debug("\t"+key+":\t"+conProps.getProperty(key));
        }
        if (ctopics == null) {
            logger.warn("Configuration property "+KafkaRepEventHandler.KAFKA_CON_PREFIX+"topics undefined. Defaulting to 'rep'");
            ctopics = Collections.singletonList("rep");
        }

        consumer = new KafkaConsumer<>(conProps);
        consumer.subscribe(ctopics);

        //Ensure Operation class parser ready to go...
        Operation.initialize(cmgr);

        Thread t = new Thread(this);
        t.start();  // Start the receiver thread!
    }

    @Override
    public void run() {
        logger.debug("Kafka replication receiver running.");
        try {
            while (!closed.get()) {
                if (logger.isDebugEnabled())
                    logger.debug("...polling Kafka for events");
                ConsumerRecords<String,JsonNode> records = consumer.poll(Duration.ofMillis(15000));
               
                for (ConsumerRecord<String, JsonNode> record : records) {
                    if (logger.isDebugEnabled())
                       logger.debug("Received events for key: "+record.key());
                    String hdrClient = null, hdrGroup = null,hdrTran;
                    for (Header header : record.headers()) {
                        String key = header.key();
                        switch(key) {
                            case KafkaRepEventHandler.HDR_CLIENT:
                                hdrClient = new String(header.value());
                                if (logger.isDebugEnabled())
                                    logger.debug("\tSender Client:\t"+hdrClient);
                                continue;
                            case KafkaRepEventHandler.HDR_CLUSTER:
                                hdrGroup = new String(header.value());
                                if (logger.isDebugEnabled())
                                    logger.debug("\tSender Group:\t"+hdrGroup);
                                continue;
                            case KafkaRepEventHandler.HDR_TID:
                                hdrTran = new String(header.value());
                                if (logger.isDebugEnabled())
                                    logger.debug("\tTransactionId:\t"+hdrTran);
                                continue;
                            default:
                                // ignore
                        }
                    }
                    if (record.value() == null || record.value().toString().equals("null")) {
                        logger.warn("Received NULL event:"+record);
                        continue;
                    }
                    switch (eventHandler.getStrategy()) {
                        case KafkaRepEventHandler.STRAT_GLOBAL:
                            // ignore if event originates in cluster or same clientid
                            if (eventHandler.clusterId.equals(hdrGroup)
                                    || eventHandler.clientId.equals(hdrClient)) {
                                eventHandler.ignoredOps.add(record);
                                continue; //ignore the event
                            }
                            break;
                        case KafkaRepEventHandler.STRAT_CLUS:
                            // ignore if clusterid does not match or client.id does match
                            if (!eventHandler.clusterId.equals(hdrGroup)
                                    || eventHandler.clientId.equals(hdrClient)) {
                                eventHandler.ignoredOps.add(record);
                                continue; //ignore the event (not in cluster or self)
                            }
                            break;
                        case KafkaRepEventHandler.STRAT_ALL:
                            // ignore only if client id matches
                            if (eventHandler.clientId.equals(hdrClient)) {
                                eventHandler.ignoredOps.add(record);
                                continue; //ignore the event (produced by self)
                            }
                            break;
                        case KafkaRepEventHandler.STRAT_TRAN:
                            // do nothing - tranId checked later.

                    }
                    eventHandler.consume(record.value());
                }
            }
        } catch (WakeupException e) {
            // Ignore exception if closing
            if (!closed.get()) throw e;
        } finally {
            consumer.commitSync();
            consumer.close();
        }
    }

    public boolean notEnabled() {
        return !enabled || !eventsEnabled;
    }

    // Shutdown hook which can be called from a separate thread
    @PreDestroy
    public void shutdown() {
        if (notEnabled())
            return;
        closed.set(true);
        consumer.wakeup();
    }

}
