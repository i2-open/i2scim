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
import io.quarkus.arc.config.ConfigProperties;
import io.quarkus.runtime.Startup;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.AuthorizationException;
import org.apache.kafka.common.errors.OutOfOrderSequenceException;
import org.apache.kafka.common.errors.ProducerFencedException;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.*;

@ApplicationScoped
@Startup
@Priority(10)
public class KafkaRepEventHandler implements IEventHandler {
    private final static Logger logger = LoggerFactory.getLogger(KafkaRepEventHandler.class);

    public static final String KAFKA_PUB_PREFIX = "scim.kafka.rep.pub.";
    public static final String KAFKA_CON_PREFIX = "scim.kafka.rep.sub.";

    @ConfigProperty (name = "scim.kafka.rep.enable", defaultValue = "false")
    boolean enabled;

    static final List<Operation> repErrorOps = Collections.synchronizedList(new ArrayList<>());
    static final List<Operation> pendingOps = Collections.synchronizedList(new ArrayList<>());

    @ConfigProperty (name = "scim.kafka.rep.bootstrap",defaultValue="localhost:9092")
    String bootstrapServers;

    @ConfigProperty (name = "scim.kafa.rep.sub.resetdate", defaultValue="NONE")
    String resetDate; //TODO implement reset date

    @ConfigProperty (name = "scim.kafka.rep.pub.topic", defaultValue = "rep")
    String repTopic;

    @ConfigProperty (name= "scim.kafka.rep.client.id")
    String clientId;

    KafkaProducer<String,IBulkOp> producer = null;
    KafkaConsumer<String,JsonNode> consumer = null;
    HashSet<UUID> processed = new HashSet<>();
    KafkaEventReceiver inboundEventProcessor = null;

    final Properties prodProps = new Properties();
    final Properties conProps = new Properties();

    @Inject
    Config sysconf;

    static boolean isErrorState = false;

    public KafkaRepEventHandler() {

    }

    @Override
    @PostConstruct
    public void init() {
        if (!enabled)
            return;
        logger.info("Kafka replication handler starting on "+bootstrapServers+" as client.id"+clientId+", using topic:"+repTopic+".");
        List<String> ctopics = null;

        Iterable<String> iter = sysconf.getPropertyNames();
        Properties prodProps = new Properties();
        Properties conProps = new Properties();

        //Normally sender and receiver should have same client id.
        //If a sub or pub property is set for cliend.id, it will override
        prodProps.put(ProducerConfig.CLIENT_ID_CONFIG,clientId);
        conProps.put(ConsumerConfig.CLIENT_ID_CONFIG,clientId);
        prodProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,bootstrapServers);
        conProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,bootstrapServers);
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
        this.inboundEventProcessor = new KafkaEventReceiver(consumer, this);
        Thread t = new Thread(this.inboundEventProcessor);
        t.start();

        logger.info("Kafka replication started.");
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
        if (!enabled)
            return;

        this.inboundEventProcessor.shutdown();

        processBuffer(); //Ensure all trans sent!

        producer.close();

    }


}
