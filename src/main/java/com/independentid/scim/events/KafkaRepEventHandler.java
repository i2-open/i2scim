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
import com.independentid.scim.backend.BackendException;
import com.independentid.scim.backend.BackendHandler;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.FifoCache;
import com.independentid.scim.core.PoolManager;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.op.*;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.resource.ScimResource;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

//@ApplicationScoped
//@Startup
@Singleton
@Priority(10)
public class KafkaRepEventHandler implements IEventHandler {
    private final static Logger logger = LoggerFactory.getLogger(KafkaRepEventHandler.class);

    public static final String KAFKA_PUB_PREFIX = "scim.kafka.rep.pub.";
    public static final String KAFKA_CON_PREFIX = "scim.kafka.rep.sub.";

    public static final String MODE_GLOB_PART   = "global-shard";
    public static final String MODE_GLOB_REP    = "global-replica";
    public static final String MODE_CLUS_PART   = "cluster-shard";
    public static final String MODE_CLUS_REP    = "cluster-replica";
    
    public static final String MODE_TRAN        = "transid";

    public static final String HDR_CLIENT = "cli";
    public static final String HDR_CLUSTER = "clus";
    public static final String HDR_TID = BulkOps.PARAM_TRANID;
    public static final String KAFKA_DIR = "kafka";
    public static final String KAFKA_NODE_CFG_PROP = "-kafkaCfg.prop";
    public static final String PROP_SCIM_KAFKA_REP_SHARDS = "scim.kafka.rep.shards";
    public static final String ID_AUTO_GEN = "<AUTO>";
    public static final String PROP_CLIENT_ID = "client.id";
    public static final String PROP_GROUP_ID = "group.id";

    @ConfigProperty(name= "scim.root.dir")
    String rootDir;

    @ConfigProperty (name = "scim.kafka.rep.enable", defaultValue = "false")
    boolean enabled;

    @ConfigProperty (name="scim.event.enable", defaultValue = "true")
    boolean eventsEnabled;

    @ConfigProperty (name="scim.kafka.rep.shards", defaultValue = "1")
    int partitions;

    @ConfigProperty (name="scim.kafka.rep.partitionerclass", defaultValue = "")
    String partClass;

    @ConfigProperty (name="scim.kafka.rep.cache.error", defaultValue = "100")
    int errSize;

    @ConfigProperty (name="scim.kafka.rep.cache.processed", defaultValue = "100")
    int procSize;

    @ConfigProperty (name="scim.kafka.rep.cache.filtered", defaultValue = "100")
    int filterSize;

    // These arrays are used primarily for timing, recovery, and testing purposes.
    FifoCache<Operation> sendErrorOps, tranConflictOps;
    FifoCache<Operation> acceptedOps;
    FifoCache<ConsumerRecord<String,JsonNode>> ignoredOps;

    static final List<Operation> pendingPubOps = Collections.synchronizedList(new ArrayList<>());

    @ConfigProperty (name = "scim.kafka.rep.bootstrap",defaultValue="localhost:9092")
    String bootstrapServers;

    @ConfigProperty (name = "scim.kafka.rep.pub.topic", defaultValue = "rep")
    String repTopic;

    @ConfigProperty (name= "scim.kafka.rep.client.id", defaultValue = ID_AUTO_GEN)
    String clientId;

    @ConfigProperty (name= "scim.kafka.rep.cluster.id",defaultValue= ID_AUTO_GEN)
    String clusterId;

    Properties clientIdProp = new Properties();

    @ConfigProperty (name= "scim.kafka.rep.mode",defaultValue = MODE_GLOB_REP)
    String repMode;

    KafkaProducer<String,IBulkOp> producer = null;


    final Properties prodProps = new Properties();

    @Inject
    Config sysconf;

    @Inject
    ConfigMgr cmgr;

    @Inject
    BackendHandler handler;

    @Inject
    PoolManager pool;

    static boolean isErrorState = false;

    boolean ready = false;

    public KafkaRepEventHandler() {

    }

