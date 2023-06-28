package com.independentid.scim.test.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.PoolManager;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.op.CreateOp;
import com.independentid.scim.op.Operation;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.serializer.JsonUtil;
import com.independentid.signals.SignalsEventHandler;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@QuarkusTest
@TestProfile(SignalsHandlerTestProfile.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class SignalsEventHandlerTest {
    private final static Logger logger = LoggerFactory.getLogger(SignalsEventHandler.class);
    private static final String testUserFile1 = "classpath:/data/TestUser-bjensen.json";
    private static final String testUserFile2 = "classpath:/data/TestUser-jsmith.json";

    @Inject
    PoolManager poolManager;

    @Inject
    ConfigMgr configMgr;

    @Inject
    SchemaManager schemaManager;

    @Inject
    TestUtils utils;

    @Test
    public void a_TestHello() {
        utils.resetMemDirectory();

        CloseableHttpClient client = HttpClients.createDefault();

        HttpGet get = new HttpGet("http://localhost:8081/signals/hello");
        try {
            CloseableHttpResponse resp = client.execute(get);
            assertThat(resp.getStatusLine().getStatusCode()).isEqualTo(200);
            String body = new String(resp.getEntity().getContent().readAllBytes());
            assertThat(body).isEqualTo("Hello there");
        } catch (IOException e) {
            fail(e.getMessage());
        }

        Operation.initialize(configMgr);
    }

    @Test
    public void b_EventHandlerTest() {
        try {
            List<Operation> ops = generateOps();
            poolManager.addJobAndWait(ops.get(0));
            poolManager.addJobAndWait(ops.get(1));
            logger.info("Mock Received is: " + MockSignalsServer.getReceived());
            //assertThat(MockSignalsServer.getReceived()).isEqualTo(1);
            int i = 0;
            while (i < 10 && MockSignalsServer.getReceived() < 2) {
                logger.info("Waiting for events to be received by Mock. Current: " + MockSignalsServer.getReceived());
                i++;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
            }
            assertThat(MockSignalsServer.getReceived()).isEqualTo(2);

            logger.info("Mock server pending count: " + MockSignalsServer.getPendingPollCnt());
            i = 0;
            while (i < 5 && MockSignalsServer.getPendingPollCnt() > 0) {
                logger.info("Waiting for pending count to go to 0. Now: " + MockSignalsServer.getPendingPollCnt());
                i++;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
            }
            assertThat(MockSignalsServer.getSent()).isEqualTo(2);
        } catch (IOException | ScimException e) {
            fail(e.getMessage());
        }

    }

    @Test
    public void c_ShutdownTest() {
        logger.info("shutting down");
    }

    protected List<Operation> generateOps() throws IOException, ScimException {
        List<Operation> ops = new ArrayList<>();

        InputStream userStream = ConfigMgr.findClassLoaderResource(testUserFile1);
        assertThat(userStream).isNotNull();
        JsonNode node1 = JsonUtil.getJsonTree(userStream);
        RequestCtx ctx1 = new RequestCtx("Users", null, null, schemaManager);
        CreateOp op1 = new CreateOp(node1, ctx1, null, 0);
        ops.add(op1);

        userStream = ConfigMgr.findClassLoaderResource(testUserFile2);
        assertThat(userStream).isNotNull();

        JsonNode node2 = JsonUtil.getJsonTree(userStream);

        RequestCtx ctx2 = new RequestCtx("Users", null, null, schemaManager);
        CreateOp op2 = new CreateOp(node2, ctx2, null, 0);
        ops.add(op2);

        return ops;
    }
}
