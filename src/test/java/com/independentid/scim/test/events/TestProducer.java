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
import com.independentid.scim.events.EventManager;
import com.independentid.scim.events.JsonKafkaDeserializer;
import com.independentid.scim.events.KafkaRepEventHandler;
import com.independentid.scim.op.BulkOps;
import com.independentid.scim.op.CreateOp;
import com.independentid.scim.op.Operation;
import com.independentid.scim.protocol.RequestCtx;
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
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@QuarkusTest
@TestProfile(ScimEventsTestProfile.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class TestProducer {

    @Inject
    ConfigMgr configMgr;

    @Inject
    SchemaManager schemaManager;

    @Inject
    PoolManager poolManager;

    @Inject
    BackendHandler handler;

    @Inject
    KafkaRepEventHandler eventHandler;

    @Inject
    EventManager eventManager;

    @Inject
    TestUtils testUtils;

    @ConfigProperty(name = KafkaRepEventHandler.KAFKA_PUB_PREFIX+"topic", defaultValue = "rep")
    String repTopic;

    private final static Logger logger = LoggerFactory.getLogger(TestProducer.class);
    static int msgCount = 0;

    private static final String testUserFile1 = "classpath:/schema/TestUser-bjensen.json";
    private static final String testUserFile2 = "classpath:/schema/TestUser-jsmith.json";

    static KafkaConsumer<String, JsonNode> consumer = null;
    Set<TopicPartition> tparts = null;

    @BeforeAll
    static void resetData() throws ScimException, BackendException, IOException {

    }

    @Test
    public void a_initProvider() {
        try {
            testUtils.resetProvider();
        } catch (ScimException | BackendException | IOException e) {
            fail("Unable to reset provider: "+e.getMessage(),e);
        }
    }
    @Test
    public void b_initConsumer() {
        Properties props = new Properties();
        props.setProperty("bootstrap.servers", "10.1.10.101:9092");
        props.setProperty("group.id", "testGroup");
        props.setProperty("client.id","TestClient1");
        props.setProperty("enable.auto.commit", "true");
        props.setProperty("auto.offset.reset","latest");
        //props.setProperty("broadcast","true");
        props.setProperty("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.setProperty("value.deserializer", JsonKafkaDeserializer.class.getName());
        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList("rep"));
        consumer.commitSync();

        List<PartitionInfo> parts =  consumer.partitionsFor("rep");
        for (PartitionInfo part : parts) {
            System.err.println(part.toString());
        }

        tparts = consumer.assignment();
        for (TopicPartition tpart : tparts) {
            System.err.println(tpart.toString());
        }
        consumer.poll(Duration.ofMillis(10));

    }

    @Test
    public void c_testMessageProducer() throws IOException, ScimException, ParseException, ClassNotFoundException, BackendException, InstantiationException {
        Operation.initialize(configMgr);
        handler.getProvider().init();
       // KafkaProducer<String,JsonNode> producer = KafkaProducer.create(vertx, producerProps);
        InputStream userStream = ConfigMgr.getClassLoaderFile(testUserFile1);
        JsonNode node = JsonUtil.getJsonTree(userStream);
        //ScimResource ures = new ScimResource(schemaManager,node,"Users");
        RequestCtx ctx = new RequestCtx("/Users",null,null,schemaManager);
        ctx.getResourceContainer();
        CreateOp op = new CreateOp(node,ctx,null,0);
        //poolManager.initialize();
        poolManager.addJobAndWait(op);

        logger.info("Sending the test event");  //This is normally done by ScimV2Servlet
        eventManager.logEvent(op);

    }

    @SuppressWarnings("CatchMayIgnoreException")
    @Test
    public void d_checkServerMessageRead()  {
        int waitCnt = 0;
        while (eventHandler.hasNoReceivedEvents() && waitCnt < 5) {
            try {
                waitCnt++;
                Thread.sleep(5000);
            } catch (InterruptedException e) {

            }
        }
        if (eventHandler.getReceivedOps().size()> 0) {
            assertThat(eventHandler.getReceivedOps().size())
                    .as("Check 1 event receivecd")
                    .isEqualTo(1);
            assertThat(eventHandler.getSendErrorOps().size())
                    .as("Check for replication sending failrues")
                    .isEqualTo(0);
            return;
        }
        fail("No events received");

    }

    @Test
    public void e_readRepStream() {

        final int minBatchSize = 1;
        List<ConsumerRecord<String, JsonNode>> buffer = new ArrayList<>();
        int loopCnt = 0;
        boolean received = false;
        while (loopCnt < 12 && !received) {
            loopCnt++;
            ConsumerRecords<String, JsonNode> records = consumer.poll(Duration.ofMillis(5000));
            for (ConsumerRecord<String, JsonNode> record : records) {
                buffer.add(record);
            }
            if (buffer.size() >= minBatchSize) {
                ConsumerRecord<String,JsonNode> rec = buffer.remove(0);
                System.out.println("TEST: Record received: "+rec.toString());
                System.out.println("TEST: Key="+rec.key());
                if (rec.value() == null || rec.value().toString().equals("null")) {
                    System.err.println("TEST: --> had null value skipping.");
                    continue;
                }
                Operation op = null;
                try {
                    op = BulkOps.parseOperation(rec.value(), null, 0);
                } catch (ScimException e) {
                    fail("Unable to parse received operation: "+e.getMessage());
                }
                assert op != null;
                System.out.println("\t"+op.toString());
                received = true;
            }

        }
        consumer.close();
        if (!received)
            fail ("Failed to receive message");

    }








}