    @Override
    @PostConstruct
    public void init() {
        sendErrorOps = new FifoCache<>(errSize);
        tranConflictOps = new FifoCache<>(errSize);
        acceptedOps = new FifoCache<>(procSize);
        ignoredOps = new FifoCache<>(filterSize);
        if (notEnabled())
            return;
        logger.info("Kafka replication handler starting on "+bootstrapServers+" as client.id"+clientId+", using topic:"+repTopic+".");

        if (partitions > 1) {
            //Configure to use partitioned cluster. This assumes scim.kafka.rep.shards matches kubernetes Statefulset replica size
            //Note partitioner can be overridden by placing in scim.kafka.rep.pub.partitioner.class.
            prodProps.put(ProducerConfig.PARTITIONER_CLASS_CONFIG,ScimKafkaPartitioner.class.getName());
            prodProps.put(PROP_SCIM_KAFKA_REP_SHARDS,partitions);
        }
        Iterable<String> iter = sysconf.getPropertyNames();

        initId();  // Load previous id or generate new ones

        //Normally sender and receiver should have same client id.
        //If a sub or pub property is set for cliend.id, it will override
        prodProps.put(ProducerConfig.CLIENT_ID_CONFIG,clientId);

        prodProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,bootstrapServers);

        for (String name : iter) {
            if (name.startsWith(KAFKA_PUB_PREFIX)) {
                String pprop = name.substring(KAFKA_PUB_PREFIX.length());
                if (!pprop.equals("topic"))
                    prodProps.put(pprop, sysconf.getValue(name,String.class));
            }
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Kafka replication producer properties...");
            for (String key : prodProps.stringPropertyNames())
                logger.debug("\t"+key+":\t"+prodProps.getProperty(key));
        }

        producer = new KafkaProducer<>(prodProps);
        producer.initTransactions();

        // initialize the receiver thread
        //Thread t = new Thread(receiver);
        //t.start();  // Do we need to start an injected thread?

        logger.info("Kafka replication started.");

        // ensure that config,schema managers are initialized.
        Operation.initialize(cmgr);

