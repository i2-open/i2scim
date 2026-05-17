package com.independentid.scim.test.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.PoolManager;
import com.independentid.scim.op.CreateOp;
import com.independentid.scim.op.DeleteOp;
import com.independentid.scim.op.Operation;
import com.independentid.scim.op.PatchOp;
import com.independentid.scim.protocol.JsonPatchOp;
import com.independentid.scim.protocol.JsonPatchRequest;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.resource.BooleanValue;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.serializer.JsonUtil;
import com.independentid.set.SecurityEventToken;
import com.independentid.signals.EventTypes;
import com.independentid.signals.RiscEventTypes;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * End-to-end test: a SCIM {@code User} delete drives an OpenID RISC Account
 * Purged Security Event Token onto the push stream, observed at the receiver,
 * sharing {@code txn} with the SCIM delete event. Slice 1 (#87).
 */
@QuarkusTest
@TestProfile(RiscEventTestProfile.class)
public class RiscEventHandlerTest {
    private final static Logger logger = LoggerFactory.getLogger(RiscEventHandlerTest.class);

    private static final String testUserFile1 = "classpath:/data/TestUser-bjensen.json";

    @Inject
    PoolManager poolManager;

    @Inject
    ConfigMgr configMgr;

    @Inject
    SchemaManager schemaManager;

    @Inject
    TestUtils utils;

    /** Scans the SETs received by the mock for one carrying the given event URI. */
    private SecurityEventToken findReceivedEvent(String eventUri) {
        for (SecurityEventToken token : MockSignalsServer.allReceived) {
            JsonNode events = token.GetEvents();
            if (events != null && events.has(eventUri))
                return token;
        }
        return null;
    }

    @Test
    public void accountPurgedPushedEndToEnd() {
        logger.info("=== RISC Account Purged end-to-end push ===");
        try {
            utils.resetMemDirectory();
            Operation.initialize(configMgr);
            MockSignalsServer.reset();

            // Create a User...
            InputStream userStream = ConfigMgr.findClassLoaderResource(testUserFile1);
            assertThat(userStream).isNotNull();
            JsonNode node = JsonUtil.getJsonTree(userStream);
            RequestCtx createCtx = new RequestCtx("Users", null, null, schemaManager);
            CreateOp createOp = new CreateOp(node, createCtx, null, 0);
            poolManager.addJobAndWait(createOp);
            assertThat(createOp.isError()).as("User create succeeded").isFalse();
            String id = createOp.getResourceId();

            // ...then delete it.
            RequestCtx deleteCtx = new RequestCtx("Users", id, null, schemaManager);
            DeleteOp deleteOp = new DeleteOp(deleteCtx, null, 0);
            poolManager.addJobAndWait(deleteOp);
            assertThat(deleteOp.isError()).as("User delete succeeded").isFalse();

            // Event publication is async — wait for the Account Purged SET to be pushed.
            SecurityEventToken purged = null;
            for (int i = 0; i < 15 && purged == null; i++) {
                purged = findReceivedEvent(RiscEventTypes.ACCOUNT_PURGED);
                if (purged == null) Thread.sleep(1000);
            }
            assertThat(purged)
                    .as("Account Purged SET pushed to the receiver").isNotNull();
            assertThat(purged.GetEvents().size())
                    .as("One event per SET").isEqualTo(1);

            SecurityEventToken scimDelete = findReceivedEvent(EventTypes.PROV_DELETE);
            assertThat(scimDelete)
                    .as("SCIM delete SET also pushed to the receiver").isNotNull();
            assertThat(scimDelete.getJti())
                    .as("RISC and SCIM SETs are distinct").isNotEqualTo(purged.getJti());

            assertThat(purged.getTxn())
                    .as("RISC SET shares txn with the SCIM delete event")
                    .isNotNull()
                    .isEqualTo(scimDelete.getTxn());
        } catch (Exception e) {
            fail("Error in RISC end-to-end test: " + e.getMessage(), e);
        }
    }

    @Test
    public void accountDisabledPushedEndToEnd() {
        logger.info("=== RISC Account Disabled end-to-end push ===");
        try {
            utils.resetMemDirectory();
            Operation.initialize(configMgr);
            MockSignalsServer.reset();

            // Create an (enabled) User. Drop the sample's fixed id/externalId and
            // give it a unique userName so it never collides with the bjensen used
            // by accountPurgedPushedEndToEnd, regardless of test order.
            InputStream userStream = ConfigMgr.findClassLoaderResource(testUserFile1);
            assertThat(userStream).isNotNull();
            ObjectNode node = (ObjectNode) JsonUtil.getJsonTree(userStream);
            node.remove("id");
            node.remove("externalId");
            node.put("userName", "risc-disable-" + java.util.UUID.randomUUID() + "@example.com");
            RequestCtx createCtx = new RequestCtx("Users", null, null, schemaManager);
            CreateOp createOp = new CreateOp(node, createCtx, null, 0);
            poolManager.addJobAndWait(createOp);
            assertThat(createOp.isError()).as("User create succeeded").isFalse();
            String id = createOp.getResourceId();

            // ...then disable it with a PATCH of active=false.
            Attribute activeAttr = schemaManager.findAttribute("User:active", null);
            JsonPatchRequest jpr = new JsonPatchRequest();
            jpr.addOperation(new JsonPatchOp(JsonPatchOp.OP_ACTION_REPLACE, "active",
                    new BooleanValue(activeAttr, false)));
            RequestCtx patchCtx = new RequestCtx("Users", id, null, schemaManager);
            PatchOp patchOp = new PatchOp(jpr.toJsonNode(), patchCtx, null, 0);
            poolManager.addJobAndWait(patchOp);
            assertThat(patchOp.isError()).as("User patch succeeded").isFalse();

            // Event publication is async — wait for the Account Disabled SET.
            SecurityEventToken disabled = null;
            for (int i = 0; i < 15 && disabled == null; i++) {
                disabled = findReceivedEvent(RiscEventTypes.ACCOUNT_DISABLED);
                if (disabled == null) Thread.sleep(1000);
            }
            assertThat(disabled)
                    .as("Account Disabled SET pushed to the receiver").isNotNull();
            assertThat(disabled.GetEvents().size())
                    .as("One event per SET").isEqualTo(1);

            SecurityEventToken scimPatch = findReceivedEvent(EventTypes.PROV_PATCH_FULL);
            assertThat(scimPatch)
                    .as("SCIM patch SET also pushed to the receiver").isNotNull();
            assertThat(scimPatch.getJti())
                    .as("RISC and SCIM SETs are distinct").isNotEqualTo(disabled.getJti());

            assertThat(disabled.getTxn())
                    .as("RISC SET shares txn with the SCIM patch event")
                    .isNotNull()
                    .isEqualTo(scimPatch.getTxn());
        } catch (Exception e) {
            fail("Error in RISC Account Disabled end-to-end test: " + e.getMessage(), e);
        }
    }
}
