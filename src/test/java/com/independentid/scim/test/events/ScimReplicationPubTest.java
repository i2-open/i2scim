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
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.PoolManager;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.events.JsonKafkaDeserializer;
import com.independentid.scim.events.KafkaRepEventHandler;
import com.independentid.scim.op.*;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.protocol.ResourceResponse;
import com.independentid.scim.protocol.ScimResponse;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.resource.StringValue;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.serializer.JsonUtil;
import com.independentid.scim.test.misc.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@QuarkusTest
@TestProfile(ScimEventsTestProfile.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class ScimReplicationPubTest {

    @Inject
    ConfigMgr configMgr;

    @Inject
    SchemaManager schemaManager;

    @Inject
    PoolManager poolManager;

    @Inject
    BackendHandler handler;

    @Inject
    TestUtils testUtils;

    @ConfigProperty(name="scim.kafka.rep.bootstrap")
    String kafkaBoot;

    @ConfigProperty(name = KafkaRepEventHandler.KAFKA_CON_PREFIX+"topic", defaultValue = "rep")
    String repTopic;

    @ConfigProperty (name= "scim.kafka.rep.client.id")
    String svr_clientId;

    @ConfigProperty (name= "scim.kafka.rep.cluster.id",defaultValue="cluster1")
    String svr_groupId;

    static final String TEST_CLIENT = "TestClient2";
    static final String TEST_GROUP = TEST_CLIENT;

    private final static Logger logger = LoggerFactory.getLogger(ScimReplicationPubTest.class);

    private static final String testUserFile1 = "classpath:/schema/TestUser-bjensen.json";

    static KafkaConsumer<String, JsonNode> consumer = null;
    Set<TopicPartition> tparts = null;

    static ScimResource res;

    @Test
    public void a_initProvider() {
        logger.info("========== SCIM Repliction Publisher Test ==========");
        logger.info("A. Initializing tests");
        try {
            testUtils.resetProvider();
        } catch (ScimException | BackendException | IOException e) {
            fail("Unable to reset provider: "+e.getMessage(),e);
        }

        Properties props = new Properties();
        props.setProperty("bootstrap.servers", kafkaBoot);
        props.setProperty("group.id", TEST_GROUP);
        props.setProperty("client.id",TEST_CLIENT);
        props.setProperty("enable.auto.commit", "true");
        props.setProperty("auto.offset.reset","latest");
        //props.setProperty("broadcast","true");
        props.setProperty("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.setProperty("value.deserializer", JsonKafkaDeserializer.class.getName());
        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList(repTopic));
        consumer.commitSync();

        List<PartitionInfo> parts =  consumer.partitionsFor(repTopic);
        for (PartitionInfo part : parts) {
            System.err.println(part.toString());
        }

        tparts = consumer.assignment();
        for (TopicPartition tpart : tparts) {
            System.err.println(tpart.toString());
        }
        // clear any outstanding records
        consumer.poll(Duration.ofMillis(100));

    }

    @Test
    public void b_testMessageProducer()  {
        logger.info("B. Initiating SCIM transaction Test");

       // KafkaProducer<String,JsonNode> producer = KafkaProducer.create(vertx, producerProps);
        InputStream userStream;
        try {
            Operation.initialize(configMgr);
            handler.getProvider().init();
            userStream = ConfigMgr.findClassLoaderResource(testUserFile1);
            JsonNode node = JsonUtil.getJsonTree(userStream);
            //ScimResource ures = new ScimResource(schemaManager,node,"Users");
            RequestCtx ctx = new RequestCtx("/Users",null,null,schemaManager);
            ctx.getResourceContainer();
            CreateOp op = new CreateOp(node,ctx,null,0);
            //poolManager.initialize();
            poolManager.addJobAndWait(op);

            ScimResponse sr = op.getScimResponse();

            assertThat(sr).isInstanceOf(ResourceResponse.class);
            ResourceResponse resp = (ResourceResponse) sr;
            assertThat(resp.getStatus())
                    .isEqualTo(ScimResponse.ST_CREATED);
            res = resp.getResultResource();
        } catch (IOException | ScimException | BackendException e) {
            fail("Failed to start a SCIM create transaction: "+e.getMessage(),e);
        }


    }

    @Test
    public void c_readRepStream() {
        logger.info("C. Reading Kafka SCIM replication stream");

        int loopCnt = 0;
        boolean received = false;
        logger.debug("Waiting for replication event...");
        while (loopCnt < 12 && !received) {
            loopCnt++;
            ConsumerRecords<String, JsonNode> records = consumer.poll(Duration.ofMillis(5000));
            for (ConsumerRecord<String, JsonNode> rec : records) {
                logger.info("...rep event received.");
                logger.debug("TEST: Record received: "+rec.toString());
                logger.debug("TEST: Key="+rec.key());
                assertThat(rec.key())
                        .as("Message key matches created resource id")
                        .isEqualTo(res.getId());
                assertThat(rec.value())
                        .as("Check that event has a value")
                        .isNotNull();
                String hdrClient = null, hdrGroup = null,hdrTran= null;
                for (Header header : rec.headers()) {
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
                assertThat(hdrClient)
                        .as("Check transmitting client id present")
                        .isNotNull();
                assertThat(hdrClient)
                        .as("Check transmitting client id is "+svr_clientId)
                        .isEqualTo(svr_clientId);
                assertThat(hdrGroup)
                        .as("Check transmitting cluster id present")
                        .isNotNull();
                assertThat(hdrGroup)
                        .as("Check transmitting cluster id is "+svr_groupId)
                        .isEqualTo(svr_groupId);
                assertThat(hdrTran)
                        .as("Check transmitting trans id is present")
                        .isNotNull();

                Operation op = null;
                try {
                    op = BulkOps.parseOperation(rec.value(), null, 0, true);
                } catch (ScimException e) {
                    fail("Unable to parse received operation: "+e.getMessage());
                }
                assertThat(op)
                        .as("Check event was parsed as an operation")
                        .isNotNull();
                assertThat(op)
                        .as("Check operation is a Create Op")
                        .isInstanceOf(CreateOp.class);
                logger.debug ("Successfully parsed operation: "+op);
                received = true;
            }

        }
        //consumer.close();
        if (!received)
            fail ("Failed to receive message");

    }

    @Test
    public void d_putTest() {
        logger.info("D. Put Test");
        Attribute uname = schemaManager.findAttribute("userName",null);
        StringValue newUserName = new StringValue(uname,"username1");

        try {
            res.addValue(newUserName);
            RequestCtx ctx = new RequestCtx("/Users",res.getId(),null,schemaManager);
            PutOp op = new PutOp(res.toJsonNode(null),ctx,null,0);

            poolManager.addJobAndWait(op);

            ScimResponse sr = op.getScimResponse();

            assertThat(sr).isInstanceOf(ResourceResponse.class);
            ResourceResponse resp = (ResourceResponse) sr;
            assertThat(resp.getStatus())
                    .isEqualTo(ScimResponse.ST_OK);
            res = resp.getResultResource();

        } catch (ScimException e) {
            fail("Error preparing put operation: "+e.getMessage(),e);
        }

        int loopCnt = 0;
        boolean received = false;
        logger.debug("Waiting for replication event...");
        while (loopCnt < 12 && !received) {
            loopCnt++;
            ConsumerRecords<String, JsonNode> records = consumer.poll(Duration.ofMillis(5000));
            for (ConsumerRecord<String, JsonNode> rec : records) {
                logger.info("...rep event received.");
                logger.debug("TEST: Record received: "+rec.toString());
                logger.debug("TEST: Key="+rec.key());
                assertThat(rec.key())
                        .as("Message key matches created resource id")
                        .isEqualTo(res.getId());
                assertThat(rec.value())
                        .as("Check that event has a value")
                        .isNotNull();

                Operation op = null;
                try {
                    op = BulkOps.parseOperation(rec.value(), null, 0, true);
                } catch (ScimException e) {
                    fail("Unable to parse received operation: "+e.getMessage());
                }
                assertThat(op)
                        .as("Check event was parsed as an operation")
                        .isNotNull();
                assertThat(op)
                        .as("Check operation is a Create Op")
                        .isInstanceOf(PutOp.class);
                logger.debug ("Successfully parsed operation: "+op);
                received = true;
            }

        }
        assertThat(received).isTrue();
    }

    @Test
    public void e_deleteTest() {
        logger.info("E. Delete Test");

        try {
            RequestCtx ctx = new RequestCtx("/Users",res.getId(),null,schemaManager);
            DeleteOp op = new DeleteOp(ctx,null,0);

            poolManager.addJobAndWait(op);

            ScimResponse sr = op.getScimResponse();

            assertThat(sr.getStatus())
                    .isEqualTo(ScimResponse.ST_NOCONTENT);

        } catch (ScimException e) {
            fail("Error preparing put operation: "+e.getMessage(),e);
        }

        int loopCnt = 0;
        boolean received = false;
        logger.debug("Waiting for replication event...");
        while (loopCnt < 12 && !received) {
            loopCnt++;
            ConsumerRecords<String, JsonNode> records = consumer.poll(Duration.ofMillis(5000));
            for (ConsumerRecord<String, JsonNode> rec : records) {
                logger.info("...rep event received.");
                logger.debug("TEST: Record received: "+rec.toString());
                logger.debug("TEST: Key="+rec.key());
                assertThat(rec.key())
                        .as("Message key matches created resource id")
                        .isEqualTo(res.getId());
                assertThat(rec.value())
                        .as("Check that event has a value")
                        .isNotNull();

                Operation op = null;
                try {
                    op = BulkOps.parseOperation(rec.value(), null, 0, true);
                } catch (ScimException e) {
                    fail("Unable to parse received operation: "+e.getMessage());
                }
                assertThat(op)
                        .as("Check event was parsed as an operation")
                        .isNotNull();
                assertThat(op)
                        .as("Check operation is a Delete Op")
                        .isInstanceOf(DeleteOp.class);
                logger.debug ("Successfully parsed operation: "+op);
                received = true;
            }

        }
        assertThat(received)
                .isTrue();
    }


    @PreDestroy
    void shutdown() {
        consumer.commitSync();
        consumer.close();
    }

}
