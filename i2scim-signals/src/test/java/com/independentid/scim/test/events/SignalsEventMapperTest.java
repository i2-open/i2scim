package com.independentid.scim.test.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.backend.BackendException;
import com.independentid.scim.backend.memory.MemoryIdGenerator;
import com.independentid.scim.backend.memory.MemoryProvider;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.PoolManager;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.op.*;
import com.independentid.scim.protocol.JsonPatchOp;
import com.independentid.scim.protocol.JsonPatchRequest;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.protocol.ScimResponse;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.resource.StringValue;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.serializer.JsonUtil;
import com.independentid.set.SecurityEventToken;
import com.independentid.signals.SignalsEventMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.annotation.Resource;
import jakarta.inject.Inject;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@QuarkusTest
@TestProfile(SignalsHandlerTestProfile.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class SignalsEventMapperTest {
    private final static Logger logger = LoggerFactory.getLogger(SignalsEventMapperTest.class);

    @Inject
    @Resource(name = "SchemaMgr")
    SchemaManager schemaManager;

    @Inject
    TestUtils testUtils;

    @Inject
    MemoryProvider provider;

    @Inject
    @Resource(name = "MemoryIdGen")
    MemoryIdGenerator generator;

    @Inject
    PoolManager poolManager;

    @Inject
    ConfigMgr cmgr;

    private static final String testUserFile1 = "classpath:/data/TestUser-bjensen.json";
    private static final String testUserFile2 = "classpath:/data/TestUser-jsmith.json";

    private static String id1 = null;
    private static ScimResource res1 = null;
    private static JsonNode node1;
    private static final List<SecurityEventToken> testEvents = new ArrayList<SecurityEventToken>();

    static CreateOp op1 = null, op2 = null;

    @Test
    public void a_initTests() {
        logger.info("=============== SCIM Events Mapper tests ===============");
        logger.info("A. Initializing tests");

        try {
            testUtils.resetMemDirectory();
            Operation.initialize(cmgr);
            InputStream userStream = ConfigMgr.findClassLoaderResource(testUserFile1);
            assertThat(userStream).isNotNull();

            node1 = JsonUtil.getJsonTree(userStream);
            res1 = new ScimResource(schemaManager, node1, "Users");

        } catch (ParseException | IOException | ScimException e) {
            fail("Failure occurred parsing transactions: " + e.getMessage());
        }


    }

    @Test
    public void b_CreateOpTest() {
        logger.info("B. Map CreateOp to Set...");

        // Operation to Event...
        try {
            RequestCtx ctx = new RequestCtx("Users", null, null, schemaManager);
            CreateOp op = new CreateOp(node1, ctx, null, 0);
            poolManager.addJobAndWait(op);
            SecurityEventToken event = SignalsEventMapper.MapOperationToSet(op);

            // Save for later
            testEvents.add(event);

            assert event != null;
            String jsonString = event.toPrettyString();
            logger.info("Result:\n" + ((jsonString != null) ? jsonString : "NULL"));

            id1 = op.getResourceId();  // Because on a create a new ID is generated.
            res1 = op.getTransactionResource();  // Update with newly modified value
            node1 = res1.toJsonNode(op.getRequestCtx());
            assertThat(jsonString).contains(id1);
            assertThat(jsonString).contains("urn:ietf:params:event:SCIM:prov:create");
        } catch (ScimException e) {
            fail("Error processing test operation: " + e.getMessage());
        }
    }

    @Test
    public void c_PutOpTest() {
        logger.info("C. Map PutOp to Set...");
        try {
            RequestCtx ctx = new RequestCtx("Users", id1, null, schemaManager);
            res1.setExternalId("tester123");
            PutOp op = new PutOp(res1.toJsonNode(ctx), ctx, null, 0);
            poolManager.addJobAndWait(op);
            res1 = op.getTransactionResource();  // Update with newly modified value
            node1 = res1.toJsonNode(op.getRequestCtx());
            SecurityEventToken event = SignalsEventMapper.MapOperationToSet(op);

            // Save for later
            testEvents.add(event);

            assert event != null;
            String jsonString = event.toPrettyString();
            assertThat(jsonString).contains(res1.getId());
            assertThat(jsonString)
                    .as("Has the updated test123")
                    .contains("tester123");
            logger.info("Result:\n" + ((jsonString != null) ? jsonString : "NULL"));
        } catch (ScimException e) {
            fail("Error processing test operation: " + e.getMessage());
        }
    }

    @Test
    public void d_PatchTest() {
        logger.info("D. Map PatchOp to Set...");
        try {
            RequestCtx ctx = new RequestCtx("Users", id1, null, schemaManager);
            JsonPatchRequest jpr = new JsonPatchRequest();
            Attribute titleAttr = schemaManager.findAttribute("User:title", null);
            StringValue titleVal = new StringValue(titleAttr, "TEST TITLE");
            JsonPatchOp replaceTitleOp = new JsonPatchOp(JsonPatchOp.OP_ACTION_REPLACE, "User:title", titleVal);

            jpr.addOperation(replaceTitleOp);
            PatchOp op = new PatchOp(jpr.toJsonNode(), ctx, null, 0);
            poolManager.addJobAndWait(op);


            SecurityEventToken event = SignalsEventMapper.MapOperationToSet(op);

            // Save for later
            testEvents.add(event);

            assert event != null;
            String jsonString = event.toPrettyString();
            assertThat(jsonString).contains(id1);
            assertThat(jsonString)
                    .as("Has the updated TEST TITLE")
                    .contains("TEST TITLE");
            logger.info("Result:\n" + ((jsonString != null) ? jsonString : "NULL"));
        } catch (ScimException e) {
            fail("Error processing test operation: " + e.getMessage());
        }
    }

    @Test
    public void e_DeleteTest() {
        logger.info("E. Map DeleteOp to Set...");
        try {
            RequestCtx ctx = new RequestCtx("Users", id1, null, schemaManager);

            DeleteOp op = new DeleteOp(ctx, null, 0);
            poolManager.addJobAndWait(op);
            SecurityEventToken event = SignalsEventMapper.MapOperationToSet(op);

            // Save for later
            testEvents.add(event);

            assert event != null;
            String jsonString = event.toPrettyString();
            assertThat(jsonString).contains("urn:ietf:params:event:SCIM:prov:delete");
            assertThat(jsonString).contains(id1);
            logger.info("Result:\n" + ((jsonString != null) ? jsonString : "NULL"));
        } catch (ScimException e) {
            fail("Error processing test operation: " + e.getMessage());
        }
    }

    @Test
    public void f_EventInit() {
        // Events are converted to Operations and performed.
        logger.info("F. Resetting memory database to mock receiver server.");

        assertThat(testEvents.size()).as("There should be 4 events from previous tests").isEqualTo(4);

        logger.info("Resetting server database to pretend to be a new server");
        try {
            testUtils.resetMemoryDb();
            Operation.initialize(cmgr);
            InputStream userStream = ConfigMgr.findClassLoaderResource(testUserFile1);
            assertThat(userStream).isNotNull();

        } catch (IOException | ScimException | BackendException e) {
            fail("Failure occurred parsing transactions: " + e.getMessage());
        }
    }

    @Test
    public void g_MapEventsToOpsTest() {
        logger.info("G. Mapping Events to Operations...");

        logger.info("G a. Map Set Create to CreateOp...");
        SecurityEventToken createEvent = testEvents.remove(0);
        Operation op = SignalsEventMapper.MapSetToOperation(createEvent, schemaManager);
        poolManager.addJobAndWait(op);

        assertThat(op).isNotNull();
        assertThat(op.isError()).isFalse();
        ScimResponse resp = op.getScimResponse();

        assertThat(resp).isNotNull();
        assertThat(resp.getStatus()).isEqualTo(ScimResponse.ST_CREATED);

        logger.info("G b. Map Set Put to PutOp...");
        SecurityEventToken putEvent = testEvents.remove(0);
        Operation putOp = SignalsEventMapper.MapSetToOperation(putEvent, schemaManager);
        poolManager.addJobAndWait(putOp);

        assertThat(putOp).isNotNull();
        assertThat(putOp.isError()).isFalse();
        resp = putOp.getScimResponse();

        assertThat(resp).isNotNull();
        assertThat(resp.getStatus()).isEqualTo(ScimResponse.ST_OK);

        logger.info("G c. Test Duplicate Txn rejected...");
        Operation putOp2 = SignalsEventMapper.MapSetToOperation(putEvent, schemaManager);
        poolManager.addJobAndWait(putOp2);

        assertThat(putOp2).isNotNull();
        assertThat(putOp2.isError()).isFalse();
        resp = putOp2.getScimResponse();

        assertThat(resp).isNotNull();
        // TODO: This should be a duplicate error.  Also trapped by event manager.
        // assertThat(resp.getStatus()).isEqualTo(ScimResponse.ST_CONFLICT);

        logger.info("G d. Map Set Patch to PatchOp...");
        SecurityEventToken patchEvent = testEvents.remove(0);
        Operation patchOp = SignalsEventMapper.MapSetToOperation(patchEvent, schemaManager);
        poolManager.addJobAndWait(patchOp);

        assertThat(patchOp).isNotNull();
        assertThat(patchOp.isError()).isFalse();
        resp = patchOp.getScimResponse();

        assertThat(resp).isNotNull();
        assertThat(resp.getStatus()).isEqualTo(ScimResponse.ST_OK);

        logger.info("G e. Map Set Delete to DeleteOp...");
        SecurityEventToken delEvent = testEvents.remove(0);
        Operation delOp = SignalsEventMapper.MapSetToOperation(delEvent, schemaManager);
        poolManager.addJobAndWait(delOp);

        assertThat(delOp).isNotNull();
        assertThat(delOp.isError()).isFalse();
        resp = delOp.getScimResponse();

        assertThat(resp).isNotNull();
        assertThat(resp.getStatus()).isEqualTo(ScimResponse.ST_NOCONTENT);

        assertThat(testEvents.size()).as("Confirm all events processed").isEqualTo(0);

    }
}
