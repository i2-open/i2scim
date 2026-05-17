package com.independentid.scim.test.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.independentid.scim.backend.memory.MemoryProvider;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.InjectionManager;
import com.independentid.scim.core.PoolManager;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.op.CreateOp;
import com.independentid.scim.op.DeleteOp;
import com.independentid.scim.op.Operation;
import com.independentid.scim.op.PatchOp;
import com.independentid.scim.op.PutOp;
import com.independentid.scim.protocol.JsonPatchOp;
import com.independentid.scim.protocol.JsonPatchRequest;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.protocol.ScimResponse;
import com.independentid.scim.resource.BooleanValue;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.resource.StringValue;
import com.independentid.scim.resource.Value;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.serializer.JsonUtil;
import com.independentid.set.SecurityEventToken;
import com.independentid.set.SubjectIdentifier;
import com.independentid.signals.RiscConfig;
import com.independentid.signals.RiscEventMapper;
import com.independentid.signals.RiscEventTypes;
import com.independentid.signals.SignalsEventMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
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

/**
 * Unit tests for {@link RiscEventMapper} — the pure transformation from a
 * completed {@link Operation} to RISC {@link SecurityEventToken}s. Slice 1
 * (#87) covers DELETE → Account Purged.
 */
@QuarkusTest
@TestProfile(RiscMapperTestProfile.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class RiscEventMapperTest {
    private final static Logger logger = LoggerFactory.getLogger(RiscEventMapperTest.class);

    private static final String testUserFile1 = "classpath:/data/TestUser-bjensen.json";

    @Inject
    SchemaManager schemaManager;

    @Inject
    TestUtils testUtils;

    @Inject
    MemoryProvider provider;

    @Inject
    PoolManager poolManager;

    @Inject
    ConfigMgr configMgr;

    private RiscEventMapper newMapper(RiscConfig config) {
        return new RiscEventMapper(config, InjectionManager.getInstance().getGenerator());
    }

    /** Creates a User in the memory backend and returns its generated id. */
    private String createUser(String userFile) throws ScimException, IOException, ParseException {
        InputStream userStream = ConfigMgr.findClassLoaderResource(userFile);
        assertThat(userStream).isNotNull();
        return createUserOp(uniquify((ObjectNode) JsonUtil.getJsonTree(userStream))).getResourceId();
    }

    /** Loads the bjensen test User as a mutable, uniquely-identified JSON object. */
    private ObjectNode loadTestUser() throws IOException {
        InputStream userStream = ConfigMgr.findClassLoaderResource(testUserFile1);
        assertThat(userStream).isNotNull();
        return uniquify((ObjectNode) JsonUtil.getJsonTree(userStream));
    }

    /**
     * Makes a test User unique so creations do not collide. The bjensen sample
     * carries a fixed {@code id}, {@code externalId} and {@code userName} — all
     * uniqueness-constrained — and the in-memory backend's map persists across
     * tests in this class.
     */
    private ObjectNode uniquify(ObjectNode user) {
        user.remove("id");
        user.remove("externalId");
        user.put("userName", "risc-user-" + java.util.UUID.randomUUID() + "@example.com");
        return user;
    }

    /** Creates a User from the given JSON and returns the completed CreateOp. */
    private CreateOp createUserOp(JsonNode node) throws ScimException {
        RequestCtx ctx = new RequestCtx("Users", null, null, schemaManager);
        CreateOp op = new CreateOp(node, ctx, null, 0);
        poolManager.addJobAndWait(op);
        assertThat(op.getScimResponse().getStatus())
                .as("User create returned 201").isEqualTo(ScimResponse.ST_CREATED);
        return op;
    }

    /** Patches the User at the given id and returns the completed PatchOp. */
    private PatchOp patchResource(String id, JsonPatchRequest jpr) throws ScimException {
        RequestCtx ctx = new RequestCtx("Users", id, null, schemaManager);
        PatchOp op = new PatchOp(jpr.toJsonNode(), ctx, null, 0);
        poolManager.addJobAndWait(op);
        return op;
    }

    /** Patches the User with a raw PatchOp JSON document and returns the completed PatchOp. */
    private PatchOp patchResourceRaw(String id, JsonNode patchNode) throws ScimException {
        RequestCtx ctx = new RequestCtx("Users", id, null, schemaManager);
        PatchOp op = new PatchOp(patchNode, ctx, null, 0);
        poolManager.addJobAndWait(op);
        return op;
    }

    /** A single-op JSON Patch request on the User {@code active} attribute. */
    private JsonPatchRequest activePatch(String action, Boolean value) throws SchemaException {
        Attribute activeAttr = schemaManager.findAttribute("User:active", null);
        JsonPatchRequest jpr = new JsonPatchRequest();
        Value v = (value == null) ? null : new BooleanValue(activeAttr, value);
        jpr.addOperation(new JsonPatchOp(action, "active", v));
        return jpr;
    }

    /** Replaces the User at the given id and returns the completed PutOp. */
    private PutOp putResource(String id, JsonNode node) throws ScimException {
        RequestCtx ctx = new RequestCtx("Users", id, null, schemaManager);
        PutOp op = new PutOp(node, ctx, null, 0);
        poolManager.addJobAndWait(op);
        return op;
    }

    /** Deletes the resource at the given container/id and returns the completed op. */
    private DeleteOp deleteResource(String container, String id) throws ScimException {
        RequestCtx ctx = new RequestCtx(container, id, null, schemaManager);
        DeleteOp op = new DeleteOp(ctx, null, 0);
        poolManager.addJobAndWait(op);
        return op;
    }

    @Test
    public void a_accountPurgedOnUserDelete() {
        logger.info("A. Account Purged on User delete");
        try {
            testUtils.resetMemDirectory();
            Operation.initialize(configMgr);

            String id = createUser(testUserFile1);
            DeleteOp delOp = deleteResource("Users", id);
            assertThat(delOp.isError()).as("User delete succeeded").isFalse();

            RiscEventMapper mapper = newMapper(new RiscConfig(true, List.of("*")));
            List<SecurityEventToken> events = mapper.mapToRiscEvents(delOp);

            assertThat(events).as("One RISC event for a User delete").hasSize(1);
            SecurityEventToken event = events.get(0);
            assertThat(event.GetEvent(RiscEventTypes.ACCOUNT_PURGED))
                    .as("SET carries the Account Purged event").isNotNull();
            assertThat(event.getJti()).as("SET has a jti").isNotBlank();
            assertThat(event.getSubjectIdentifier().id)
                    .as("Subject id is the deleted User").isEqualTo(id);
        } catch (Exception e) {
            fail("Error in Account Purged test: " + e.getMessage(), e);
        }
    }

    @Test
    public void b_accountPurgedSharesTxnAndToe() {
        logger.info("B. Account Purged shares txn + toe with the SCIM delete event");
        try {
            String id = createUser(testUserFile1);
            DeleteOp delOp = deleteResource("Users", id);
            assertThat(delOp.isError()).as("User delete succeeded").isFalse();

            SignalsEventMapper scimMapper = new SignalsEventMapper(
                    new ArrayList<>(), new ArrayList<>(), InjectionManager.getInstance().getGenerator());
            List<SecurityEventToken> scimEvents = scimMapper.MapOperationToSet(delOp);
            assertThat(scimEvents).as("SCIM delete event present").hasSize(1);
            SecurityEventToken scimEvent = scimEvents.get(0);

            RiscEventMapper riscMapper = newMapper(new RiscConfig(true, List.of("*")));
            List<SecurityEventToken> riscEvents = riscMapper.mapToRiscEvents(delOp);
            assertThat(riscEvents).as("RISC event present").hasSize(1);
            SecurityEventToken riscEvent = riscEvents.get(0);

            assertThat(riscEvent.getJti())
                    .as("RISC SET has its own jti").isNotEqualTo(scimEvent.getJti());
            assertThat(riscEvent.getTxn())
                    .as("RISC SET shares the SCIM event's txn")
                    .isNotNull()
                    .isEqualTo(scimEvent.getTxn());
            assertThat(riscEvent.getToe())
                    .as("RISC SET shares the SCIM event's toe")
                    .isEqualTo(scimEvent.getToe());
        } catch (Exception e) {
            fail("Error in txn/toe sharing test: " + e.getMessage(), e);
        }
    }

    @Test
    public void c_nonUserDeleteEmitsNothing() {
        logger.info("C. Non-User delete emits no RISC event");
        try {
            String groupJson = "{\"schemas\":[\"urn:ietf:params:scim:schemas:core:2.0:Group\"],"
                    + "\"displayName\":\"RISC Test Group\"}";
            JsonNode node = JsonUtil.getJsonTree(groupJson);
            RequestCtx createCtx = new RequestCtx("Groups", null, null, schemaManager);
            CreateOp createOp = new CreateOp(node, createCtx, null, 0);
            poolManager.addJobAndWait(createOp);
            assertThat(createOp.isError()).as("Group create succeeded").isFalse();

            DeleteOp delOp = deleteResource("Groups", createOp.getResourceId());
            assertThat(delOp.isError()).as("Group delete succeeded").isFalse();

            RiscEventMapper mapper = newMapper(new RiscConfig(true, List.of("*")));
            assertThat(mapper.mapToRiscEvents(delOp))
                    .as("No RISC event for a non-User resource").isEmpty();
        } catch (Exception e) {
            fail("Error in non-User test: " + e.getMessage(), e);
        }
    }

    @Test
    public void d_disabledConfigEmitsNothing() {
        logger.info("D. RISC disabled emits no event");
        try {
            String id = createUser(testUserFile1);
            DeleteOp delOp = deleteResource("Users", id);
            assertThat(delOp.isError()).as("User delete succeeded").isFalse();

            RiscEventMapper mapper = newMapper(new RiscConfig(false, List.of("*")));
            assertThat(mapper.mapToRiscEvents(delOp))
                    .as("No RISC event when RISC emission is disabled").isEmpty();
        } catch (Exception e) {
            fail("Error in disabled-config test: " + e.getMessage(), e);
        }
    }

    @Test
    public void e_accountPurgedTypeFilteredOut() {
        logger.info("E. account-purged excluded from types emits no event");
        try {
            String id = createUser(testUserFile1);
            DeleteOp delOp = deleteResource("Users", id);
            assertThat(delOp.isError()).as("User delete succeeded").isFalse();

            RiscEventMapper mapper = newMapper(new RiscConfig(true, List.of("account-disabled")));
            assertThat(mapper.mapToRiscEvents(delOp))
                    .as("No Account Purged when account-purged is filtered out").isEmpty();
        } catch (Exception e) {
            fail("Error in type-filter test: " + e.getMessage(), e);
        }
    }

    @Test
    public void g_accountEnabledOnCreateActiveAbsent() {
        logger.info("G. Account Enabled on User create when active is absent");
        try {
            testUtils.resetMemDirectory();
            Operation.initialize(configMgr);

            ObjectNode user = loadTestUser();
            user.remove("active");
            CreateOp op = createUserOp(user);

            RiscEventMapper mapper = newMapper(new RiscConfig(true, List.of("*")));
            List<SecurityEventToken> events = mapper.mapToRiscEvents(op);

            assertThat(events).as("One RISC event for a User create").hasSize(1);
            assertThat(events.get(0).GetEvent(RiscEventTypes.ACCOUNT_ENABLED))
                    .as("Absent active → Account Enabled").isNotNull();
        } catch (Exception e) {
            fail("Error in create-active-absent test: " + e.getMessage(), e);
        }
    }

    @Test
    public void h_accountDisabledOnCreateActiveFalse() {
        logger.info("H. Account Disabled on User create when active is false");
        try {
            ObjectNode user = loadTestUser();
            user.put("active", false);
            CreateOp op = createUserOp(user);

            RiscEventMapper mapper = newMapper(new RiscConfig(true, List.of("*")));
            List<SecurityEventToken> events = mapper.mapToRiscEvents(op);

            assertThat(events).as("One RISC event for a User create").hasSize(1);
            assertThat(events.get(0).GetEvent(RiscEventTypes.ACCOUNT_DISABLED))
                    .as("active=false → Account Disabled").isNotNull();
        } catch (Exception e) {
            fail("Error in create-active-false test: " + e.getMessage(), e);
        }
    }

    @Test
    public void i_accountDisabledOnPatchReplaceActiveFalse() {
        logger.info("I. Account Disabled on PATCH replace active=false");
        try {
            String id = createUser(testUserFile1);
            PatchOp op = patchResource(id, activePatch(JsonPatchOp.OP_ACTION_REPLACE, false));
            assertThat(op.isError()).as("Patch succeeded").isFalse();

            RiscEventMapper mapper = newMapper(new RiscConfig(true, List.of("*")));
            List<SecurityEventToken> events = mapper.mapToRiscEvents(op);

            assertThat(events).as("One RISC event for the active PATCH").hasSize(1);
            assertThat(events.get(0).GetEvent(RiscEventTypes.ACCOUNT_DISABLED))
                    .as("PATCH replace active=false → Account Disabled").isNotNull();
        } catch (Exception e) {
            fail("Error in patch-replace test: " + e.getMessage(), e);
        }
    }

    @Test
    public void j_accountDisabledOnPatchPathlessValueObject() {
        logger.info("J. Account Disabled on PATCH path-less value object {active:false}");
        try {
            String id = createUser(testUserFile1);
            String patchJson = "{\"schemas\":[\"urn:ietf:params:scim:api:messages:2.0:PatchOp\"],"
                    + "\"Operations\":[{\"op\":\"add\",\"value\":{\"active\":false}}]}";
            PatchOp op = patchResourceRaw(id, JsonUtil.getJsonTree(patchJson));
            assertThat(op.isError()).as("Patch succeeded").isFalse();

            RiscEventMapper mapper = newMapper(new RiscConfig(true, List.of("*")));
            List<SecurityEventToken> events = mapper.mapToRiscEvents(op);

            assertThat(events).as("One RISC event for the path-less PATCH").hasSize(1);
            assertThat(events.get(0).GetEvent(RiscEventTypes.ACCOUNT_DISABLED))
                    .as("Path-less value object with active=false → Account Disabled").isNotNull();
        } catch (Exception e) {
            fail("Error in patch-pathless test: " + e.getMessage(), e);
        }
    }

    @Test
    public void k_accountEnabledOnPatchRemoveActive() {
        logger.info("K. Account Enabled on PATCH remove active");
        try {
            ObjectNode user = loadTestUser();
            user.put("active", false);
            String id = createUserOp(user).getResourceId();

            PatchOp op = patchResource(id, activePatch(JsonPatchOp.OP_ACTION_REMOVE, null));
            assertThat(op.isError()).as("Patch succeeded").isFalse();

            RiscEventMapper mapper = newMapper(new RiscConfig(true, List.of("*")));
            List<SecurityEventToken> events = mapper.mapToRiscEvents(op);

            assertThat(events).as("One RISC event for the active PATCH remove").hasSize(1);
            assertThat(events.get(0).GetEvent(RiscEventTypes.ACCOUNT_ENABLED))
                    .as("PATCH remove active → Account Enabled").isNotNull();
        } catch (Exception e) {
            fail("Error in patch-remove test: " + e.getMessage(), e);
        }
    }

    @Test
    public void l_accountDisabledOnPutActiveChange() {
        logger.info("L. Account Disabled on PUT changing active true→false");
        try {
            CreateOp createOp = createUserOp(loadTestUser()); // bjensen active=true
            String id = createOp.getResourceId();
            RequestCtx readCtx = new RequestCtx("Users", id, null, schemaManager);
            ObjectNode body = (ObjectNode) createOp.getTransactionResource().toJsonNode(readCtx);
            body.put("active", false);

            PutOp op = putResource(id, body);
            assertThat(op.isError()).as("Put succeeded").isFalse();

            RiscEventMapper mapper = newMapper(new RiscConfig(true, List.of("*")));
            List<SecurityEventToken> events = mapper.mapToRiscEvents(op);

            assertThat(events).as("One RISC event for the active change").hasSize(1);
            assertThat(events.get(0).GetEvent(RiscEventTypes.ACCOUNT_DISABLED))
                    .as("PUT active true→false → Account Disabled").isNotNull();
        } catch (Exception e) {
            fail("Error in put-change test: " + e.getMessage(), e);
        }
    }

    @Test
    public void m_putWithoutActiveChangeEmitsNothing() {
        logger.info("M. PUT not changing active emits no RISC event");
        try {
            CreateOp createOp = createUserOp(loadTestUser()); // active=true
            String id = createOp.getResourceId();
            RequestCtx readCtx = new RequestCtx("Users", id, null, schemaManager);
            ObjectNode body = (ObjectNode) createOp.getTransactionResource().toJsonNode(readCtx);
            body.put("title", "Changed Title"); // change something other than active

            PutOp op = putResource(id, body);
            assertThat(op.isError()).as("Put succeeded").isFalse();

            RiscEventMapper mapper = newMapper(new RiscConfig(true, List.of("*")));
            assertThat(mapper.mapToRiscEvents(op))
                    .as("No RISC event when active is unchanged by a PUT").isEmpty();
        } catch (Exception e) {
            fail("Error in put-no-change test: " + e.getMessage(), e);
        }
    }

    @Test
    public void n_activeEventSharesTxnAndToe() {
        logger.info("N. Account Disabled SET shares txn + toe with the SCIM patch event");
        try {
            String id = createUser(testUserFile1);
            PatchOp op = patchResource(id, activePatch(JsonPatchOp.OP_ACTION_REPLACE, false));
            assertThat(op.isError()).as("Patch succeeded").isFalse();

            SignalsEventMapper scimMapper = new SignalsEventMapper(
                    new ArrayList<>(), new ArrayList<>(), InjectionManager.getInstance().getGenerator());
            List<SecurityEventToken> scimEvents = scimMapper.MapOperationToSet(op);
            assertThat(scimEvents).as("SCIM patch event present").isNotEmpty();
            SecurityEventToken scimEvent = scimEvents.get(0);

            RiscEventMapper riscMapper = newMapper(new RiscConfig(true, List.of("*")));
            List<SecurityEventToken> riscEvents = riscMapper.mapToRiscEvents(op);
            assertThat(riscEvents).as("RISC event present").hasSize(1);
            SecurityEventToken riscEvent = riscEvents.get(0);

            assertThat(riscEvent.getJti())
                    .as("RISC SET has its own jti").isNotEqualTo(scimEvent.getJti());
            assertThat(riscEvent.getTxn())
                    .as("RISC SET shares the SCIM event's txn")
                    .isNotNull().isEqualTo(scimEvent.getTxn());
            assertThat(riscEvent.getToe())
                    .as("RISC SET shares the SCIM event's toe")
                    .isEqualTo(scimEvent.getToe());
        } catch (Exception e) {
            fail("Error in txn/toe sharing test: " + e.getMessage(), e);
        }
    }

    @Test
    public void o_accountEnabledOnCreateActiveTrue() {
        logger.info("O. Account Enabled on User create when active is true");
        try {
            ObjectNode user = loadTestUser();
            user.put("active", true);
            CreateOp op = createUserOp(user);

            RiscEventMapper mapper = newMapper(new RiscConfig(true, List.of("*")));
            List<SecurityEventToken> events = mapper.mapToRiscEvents(op);

            assertThat(events).as("One RISC event for a User create").hasSize(1);
            assertThat(events.get(0).GetEvent(RiscEventTypes.ACCOUNT_ENABLED))
                    .as("active=true → Account Enabled").isNotNull();
        } catch (Exception e) {
            fail("Error in create-active-true test: " + e.getMessage(), e);
        }
    }

    @Test
    public void p_nonUserCreateEmitsNothing() {
        logger.info("P. Non-User create emits no RISC event");
        try {
            String groupJson = "{\"schemas\":[\"urn:ietf:params:scim:schemas:core:2.0:Group\"],"
                    + "\"displayName\":\"RISC Test Group " + java.util.UUID.randomUUID() + "\"}";
            JsonNode node = JsonUtil.getJsonTree(groupJson);
            RequestCtx ctx = new RequestCtx("Groups", null, null, schemaManager);
            CreateOp op = new CreateOp(node, ctx, null, 0);
            poolManager.addJobAndWait(op);
            assertThat(op.isError()).as("Group create succeeded").isFalse();

            RiscEventMapper mapper = newMapper(new RiscConfig(true, List.of("*")));
            assertThat(mapper.mapToRiscEvents(op))
                    .as("No RISC event for a non-User create").isEmpty();
        } catch (Exception e) {
            fail("Error in non-User create test: " + e.getMessage(), e);
        }
    }

    @Test
    public void q_identifierChangedOnPutUserName() {
        logger.info("Q. Identifier Changed on PUT changing userName");
        try {
            CreateOp createOp = createUserOp(loadTestUser());
            String id = createOp.getResourceId();
            RequestCtx readCtx = new RequestCtx("Users", id, null, schemaManager);
            ObjectNode body = (ObjectNode) createOp.getTransactionResource().toJsonNode(readCtx);
            body.put("userName", "renamed-" + java.util.UUID.randomUUID() + "@example.com");

            PutOp op = putResource(id, body);
            assertThat(op.isError()).as("Put succeeded").isFalse();

            RiscEventMapper mapper = newMapper(new RiscConfig(true, List.of("*")));
            List<SecurityEventToken> events = mapper.mapToRiscEvents(op);

            assertThat(events).as("One RISC event for the userName change").hasSize(1);
            assertThat(events.get(0).GetEvent(RiscEventTypes.IDENTIFIER_CHANGED))
                    .as("PUT changing userName → Identifier Changed").isNotNull();
        } catch (Exception e) {
            fail("Error in put-userName test: " + e.getMessage(), e);
        }
    }

    @Test
    public void r_identifierChangedOnPatchUserName() {
        logger.info("R. Identifier Changed on PATCH replacing userName");
        try {
            String id = createUser(testUserFile1);
            Attribute unAttr = schemaManager.findAttribute("User:userName", null);
            JsonPatchRequest jpr = new JsonPatchRequest();
            String newName = "patched-" + java.util.UUID.randomUUID() + "@example.com";
            jpr.addOperation(new JsonPatchOp(JsonPatchOp.OP_ACTION_REPLACE, "userName",
                    new StringValue(unAttr, newName)));

            PatchOp op = patchResource(id, jpr);
            assertThat(op.isError()).as("Patch succeeded").isFalse();

            RiscEventMapper mapper = newMapper(new RiscConfig(true, List.of("*")));
            List<SecurityEventToken> events = mapper.mapToRiscEvents(op);

            assertThat(events).as("One RISC event for the userName change").hasSize(1);
            assertThat(events.get(0).GetEvent(RiscEventTypes.IDENTIFIER_CHANGED))
                    .as("PATCH replacing userName → Identifier Changed").isNotNull();
        } catch (Exception e) {
            fail("Error in patch-userName test: " + e.getMessage(), e);
        }
    }

    @Test
    public void s_identifierChangedOnPatchPrimaryEmail() {
        logger.info("S. Identifier Changed on PATCH changing the primary email");
        try {
            String id = createUser(testUserFile1); // bjensen primary email bjensen@example.com
            String newEmail = "newprimary-" + java.util.UUID.randomUUID() + "@example.com";
            String patchJson = "{\"schemas\":[\"urn:ietf:params:scim:api:messages:2.0:PatchOp\"],"
                    + "\"Operations\":[{\"op\":\"add\",\"path\":\"emails\","
                    + "\"value\":{\"value\":\"" + newEmail + "\",\"type\":\"work\",\"primary\":true}}]}";
            PatchOp op = patchResourceRaw(id, JsonUtil.getJsonTree(patchJson));
            assertThat(op.isError()).as("Patch succeeded").isFalse();

            RiscEventMapper mapper = newMapper(new RiscConfig(true, List.of("*")));
            List<SecurityEventToken> events = mapper.mapToRiscEvents(op);

            assertThat(events).as("One RISC event for the primary-email change").hasSize(1);
            assertThat(events.get(0).GetEvent(RiscEventTypes.IDENTIFIER_CHANGED))
                    .as("PATCH changing the primary email → Identifier Changed").isNotNull();
        } catch (Exception e) {
            fail("Error in patch-primary-email test: " + e.getMessage(), e);
        }
    }

    /** A PATCH that removes the whole {@code emails} attribute. */
    private PatchOp removeEmailsPatch(String id) throws ScimException, IOException {
        String patchJson = "{\"schemas\":[\"urn:ietf:params:scim:api:messages:2.0:PatchOp\"],"
                + "\"Operations\":[{\"op\":\"remove\",\"path\":\"emails\"}]}";
        return patchResourceRaw(id, JsonUtil.getJsonTree(patchJson));
    }

    @Test
    public void t_identifierAttrSetHonored() {
        logger.info("T. Only configured identifier attributes drive Identifier Changed");
        try {
            String id = createUser(testUserFile1);
            String newEmail = "newprimary-" + java.util.UUID.randomUUID() + "@example.com";
            String patchJson = "{\"schemas\":[\"urn:ietf:params:scim:api:messages:2.0:PatchOp\"],"
                    + "\"Operations\":[{\"op\":\"add\",\"path\":\"emails\","
                    + "\"value\":{\"value\":\"" + newEmail + "\",\"type\":\"work\",\"primary\":true}}]}";
            PatchOp op = patchResourceRaw(id, JsonUtil.getJsonTree(patchJson));
            assertThat(op.isError()).as("Patch succeeded").isFalse();

            // emails is not in the configured identifier set — only userName is.
            RiscEventMapper mapper = newMapper(
                    new RiscConfig(true, List.of("*"), List.of("userName"), true));
            assertThat(mapper.mapToRiscEvents(op))
                    .as("Email change ignored when emails is not a configured identifier").isEmpty();
        } catch (Exception e) {
            fail("Error in identifier-attr-set test: " + e.getMessage(), e);
        }
    }

    @Test
    public void u_pureRemovalEmitsWhenAddRemoveIsChange() {
        logger.info("U. Pure removal of an identifier emits when addRemoveIsChange=true");
        try {
            String id = createUser(testUserFile1);
            PatchOp op = removeEmailsPatch(id);
            assertThat(op.isError()).as("Patch succeeded").isFalse();

            RiscEventMapper mapper = newMapper(
                    new RiscConfig(true, List.of("*"), List.of("userName", "emails"), true));
            List<SecurityEventToken> events = mapper.mapToRiscEvents(op);

            assertThat(events).as("One RISC event for the email removal").hasSize(1);
            assertThat(events.get(0).GetEvent(RiscEventTypes.IDENTIFIER_CHANGED))
                    .as("Pure removal → Identifier Changed when addRemoveIsChange=true").isNotNull();
        } catch (Exception e) {
            fail("Error in pure-removal-true test: " + e.getMessage(), e);
        }
    }

    @Test
    public void v_pureRemovalSuppressedWhenNotAddRemoveChange() {
        logger.info("V. Pure removal of an identifier is suppressed when addRemoveIsChange=false");
        try {
            String id = createUser(testUserFile1);
            PatchOp op = removeEmailsPatch(id);
            assertThat(op.isError()).as("Patch succeeded").isFalse();

            RiscEventMapper mapper = newMapper(
                    new RiscConfig(true, List.of("*"), List.of("userName", "emails"), false));
            assertThat(mapper.mapToRiscEvents(op))
                    .as("No Identifier Changed for a pure removal when addRemoveIsChange=false").isEmpty();
        } catch (Exception e) {
            fail("Error in pure-removal-false test: " + e.getMessage(), e);
        }
    }

    @Test
    public void w_identifierEventSubjectPayloadTxnToe() {
        logger.info("W. Identifier Changed SET — scim subject, new-value payload, shared txn/toe");
        try {
            CreateOp createOp = createUserOp(loadTestUser());
            String id = createOp.getResourceId();
            RequestCtx readCtx = new RequestCtx("Users", id, null, schemaManager);
            ObjectNode body = (ObjectNode) createOp.getTransactionResource().toJsonNode(readCtx);
            String newName = "renamed-" + java.util.UUID.randomUUID() + "@example.com";
            body.put("userName", newName);

            PutOp op = putResource(id, body);
            assertThat(op.isError()).as("Put succeeded").isFalse();

            SignalsEventMapper scimMapper = new SignalsEventMapper(
                    new ArrayList<>(), new ArrayList<>(), InjectionManager.getInstance().getGenerator());
            List<SecurityEventToken> scimEvents = scimMapper.MapOperationToSet(op);
            assertThat(scimEvents).as("SCIM put event present").isNotEmpty();
            SecurityEventToken scimEvent = scimEvents.get(0);

            RiscEventMapper mapper = newMapper(new RiscConfig(true, List.of("*")));
            List<SecurityEventToken> events = mapper.mapToRiscEvents(op);
            assertThat(events).as("One RISC event").hasSize(1);
            SecurityEventToken event = events.get(0);

            assertThat(event.getSubjectIdentifier().format)
                    .as("scim subject format").isEqualTo("scim");
            assertThat(event.getSubjectIdentifier().id)
                    .as("Subject carries the stable id").isEqualTo(id);
            assertThat(event.getSubjectIdentifier().uri)
                    .as("Subject carries the stable uri").isEqualTo("/Users/" + id);
            assertThat(event.GetEvent(RiscEventTypes.IDENTIFIER_CHANGED).get("new-value").asText())
                    .as("Payload carries the new identifier value").isEqualTo(newName);

            assertThat(event.getJti())
                    .as("RISC SET has its own jti").isNotEqualTo(scimEvent.getJti());
            assertThat(event.getTxn())
                    .as("RISC SET shares the SCIM event's txn")
                    .isNotNull().isEqualTo(scimEvent.getTxn());
            assertThat(event.getToe())
                    .as("RISC SET shares the SCIM event's toe")
                    .isEqualTo(scimEvent.getToe());
        } catch (Exception e) {
            fail("Error in identifier subject/payload test: " + e.getMessage(), e);
        }
    }

    /** A RiscConfig emitting all types with the given subject format. */
    private RiscConfig configWithSubjectFormat(String format) {
        return new RiscConfig(true, List.of("*"), RiscConfig.DEFAULT_IDENTIFIER_ATTRS, true, format);
    }

    @Test
    public void x_emailSubjectFormatOnAccountEvent() {
        logger.info("X. email subject format — Account Purged subject carries the primary email");
        try {
            String id = createUser(testUserFile1); // bjensen primary email bjensen@example.com
            DeleteOp delOp = deleteResource("Users", id);
            assertThat(delOp.isError()).as("User delete succeeded").isFalse();

            RiscEventMapper mapper = newMapper(configWithSubjectFormat("email"));
            List<SecurityEventToken> events = mapper.mapToRiscEvents(delOp);

            assertThat(events).as("One RISC event for a User delete").hasSize(1);
            SubjectIdentifier subject = events.get(0).getSubjectIdentifier();
            assertThat(subject.format).as("email subject format").isEqualTo("email");
            assertThat(subject.email)
                    .as("Subject carries the primary email").isEqualTo("bjensen@example.com");
        } catch (Exception e) {
            fail("Error in email-subject-format test: " + e.getMessage(), e);
        }
    }

    @Test
    public void y_usernameSubjectFormatOnAccountEvent() {
        logger.info("Y. username subject format — account event subject carries userName");
        try {
            ObjectNode user = loadTestUser();
            String userName = user.get("userName").asText();
            CreateOp op = createUserOp(user); // bjensen active=true → Account Enabled

            RiscEventMapper mapper = newMapper(configWithSubjectFormat("username"));
            List<SecurityEventToken> events = mapper.mapToRiscEvents(op);

            assertThat(events).as("One RISC event for a User create").hasSize(1);
            SubjectIdentifier subject = events.get(0).getSubjectIdentifier();
            assertThat(subject.format).as("username subject format").isEqualTo("username");
            assertThat(subject.username)
                    .as("Subject carries the userName").isEqualTo(userName);
        } catch (Exception e) {
            fail("Error in username-subject-format test: " + e.getMessage(), e);
        }
    }

    @Test
    public void z_phoneSubjectFormatOnAccountEvent() {
        logger.info("Z. phone subject format — account event subject carries the primary phone");
        try {
            ObjectNode user = loadTestUser();
            // bjensen's sample phoneNumbers has two entries and no primary flag;
            // give it a single entry so it has an unambiguous primary value.
            user.set("phoneNumbers",
                    JsonUtil.getJsonTree("[{\"value\":\"555-0100\",\"type\":\"work\"}]"));
            CreateOp op = createUserOp(user);

            RiscEventMapper mapper = newMapper(configWithSubjectFormat("phone"));
            List<SecurityEventToken> events = mapper.mapToRiscEvents(op);

            assertThat(events).as("One RISC event for a User create").hasSize(1);
            SubjectIdentifier subject = events.get(0).getSubjectIdentifier();
            assertThat(subject.format).as("phone subject format").isEqualTo("phone");
            assertThat(subject.phoneNumber)
                    .as("Subject carries the primary phone number").isEqualTo("555-0100");
        } catch (Exception e) {
            fail("Error in phone-subject-format test: " + e.getMessage(), e);
        }
    }

    @Test
    public void za_identifierChangedCarriesPriorValueUnderEmailFormat() {
        logger.info("ZA. email format — Identifier Changed subject carries the prior email");
        try {
            CreateOp createOp = createUserOp(loadTestUser()); // primary email bjensen@example.com
            String id = createOp.getResourceId();
            RequestCtx readCtx = new RequestCtx("Users", id, null, schemaManager);
            ObjectNode body = (ObjectNode) createOp.getTransactionResource().toJsonNode(readCtx);
            String newName = "renamed-" + java.util.UUID.randomUUID() + "@example.com";
            body.put("userName", newName);

            PutOp op = putResource(id, body);
            assertThat(op.isError()).as("Put succeeded").isFalse();

            RiscEventMapper mapper = newMapper(configWithSubjectFormat("email"));
            List<SecurityEventToken> events = mapper.mapToRiscEvents(op);

            assertThat(events).as("One RISC event for the userName change").hasSize(1);
            SecurityEventToken event = events.get(0);
            SubjectIdentifier subject = event.getSubjectIdentifier();
            assertThat(subject.format).as("email subject format").isEqualTo("email");
            assertThat(subject.email)
                    .as("Subject carries the prior (pre-image) email")
                    .isEqualTo("bjensen@example.com");
            assertThat(event.GetEvent(RiscEventTypes.IDENTIFIER_CHANGED).get("new-value").asText())
                    .as("Payload carries the new identifier value").isEqualTo(newName);
        } catch (Exception e) {
            fail("Error in email-format identifier-changed test: " + e.getMessage(), e);
        }
    }

    @Test
    public void zb_subjectFormatFallsBackToScimWhenUnsatisfiable() {
        logger.info("ZB. email format but no email — subject falls back to scim, event still emitted");
        try {
            ObjectNode user = loadTestUser();
            user.remove("emails"); // no email — the email subject format cannot be satisfied
            String id = createUserOp(user).getResourceId();
            DeleteOp delOp = deleteResource("Users", id);
            assertThat(delOp.isError()).as("User delete succeeded").isFalse();

            RiscEventMapper mapper = newMapper(configWithSubjectFormat("email"));
            List<SecurityEventToken> events = mapper.mapToRiscEvents(delOp);

            assertThat(events).as("Event still emitted despite the unsatisfiable format").hasSize(1);
            SecurityEventToken event = events.get(0);
            assertThat(event.GetEvent(RiscEventTypes.ACCOUNT_PURGED))
                    .as("SET carries the Account Purged event").isNotNull();
            SubjectIdentifier subject = event.getSubjectIdentifier();
            assertThat(subject.format)
                    .as("Falls back to the scim subject format").isEqualTo("scim");
            assertThat(subject.id)
                    .as("Fallback scim subject carries the stable id").isEqualTo(id);
        } catch (Exception e) {
            fail("Error in subject-format fallback test: " + e.getMessage(), e);
        }
    }

    @Test
    public void f_memoryProviderCapturesPreImage() {
        logger.info("F. MemoryProvider.delete() captures the pre-image on RequestCtx");
        try {
            String id = createUser(testUserFile1);
            RequestCtx ctx = new RequestCtx("Users", id, null, schemaManager);
            ScimResponse resp = provider.delete(ctx);
            assertThat(resp.getStatus())
                    .as("Delete succeeded").isEqualTo(ScimResponse.ST_NOCONTENT);

            ScimResource preImage = ctx.getPreImageResource();
            assertThat(preImage).as("Pre-image captured by delete()").isNotNull();
            assertThat(preImage.getId())
                    .as("Pre-image is the deleted User").isEqualTo(id);
        } catch (Exception e) {
            fail("Error in memory pre-image test: " + e.getMessage(), e);
        }
    }
}