        ready = true;
    }

    public boolean notEnabled() {
        return !enabled || !eventsEnabled;
    }


    public Properties getProducerProps() {
        return this.prodProps;
    }

    /**
     * Enables testing. Should not be used in production as it may cause loss of replication events as filtering changes.
     * @param strat The replication strategy to enforce (determines how inbound rep events are filtered)
     */
    public void setStrategy(String strat) {
        logger.warn("Server replication processing strategy changed to: "+strat);
        repMode = strat;
    }

    private void initId() {
        /*
         Because a kubernetes cluster may turn over and the server host name and other factors may change, the kafka
         client.id will be stored on disk (the PV) based on a generated identifier (UUID). Thus as a new pod takes
         over an old PV, the new pod *becomes* the node and resumes processing.
         */
        if (ready) return;
        File rootFile = new File(rootDir);
        if (!rootFile.exists())
            logger.error("Root directory for SCIM does not exist(scim.root.dir="+rootFile+").");
        File kafkaDir = new File(rootFile, KAFKA_DIR);
        if (!kafkaDir.exists())
            kafkaDir.mkdir();
        File kafkaState = new File(kafkaDir, KAFKA_NODE_CFG_PROP);

        if (kafkaState.exists()) {
            try {
                FileInputStream fis = new FileInputStream(kafkaState);
                clientIdProp.load(fis);
                fis.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {

            if (clientId.equals(ID_AUTO_GEN))
                clientId = UUID.randomUUID().toString();
            clientIdProp.put("client.id", clientId);
            switch (repMode) {
                case MODE_CLUS_REP:
                case MODE_GLOB_REP:
                case MODE_TRAN:
                    // Each node gets an equal full copy, therefore each node is its own group
                    clientIdProp.put(PROP_GROUP_ID,clientId);
                    break;
                default:
                    clientIdProp.put(PROP_GROUP_ID,clusterId);
            }
            try {
                FileOutputStream fos = new FileOutputStream(kafkaState);
                clientIdProp.store(fos,"i2Scim Replication Kafka client data");
                fos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        clientId = clientIdProp.getProperty(PROP_CLIENT_ID);
        clusterId = clientIdProp.getProperty(PROP_GROUP_ID);

        logger.info("Replication client properties:\n"+clientIdProp.toString());

        ready = true;
    }

    public Properties getClientIdProps() {
        initId();
        return clientIdProp;

    }

    public String getStrategy() { return repMode; }

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
            op = BulkOps.parseOperation(node, null, 0, true);
            RequestCtx ctx = op.getRequestCtx();
            if (ctx != null) {
                String tranId = ctx.getTranId();
                try {
                    ScimResource tres = handler.getTransactionRecord(tranId);
                    if (tres != null) {
                        tranConflictOps.add(op);
                        logger.debug("Received duplicate transaction ["+tranId+"] *IGNORED*.");
                        return;
                    }
                } catch (BackendException e) {
                    logger.warn("Unexpected error validating transaction id ["+tranId+"]: "+e.getMessage(),e);
                    return;
                }

            }            op.getTransactionResource();
        } catch (ScimException e) {
            logger.warn("Unexpected error parsing transaction: "+e.getMessage(),e);
            return;
        }

        if (logger.isDebugEnabled())
            logger.debug("\tRecieved JSON replica event\n" + op.toString());
        acceptedOps.add(op);
        pool.addJob(op);
        // logevent will be triggereed within the operation itself depending on completion


    }

    public FifoCache<ConsumerRecord<String,JsonNode>> getIgnoredOps () {return ignoredOps;}

    public FifoCache<Operation> getSendErrorOps() { return sendErrorOps; }

    public int getSendErrorCnt() { return sendErrorOps.size(); }

    private synchronized void produce(final Operation op) {
        RequestCtx ctx = op.getRequestCtx();
        if (ctx != null && ctx.isReplicaOp()) {
            if (logger.isDebugEnabled())
                logger.debug("Ignoring internal event: "+op.toString());
            return; // This is a replication op and should not be re-replicated!
        }
        if (logger.isDebugEnabled())
            logger.debug("Processing event: "+op.toString());

        final ProducerRecord<String, IBulkOp> producerRecord = new ProducerRecord<>(repTopic, op.getResourceId(), ((IBulkOp) op));
        // The following headers are used by the receiver to filter out duplicates and in cluster events depending on
        // replication strategy
        if (clientId != null && !clientId.isBlank())
            producerRecord.headers().add(HDR_CLIENT,clientId.getBytes(StandardCharsets.UTF_8));
        if (clusterId != null && !clusterId.isBlank())
            producerRecord.headers().add(HDR_CLUSTER, clusterId.getBytes(StandardCharsets.UTF_8));

        if (ctx != null) {
            String tranId = ctx.getTranId();
            if (tranId != null)
                producerRecord.headers().add(HDR_TID,tranId.getBytes(StandardCharsets.UTF_8));
        }
        try {
            producer.beginTransaction();
            producer.send(producerRecord);
            producer.commitTransaction();
        } catch (ProducerFencedException | OutOfOrderSequenceException | AuthorizationException e) {
            producer.close();
            logger.error("Kafka Producer fatal error: " + e.getMessage(), e);
            isErrorState = true;
            sendErrorOps.add(op);
        } catch (KafkaException e) {
            logger.warn("Error sending Op: "+op.toString()+", error: "+e.getMessage());
            producer.abortTransaction();
            sendErrorOps.add(op);
        }

    }

    /**
     * This method takes an operation and produces a stream.
     * @param op The {@link Operation} to be published
     */
    @Override
    public void publish(Operation op) {
        // Ignore search and get requests
        if (op instanceof GetOp
            || op instanceof SearchOp)
            return;
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

        processBuffer(); //Ensure all trans sent!

        producer.close();

    }

    /*
     Following methods generally used to error detection and testing...
     */

    public int getTranConflictCnt() { return tranConflictOps.size(); }

    public boolean hasNoTranConflictOps() { return tranConflictOps.isEmpty(); }

    public int getAcceptedOpsCnt() { return acceptedOps.size(); }

    public boolean hasNoReceivedOps() { return acceptedOps.isEmpty(); }

    public boolean hasNoReceivedEvents() { return acceptedOps.isEmpty(); }

    public boolean hasNoIgnoredOps() { return ignoredOps.isEmpty(); }

    public int getIgnoredOpsCnt() { return ignoredOps.size(); }


    public void resetCounts() {
        acceptedOps.clear();
        ignoredOps.clear();
        sendErrorOps.clear();
        tranConflictOps.clear();
    }
}
