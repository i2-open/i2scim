package com.independentid.scim.test.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.backend.memory.MemoryProvider;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.InjectionManager;
import com.independentid.scim.core.PoolManager;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.op.CreateOp;
import com.independentid.scim.op.DeleteOp;
import com.independentid.scim.op.Operation;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.protocol.ScimResponse;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.serializer.JsonUtil;
import com.independentid.set.SecurityEventToken;
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
        JsonNode node = JsonUtil.getJsonTree(userStream);
        RequestCtx ctx = new RequestCtx("Users", null, null, schemaManager);
        CreateOp op = new CreateOp(node, ctx, null, 0);
        poolManager.addJobAndWait(op);
        assertThat(op.isError()).as("User create succeeded").isFalse();
        return op.getResourceId();
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
