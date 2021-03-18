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
import com.independentid.scim.core.FifoCache;
import com.independentid.scim.op.GetOp;
import com.independentid.scim.op.Operation;
import com.independentid.scim.op.SearchOp;
import io.quarkus.runtime.Startup;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.AuthorizationException;
import org.apache.kafka.common.errors.OutOfOrderSequenceException;
import org.apache.kafka.common.errors.ProducerFencedException;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

@ApplicationScoped
@Startup
@Priority(12)
public class KafkaLogEventHandler implements IEventHandler {
    private final static Logger logger = LoggerFactory.getLogger(KafkaLogEventHandler.class);

    static final String KAFKA_PUB_PREFIX = "scim.kafka.log.pub.";

    @ConfigProperty (name = "scim.kafka.log.bootstrap",defaultValue="localhost:9092")
    String bootstrapServers;

    @ConfigProperty (name = "scim.kafka.log.enable", defaultValue = "false")
    boolean enabled;

    @ConfigProperty (name="scim.event.enable", defaultValue = "true")
    boolean eventsEnabled;

    @ConfigProperty (name = "scim.kafka.log.pub.topic", defaultValue="log")
    String logTopic;

    @ConfigProperty (name="scim.kafka.rep.cache.error", defaultValue = "100")
    int errSize;

    final FifoCache<Operation> errorOps = new FifoCache<>(errSize);
    static final List<Operation> pendingOps = Collections.synchronizedList(new ArrayList<>());

    KafkaProducer<String,String> producer = null;

    static boolean isErrorState = false;

    public KafkaLogEventHandler() {

    }

    @Override
    @PostConstruct
    public void init() {
        if (notEnabled())
            return;
        logger.info("Kafka event logger starting on "+bootstrapServers+" using topic:"+logTopic+".");
        Config sysconf = ConfigProvider.getConfig();
        Iterable<String> iter = sysconf.getPropertyNames();
        Properties prodProps = new Properties();

        for (String name : iter) {
            if (name.startsWith(KAFKA_PUB_PREFIX)) {
                String pprop = name.substring(KAFKA_PUB_PREFIX.length());
                if (!pprop.startsWith("topic"))
                    prodProps.put(pprop, sysconf.getValue(name,String.class));
            }
        }
        prodProps.put("bootstrap.servers",bootstrapServers);

        producer = new KafkaProducer<>(prodProps);

        logger.info("Kafka Event Logger configured.");
    }

    @Override
    public void consume(JsonNode node) {
        // do nothing
    }

    public FifoCache<Operation> getSendErrorOps() { return errorOps; }

    public int getSendErrorCnt() { return errorOps.size(); }

    private synchronized void produce(final Operation op) {
       //RequestCtx ctx = op.getRequestCtx();

        String logMessage = op.getLogMessage();

        final ProducerRecord<String, String> producerRecord = new ProducerRecord<>(logTopic, op.getResourceId(), logMessage);

        try {
            producer.send(producerRecord);
        } catch (ProducerFencedException | OutOfOrderSequenceException | AuthorizationException e) {
            producer.close();
            logger.error("Kafka Producer fatal error: " + e.getMessage(), e);
            isErrorState = true;
            errorOps.add(op);
        } catch (KafkaException e) {
            logger.warn("Error sending Op: "+op.toString()+", error: "+e.getMessage());
            producer.abortTransaction();
            errorOps.add(op);
        }

    }

    /**
     * This method takes an operation and produces a stream.
     * @param op The {@link Operation} to be published
     */
    @Override
    public void publish(Operation op) {
        // Ignore search and get requests
        if (!enabled)
            return;  // ignore events when disabled

        if (op instanceof GetOp
            || op instanceof SearchOp)
            return;
        pendingOps.add(op);
        while(pendingOps.size()>0 && isProducing())
            produce(pendingOps.remove(0));

    }

    public boolean notEnabled() {
        return !enabled || !eventsEnabled;
    }

    @Override
    public boolean isProducing() {
        return !isErrorState;
    }

    @Override
    @PreDestroy
    public void shutdown() {
        if (notEnabled())
            return;
        //repEmitter.complete();
        if (!pendingOps.isEmpty()) {
            logger.warn("Attempting to send "+pendingOps.size() +" pending transactions");
            while(pendingOps.size()>0 && isProducing())
                produce(pendingOps.remove(0));
        }

        producer.close();

    }


}
