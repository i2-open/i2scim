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
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.op.*;
import io.quarkus.runtime.Startup;
import org.apache.kafka.clients.consumer.KafkaConsumer;
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
import java.util.*;

@ApplicationScoped
@Startup
@Priority(10)
public class KafkaRepEventHandler implements IEventHandler {
    private final static Logger logger = LoggerFactory.getLogger(KafkaRepEventHandler.class);

    public static final String KAFKA_PUB_PREFIX = "scim.kafkaRepEventHandler.pub.";
    public static final String KAFKA_CON_PREFIX = "scim.kafkaRepEventHandler.sub.";

    @ConfigProperty (name = "scim.kafkaRepEventHandler.enable", defaultValue = "false")
    boolean enabled;

    static List<Operation> repErrorOps = Collections.synchronizedList(new ArrayList<>());
    static List<Operation> pendingOps = Collections.synchronizedList(new ArrayList<>());

    @ConfigProperty (name = "kafka.bootstrap.servers",defaultValue="localhost:9092")
    String bootstrapServers;

    @ConfigProperty (name = "scim.replication.reset", defaultValue="NONE")
    String resetDate; //TODO implement reset date

    @ConfigProperty (name = KAFKA_PUB_PREFIX+"topic", defaultValue = "rep")
    String repTopic;

    @ConfigProperty (name= "scim.kafkaRepEventHandler.client.id", defaultValue="defaultClient")
    String clientId;

    KafkaProducer<String,IBulkOp> producer = null;
    KafkaConsumer<String,JsonNode> consumer = null;
    HashSet<UUID> processed = new HashSet<>();
    KafkaEventReceiver processor = null;

    Properties prodProps = new Properties();
    Properties conProps = new Properties();

    static boolean isErrorState = false;

    public KafkaRepEventHandler() {

    }

    @Override
    @PostConstruct
    public void init() {
        if (!enabled)
            return;

        List<String> ctopics = null;

        Config sysconf = ConfigProvider.getConfig();
        Iterable<String> iter = sysconf.getPropertyNames();
        Properties prodProps = new Properties();
        Properties conProps = new Properties();

        //Normally sender and receiver should have same client id.
        //If a sub or pub property is set for cliend.id, it will override
        prodProps.put("client.id",clientId);
        conProps.put("client.id",clientId);
        prodProps.put("bootstrap.servers",bootstrapServers);
        conProps.put("bootstrap.servers",bootstrapServers);
        for (String name : iter) {
            if (name.startsWith(KAFKA_PUB_PREFIX)) {
                String pprop = name.substring(KAFKA_PUB_PREFIX.length());
                if (!pprop.equals("topic"))
                    prodProps.put(pprop, sysconf.getValue(name,String.class));
            }
            if (name.startsWith(KAFKA_CON_PREFIX)) {
                String cprop = name.substring(KAFKA_CON_PREFIX.length());
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

            logger.debug("Kafka replication producer properties...");
            for (String key : prodProps.stringPropertyNames())
                logger.debug("\t"+key+":\t"+prodProps.getProperty(key));
        }

        if (ctopics == null) {
            logger.warn("Configuration property "+KAFKA_CON_PREFIX+"topics undefined. Defaulting to 'rep'");
            ctopics = Collections.singletonList("rep");
        }
        consumer = new KafkaConsumer<String, JsonNode>(conProps);
        consumer.subscribe(ctopics);

        producer = new KafkaProducer<String, IBulkOp>(prodProps);
        producer.initTransactions();

        // initialize the receiver thread
        if (!isErrorState) {
            processor = new KafkaEventReceiver(consumer, this);
            Thread t = new Thread(processor);
            t.start();
        }

        logger.info("Kafka Replicator configured.");
    }

    public Properties getConsumerProps() {
        return this.conProps;
    }

    public Properties getProducerProps() {
        return this.prodProps;
    }


    //@Incoming("rep-in")
    //@Acknowledgment(Acknowledgment.Strategy.PRE_PROCESSING)
    @Override
    public void consume(JsonNode node) {

        if (node == null) {
            logger.warn("Ignoring invalid replication message.");
            return;
        }
        Operation op;
        try {
            op = BulkOps.parseOperation(node, null, 0);
        } catch (ScimException e) {
            e.printStackTrace();
            return;
        }
        if (op != null)
            System.err.println("\n\nRecieved JSON replica event\n" + op.toString() + "\n\n");
        pendingOps.add(op);
    }

    @Override
    public List<Operation> getReceivedOps() {
        return pendingOps;
    }


    @Override
    public boolean hasNoReceivedEvents() {
        return pendingOps.isEmpty();
    }

    @Override
    public List<Operation> getSendErrorOps() { return repErrorOps; }

    private synchronized void produce(final Operation op) {

        final ProducerRecord<String, IBulkOp> producerRecord = new ProducerRecord<>(repTopic, op.getResourceId(), ((IBulkOp) op));

        try {
            producer.beginTransaction();
            producer.send(producerRecord);
            producer.commitTransaction();
        } catch (ProducerFencedException | OutOfOrderSequenceException | AuthorizationException e) {
            producer.close();
            logger.error("Kafka Producer fatal error: " + e.getMessage(), e);
            isErrorState = true;
            repErrorOps.add(op);
        } catch (KafkaException e) {
            logger.warn("Error sending Op: "+op.toString()+", error: "+e.getMessage());
            producer.abortTransaction();
            repErrorOps.add(op);
        }

    }

    /**
     * This method takes an operation and produces a stream.
     * @param op The {@link Operation} to be published
     */
    @Override
    public void process(Operation op) {
        // Ignore search and get requests
        if (op instanceof GetOp
            || op instanceof SearchOp)
            return;
        pendingOps.add(op);
        processBuffer();

    }

    private synchronized void processBuffer() {
        while(pendingOps.size()>0 && isProducing())
            produce(pendingOps.remove(0));
    }

    @Override
    public boolean isProducing() {
        return !isErrorState;
    }

    @Override
    @PreDestroy
    public void shutdown() {
        //repEmitter.complete();
        consumer.commitSync();
        consumer.close();

        if (!pendingOps.isEmpty()) {
            logger.warn("Attempting to send "+ pendingOps.size() +" pending transactions");
            processBuffer();
        }
        producer.close();
        processor.shutdown();

    }


}
