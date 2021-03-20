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

package com.independentid.scim.test.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.backend.BackendException;
import com.independentid.scim.backend.BackendHandler;
import com.independentid.scim.backend.mongo.MongoIdGenerator;
import com.independentid.scim.core.FifoCache;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.events.KafkaRepEventHandler;
import com.independentid.scim.events.OperationSerializer;
import com.independentid.scim.op.*;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.protocol.ResourceResponse;
import com.independentid.scim.protocol.ScimResponse;
import com.independentid.scim.resource.Meta;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.resource.StringValue;
import com.independentid.scim.resource.Value;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.test.misc.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.AuthorizationException;
import org.apache.kafka.common.errors.OutOfOrderSequenceException;
import org.apache.kafka.common.errors.ProducerFencedException;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Properties;

import static java.lang.Thread.sleep;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * This test suite generates replication events and checks to see if the replication receivers receives and correctly processes the events.
 */
@SuppressWarnings("BusyWait")
@QuarkusTest
@TestProfile(ScimEventsTestProfile.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class ScimReplicationRcvTest {
    private final static Logger logger = LoggerFactory.getLogger(ScimReplicationRcvTest.class);

    @Inject
    SchemaManager schemaManager;

    @Inject
    KafkaRepEventHandler eventHandler;

    @Inject
    MongoIdGenerator generator;

    @Inject
    BackendHandler handler;

    @Inject
    TestUtils testUtils;

    @ConfigProperty(name = KafkaRepEventHandler.KAFKA_PUB_PREFIX+"topic", defaultValue = "rep")
    String repTopic;

    @ConfigProperty(name="scim.kafka.rep.bootstrap")
    String kafkaBoot;

    @ConfigProperty (name= "scim.kafka.rep.client.id")
    String svr_clientId;

    @ConfigProperty (name= "scim.kafka.rep.cluster.id",defaultValue="cluster1")
    String svr_groupId;

    static final String TEST_CLIENT = "TestClient1";
    static final String TEST_GROUP = "TestGroup1";

    private static final String testUserFile1 = "classpath:/schema/TestUser-bjensen.json";
    private static final String testUserFile2 = "classpath:/schema/TestUser-jsmith.json";
    
    private static String id1=null,id2=null;
    private static ScimResource res1=null;

    static KafkaProducer<String, IBulkOp> producer = null;

    static CreateOp op1=null, op2 = null;

    /**
     * Prepares a set of tests so tester can pretend to be a producer not affiliated with server cluster
     */
    @Test
    public void a_initTests() {
        logger.info("========== SCIM Repliction Reception Receiver Test ==========");
        logger.info("A. Initializing tests");
        try {
            testUtils.resetProvider();
        } catch (ScimException | BackendException | IOException e) {
            fail("Unable to reset provider: "+e.getMessage(),e);
        }

        Properties props = new Properties();
        props.setProperty("bootstrap.servers", kafkaBoot);
        props.setProperty("transactional.id", TEST_CLIENT);
        props.setProperty("client.id",TEST_CLIENT);
        props.setProperty("enable.auto.commit", "true");
        props.setProperty("acks","all"); // Wait for all nodes to acknowledge to ensure test values sent

        //props.setProperty("broadcast","true");
        props.setProperty("key.serializer", StringSerializer.class.getName());
        props.setProperty("value.serializer", OperationSerializer.class.getName());
        producer = new KafkaProducer<>(props);
        producer.initTransactions();

    }

    @Test
    public void b_sendAsGlobalWithGlobalStratTest() {
        logger.info("B. Sending global event with global rep mode (STRAT_GLOBAL)");
        eventHandler.setStrategy(KafkaRepEventHandler.STRAT_GLOBAL);
        eventHandler.resetCounts();

        id1 = generator.getNewIdentifier();
        id2 = generator.getNewIdentifier();
        res1 = testUtils.loadResource(testUserFile1,"Users");
        ScimResource res2 = testUtils.loadResource(testUserFile2, "Users");
        op1 = prepareCreateUser(res1,id1);
        op2 = prepareCreateUser(res2,id2);

        logger.debug("\tSending 2 events as external to cluster - should be accepted.");
        produce(op1,id1, TEST_GROUP,TEST_CLIENT);
        produce(op2,id2, TEST_GROUP,TEST_CLIENT);

        waitForEventReceived(2, eventHandler);

        assertThat(eventHandler.getAcceptedOpsCnt())
                .as("Check 2 valid events received")
                .isEqualTo(2);

        logger.debug("\tSending repeat event as external node - should be REJECTED due to tranId conflict");
        eventHandler.resetCounts();
        // This should be rejected due to tran conflict
        produce(op1,id1, TEST_GROUP,TEST_CLIENT);

        waitForEventReceived(1, eventHandler);

        assertThat(eventHandler.getTranConflictCnt())
                .as("Confirm was rejected.")
                .isEqualTo(1);

    }

    @Test
    public void c_sendAsClusterWithStatGlobalTest() {
        logger.info("C. Testing create user in same cluster with Global replication (STRAT_GLOBAL)");
        eventHandler.setStrategy(KafkaRepEventHandler.STRAT_GLOBAL);
        eventHandler.resetCounts();

        try {
            testUtils.resetProvider();
        } catch (ScimException | BackendException | IOException e) {
            fail("Unable to reset provider: "+e.getMessage(),e);
        }

        logger.debug("\tSending 2 events as cluster node - should be ignored.");
        produce(op1,id1, svr_groupId,TEST_CLIENT);
        produce(op2,id2, svr_groupId,TEST_CLIENT);

        FifoCache<ConsumerRecord<String, JsonNode>> ignoredEvents = eventHandler.getIgnoredOps();
        waitForEventReceived(2, eventHandler);
        assertThat(eventHandler.getAcceptedOpsCnt())
                .isEqualTo(0);
        assertThat(ignoredEvents.size())
                .isEqualTo(2);
    }

    @Test
    public void d_stratClusterTest() {
        logger.info("D. Testing create user in with Cluster replication (STRAT_CLUS)");

        logger.debug("\tResetting provider and counts");
        eventHandler.resetCounts();
        eventHandler.setStrategy(KafkaRepEventHandler.STRAT_CLUS);

        try {
            testUtils.resetProvider();
        } catch (ScimException | BackendException | IOException e) {
            fail("Unable to reset provider: "+e.getMessage(),e);
        }

        // send external events (global) - should be ignored
        logger.debug("\tSending 2 events as external node - should be ignored.");
        produce(op1,id1, TEST_GROUP,TEST_CLIENT);
        produce(op2,id2, TEST_GROUP,TEST_CLIENT);

        FifoCache<ConsumerRecord<String, JsonNode>> ignoredEvents = eventHandler.getIgnoredOps();
        waitForEventReceived(2, eventHandler);

        assertThat(eventHandler.getAcceptedOpsCnt())
                .as("Non cluster local events should be ignored")
                .isEqualTo(0);
        assertThat(ignoredEvents.size())
                .as("2 events ignored")
                .isEqualTo(2);


        // send in-cluster events - should be accepted
        logger.debug("\tSending 2 events as cluster node - should be accepted.");
        eventHandler.resetCounts();
        produce(op1,id1, svr_groupId,TEST_CLIENT);
        produce(op2,id2, svr_groupId,TEST_CLIENT);

        waitForEventReceived(2, eventHandler);

        assertThat(eventHandler.getAcceptedOpsCnt())
                .as("2 In cluster events should be accepted")
                .isEqualTo(2);

        assertThat(ignoredEvents.size())
                .as("0 events ignored") //includes 2 from previous test
                .isEqualTo(0);
    }

    @Test
    public void e_stratAllProducerTest() {
        logger.info("E. Testing inter-node replication (STRAT_ALL)");

        logger.debug("\tResetting provider and counts");
        eventHandler.resetCounts();
        eventHandler.setStrategy(KafkaRepEventHandler.STRAT_ALL);

        try {
            testUtils.resetProvider();
        } catch (ScimException | BackendException | IOException e) {
            fail("Unable to reset provider: "+e.getMessage(),e);
        }

        logger.debug("\tSending 2 events as global node - should be accepted.");
        produce(op1,id1, TEST_GROUP,TEST_CLIENT);
        produce(op2,id2, TEST_GROUP,TEST_CLIENT);

        waitForEventReceived(2, eventHandler);

        assertThat(eventHandler.getAcceptedOpsCnt())
                .as("Non cluster local events should be ignored")
                .isEqualTo(2);
        assertThat(eventHandler.getIgnoredOps().size())
                .as("2 events ignored")
                .isEqualTo(0);

        // Send as self
        try {
            testUtils.resetProvider();
        } catch (ScimException | BackendException | IOException e) {
            fail("Unable to reset provider: "+e.getMessage(),e);
        }
        eventHandler.resetCounts();

        logger.debug("\tSending 2 events as self - should be ignored.");
        produce(op1,id1, svr_groupId,svr_clientId);
        produce(op2,id2, svr_groupId,svr_clientId);

        waitForEventReceived(2, eventHandler);

        assertThat(eventHandler.getAcceptedOpsCnt())
                .as("In cluster events should be accepted")
                .isEqualTo(0);

        assertThat(eventHandler.getIgnoredOps().size())
                .as("2 events ignored")
                .isEqualTo(2);


        try {
            testUtils.resetProvider();
        } catch (ScimException | BackendException | IOException e) {
            fail("Unable to reset provider: "+e.getMessage(),e);
        }
        eventHandler.resetCounts();

        logger.debug("\tSending 2 events as cluster node - should be accepted.");
        produce(op1,id1, svr_groupId,TEST_CLIENT);
        produce(op2,id2, svr_groupId,TEST_CLIENT);

        waitForEventReceived(2, eventHandler);

        assertThat(eventHandler.getAcceptedOpsCnt())
                .as("In cluster events should be accepted")
                .isEqualTo(2);

        assertThat(eventHandler.getIgnoredOps().size())
                .as("2 events ignored")
                .isEqualTo(0);

    }

    @Test
    public void f_CheckDataTest() {
        logger.info("F. Confirming provider actually created replicated resource");
        // Read the backend to ensure the replicated resource was actually created.
        RequestCtx ctx;
        try {
            ctx = new RequestCtx("/Users",id1,null,schemaManager);
            ScimResponse resp = handler.get(ctx);
            assertThat(resp)
                    .isNotNull();
            assertThat(resp).isInstanceOf(ResourceResponse.class);
            ResourceResponse rresp = (ResourceResponse) resp;
            ScimResource res = rresp.getResultResource();
            assertThat(res).isNotNull();
            assertThat(res.getId())
                    .isEqualTo(res1.getId());
            Attribute pwdAttr = schemaManager.findAttribute("password",null);
            Value val = res.getValue(pwdAttr);
            assertThat(val)
                    .as("entire resource was replicated and password is not null")
                    .isNotNull();
            assertThat(val)
                    .as("entire resource was replicated and password is StringValue")
                    .isInstanceOf(StringValue.class);
            assertThat(((StringValue)val).getRawValue())
                    .as("entire resource was replicated and password is present")
                    .isEqualTo("t1meMa$heen");

        } catch (ScimException | BackendException e) {
            fail("Unable to read resource: "+e.getMessage(),e);
        }

    }

    @Test
    public void g_putRepTest() {
        logger.info("G. Testing PUT replication (STRAT_ALL)");

        logger.debug("\tResetting counts");
        eventHandler.resetCounts();
        eventHandler.setStrategy(KafkaRepEventHandler.STRAT_ALL);

        try {
            Attribute unameattr = schemaManager.findAttribute("userName",null);
            StringValue newUserName = new StringValue(unameattr,"username1");
            res1.addValue(newUserName);

            assertThat(res1.getId())
                    .as("Check resource has id set by prepareCreate")
                    .isEqualTo(id1);

            PutOp op = preparePutUser(res1);
            assert op != null;
            produce(op,res1.getId(), TEST_GROUP,TEST_CLIENT);

            waitForEventReceived(1, eventHandler);

            assertThat(eventHandler.getAcceptedOpsCnt())
                    .as("1 (put) event should be accepted")
                    .isEqualTo(1);

            assertThat(eventHandler.getIgnoredOps().size())
                    .as("0 events ignored")
                    .isEqualTo(0);

            //Wait for mongo to settle
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
               //ignore
            }
            RequestCtx ctx = new RequestCtx("/Users",res1.getId(),null,schemaManager);
            ScimResponse resp = handler.get(ctx);
            assertThat(resp)
                    .isNotNull();
            assertThat(resp).isInstanceOf(ResourceResponse.class);
            ResourceResponse rresp = (ResourceResponse) resp;
            ScimResource res = rresp.getResultResource();
            assertThat(res).isNotNull();
            assertThat(res.getId())
                    .isEqualTo(res1.getId());
            Attribute pwdAttr = schemaManager.findAttribute("password",null);
            Value val = res.getValue(pwdAttr);
            assertThat(val)
                    .as("entire resource was updated and password is not null")
                    .isNotNull();
            assertThat(val)
                    .as("entire resource was updated and password is StringValue")
                    .isInstanceOf(StringValue.class);
            assertThat(((StringValue)val).getRawValue())
                    .as("pasword value was updated and password is present")
                    .isEqualTo("t1meMa$heen");
            val = res.getValue(unameattr);
            assertThat(val)
                    .as("entire resource was updated and username is not null")
                    .isNotNull();
            assertThat(val)
                    .as("entire resource was updated and username is StringValue")
                    .isInstanceOf(StringValue.class);
            assertThat(((StringValue)val).getRawValue())
                    .as("username was updated and present")
                    .isEqualTo("username1");

        } catch (ScimException | BackendException e) {
            fail("Unable to modify/read resource: "+e.getMessage(),e);
        }

    }

    @Test
    public void h_deleteRepTest() {
        logger.info("H. Testing DELETE replication (STRAT_ALL)");

        logger.debug("\tResetting counts");
        eventHandler.resetCounts();
        eventHandler.setStrategy(KafkaRepEventHandler.STRAT_ALL);

        try {

            DeleteOp op = prepareDeleteUser(id1);
            assert op != null;
            produce(op,res1.getId(), TEST_GROUP,TEST_CLIENT);

            waitForEventReceived(1, eventHandler);

            assertThat(eventHandler.getAcceptedOpsCnt())
                    .as("1 (put) event should be accepted")
                    .isEqualTo(1);

            assertThat(eventHandler.getIgnoredOps().size())
                    .as("0 events ignored")
                    .isEqualTo(0);
            //Wait for mongo to settle
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                //ignore
            }
            RequestCtx ctx = new RequestCtx("/Users",id1,null,schemaManager);
            ScimResponse resp = handler.get(ctx);
            assertThat(resp)
                    .isNotNull();
            // confirm status 204 per RFC7644 Sec 3.6
            assertThat(resp.getStatus())
                    .as("Confirm succesfull deletion of user")
                    .isEqualTo(ScimResponse.ST_NOTFOUND);

        } catch (ScimException | BackendException e) {
            fail("Unable to modify/read resource: "+e.getMessage(),e);
        }

    }

    static void waitForEventReceived(int num, KafkaRepEventHandler eventHandler) {
        logger.debug("\t*** waiting for Kafka event delivery ["+num+"]...");

        int wcnt = 0;
        while (eventHandler.getAcceptedOpsCnt() < num
                && eventHandler.getIgnoredOps().size() < num
                && eventHandler.getTranConflictCnt() < num
                && wcnt < 15 ) {
            if (wcnt > 0)
                logger.debug("\t\t..."+wcnt);
            try {
                wcnt++;
                sleep(1000);
            } catch (InterruptedException ignored) {
                //ignore
            }
        }
    }



    private synchronized void produce(final Operation op,String key, String grpid, String clid) {
        RequestCtx ctx = op.getRequestCtx();
        final ProducerRecord<String, IBulkOp> producerRecord = new ProducerRecord<>(repTopic, key, ((IBulkOp) op));
        producerRecord.headers().add(KafkaRepEventHandler.HDR_CLIENT,clid.getBytes(StandardCharsets.UTF_8));
        producerRecord.headers().add(KafkaRepEventHandler.HDR_CLUSTER,grpid.getBytes(StandardCharsets.UTF_8));
        if (ctx != null) {
            String tranId = ctx.getTranId();
            if (tranId != null)
                producerRecord.headers().add(KafkaRepEventHandler.HDR_TID,tranId.getBytes(StandardCharsets.UTF_8));
        }
        try {
            producer.beginTransaction();
            producer.send(producerRecord);
            producer.commitTransaction();
        } catch (ProducerFencedException | OutOfOrderSequenceException | AuthorizationException e) {
            producer.close();
            fail("Kafka Producer fatal error: " + e.getMessage(), e);
        } catch (KafkaException e) {
            producer.abortTransaction();
            producer.close();
            fail("Error sending Op: "+op.toString()+", error: "+e.getMessage());
        }

    }

    private PutOp preparePutUser(ScimResource res) {
        try {
            Meta meta = res.getMeta();
            if (meta == null) {
                meta = new Meta();
                res.setMeta(meta);
            }
            // Not needed for TransactionRecord type
            Date modified = Date.from(Instant.now());
            meta.setLastModifiedDate(modified); // always set the modify date upon create.

            meta.setLocation("/Users/" + res.getId());
            String etag = res.calcVersionHash();
            meta.setVersion(etag);

            // Generate a requestctx to simulate a transaction id
            RequestCtx ctx = new RequestCtx("/Users",res.getId(),null,schemaManager);

            // Generate the creation operation that will be used to generate a replication message.
            PutOp op =  new PutOp(res.toJsonNode(null),ctx,null,0);
            op.prepareTestOp();
            return op;
        } catch (ScimException e) {
            fail("Error preparing operation: "+e.getMessage(),e);
        }
        return null;

    }

    private DeleteOp prepareDeleteUser(String id) {
        try {

            // Generate a requestctx to simulate a transaction id
            RequestCtx ctx = new RequestCtx("/Users",id,null,schemaManager);

            // Generate the creation operation that will be used to generate a replication message.
            return new DeleteOp(ctx,null,0);
        } catch (ScimException e) {
            fail("Error preparing operation: "+e.getMessage(),e);
        }
        return null;
    }

    private CreateOp prepareCreateUser(ScimResource res, String newId) {
        try {
            /*
              Prepare the resource as if it had been created locally to simulate a processed entry
             */

            // assign a Mongo compatible ID to simulate a local server
            res.setId(newId);

            Meta meta = res.getMeta();
            if (meta == null) {
                meta = new Meta();
                res.setMeta(meta);
            }
            // Not needed for TransactionRecord type
            Date created = Date.from(Instant.now());
            if (meta.getCreatedDate() == null) // only set the created date if it does not already exist.
                meta.setCreatedDate(created);
            meta.setLastModifiedDate(created); // always set the modify date upon create.

            meta.setLocation("/Users/" + res.getId());
            String etag = res.calcVersionHash();
            meta.setVersion(etag);

            // Generate a requestctx to simulate a transaction id
            RequestCtx ctx = new RequestCtx("/Users",null,null,schemaManager);

            // Generate the creation operation that will be used to generate a replication message.
            return new CreateOp(res.toJsonNode(null),ctx,null,0);

        } catch (ScimException e) {
            fail("Error preparing operation: "+e.getMessage(),e);
        }
        return null;
    }

    @PreDestroy
    void shutdown() {
        producer.close();
    }

}
