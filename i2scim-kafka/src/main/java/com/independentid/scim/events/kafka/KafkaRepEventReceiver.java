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

package com.independentid.scim.events.kafka;

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
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * This class processes incoming events for application against the local server. It is started by EventHandler.
 */
@Singleton
@Priority(20)
@Startup // required to esnure independent startup
public class KafkaRepEventReceiver implements Runnable{
    private final static Logger logger = LoggerFactory.getLogger(KafkaRepEventReceiver.class);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public final static String MODE_REPLICATE = "replicas";
    public final static String MODE_SHARD = "sharded";

    private  KafkaConsumer<String, JsonNode> consumer;

    FifoCache<Operation> repErrorOps;
    List<Operation> received;

    @Inject
    ConfigMgr cmgr;

    @Inject
    KafkaRepEventHandler eventHandler;

    @Inject
    Config sysconf;

    @ConfigProperty (name="scim.kafka.rep.shards", defaultValue = "1")
    int partitions;

    @ConfigProperty(name = "scim.kafka.rep.enable", defaultValue = "false")
    boolean enabled;

    @ConfigProperty (name="scim.event.enable", defaultValue = "true")
    boolean eventsEnabled;

    @ConfigProperty (name = "scim.kafka.rep.bootstrap",defaultValue="localhost:9092")
    String bootstrapServers;

    Properties clIdProps = null;
    long lastTime = 0, offset = 0;

    @ConfigProperty (name= "scim.kafka.rep.client.id", defaultValue = KafkaRepEventHandler.ID_AUTO_GEN)
    String clientId;

    @ConfigProperty (name= "scim.kafka.rep.cluster.id",defaultValue="cluster1")
    String clusterId;

    @ConfigProperty (name= "scim.kafka.rep.mode",defaultValue = "replicas")
    String clusterMode;

    public KafkaRepEventReceiver() {

    }

    @PostConstruct
    void init() {
        if (notEnabled()) // exit if events or replication disabled
            return;
        logger.debug("Kafka Replication receiver initializing...");
        this.repErrorOps = eventHandler.sendErrorOps;
        this.received = KafkaRepEventHandler.pendingPubOps;

        clIdProps = eventHandler.getClientIdProps();

        switch (eventHandler.getStrategy()) {
            case KafkaRepEventHandler.MODE_CLUS_REP:
                logger.info("Replication mode: cluster-replica, each node will be identical replicas. Only events from the local cluster accepted.");
                break;
            case KafkaRepEventHandler.MODE_CLUS_PART:
                logger.info("Replication mode: cluster-shard, each node holds a portion of the cluster.");
                break;
            case KafkaRepEventHandler.MODE_GLOB_REP:
                logger.info("Replication mode: global-replica, each node will be identical replicas. All events from all clusters accepted.");
                break;
            case KafkaRepEventHandler.MODE_GLOB_PART:
                logger.info("Replication mode: global-shard,each node holds a portion of the cluster. All events from all clusters accepted.");
                break;
        }

        Properties conProps = new Properties();
        List<String> ctopics = null;

        conProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,bootstrapServers);


        Iterable<String> iter = sysconf.getPropertyNames();

        if(clusterMode.equalsIgnoreCase(MODE_REPLICATE))
            conProps.put(ConsumerConfig.GROUP_ID_CONFIG,clientId); //each client is its own reader
        else
            conProps.put(ConsumerConfig.GROUP_ID_CONFIG,clusterId); //each client is part of a group

        for (String name : iter) {

            if (name.startsWith(KafkaRepEventHandler.KAFKA_CON_PREFIX)) {
                String cprop = name.substring(KafkaRepEventHandler.KAFKA_CON_PREFIX.length());
                if (cprop.equals("topics"))
                    ctopics = Arrays.asList(sysconf.getValue(name,String.class).split(", "));
                else
                    conProps.put(cprop, sysconf.getValue(name,String.class));
            }
        }

        // move the client identification properties state into consumer props (e.g. client.id. group.id)
        for (String key : clIdProps.stringPropertyNames()) {
            conProps.put(key,clIdProps.getProperty(key));
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
                        case KafkaRepEventHandler.MODE_GLOB_PART: // Kafka handles partitioning
                        case KafkaRepEventHandler.MODE_GLOB_REP:
                            // ignore if event is from self
                            if (eventHandler.clientId.equals(hdrClient)) {
                                eventHandler.ignoredOps.add(record);
                                continue; //ignore the event
                            }
                            break;

                        case KafkaRepEventHandler.MODE_CLUS_PART:
                        case KafkaRepEventHandler.MODE_CLUS_REP:
                            // ignore if clusterid does not match or client.id does match
                            if (!eventHandler.clusterId.equals(hdrGroup)
                                    || eventHandler.clientId.equals(hdrClient)) {
                                eventHandler.ignoredOps.add(record);
                                continue; //ignore the event (not in cluster or self)
                            }
                            break;
                        case KafkaRepEventHandler.MODE_TRAN:
                            // do nothing - tranId checked later.

                    }
                    lastTime = record.timestamp();
                    offset = record.offset();

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
        // store the current state on shut down offset and lasteventdate can be used for recovery
        File kafkaFile = new File(KafkaRepEventHandler.KAFKA_DIR,KafkaRepEventHandler.KAFKA_NODE_CFG_PROP);
        try {
            Date lastDate = new Date(lastTime);
            clIdProps.put("lastEventDate",lastDate.toString());
            clIdProps.put("offset",String.valueOf(offset));
            clIdProps.store(new FileWriter(kafkaFile),"i2Scim Replication Kafka client data");
        } catch (IOException e) {
            logger.error("Unable to store kafa state: "+e.getMessage(),e);
        }

    }

}
