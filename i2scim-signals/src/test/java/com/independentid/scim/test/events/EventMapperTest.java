package com.independentid.scim.test.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.backend.BackendException;
import com.independentid.scim.backend.memory.MemoryIdGenerator;
import com.independentid.scim.backend.memory.MemoryProvider;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.op.CreateOp;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.resource.Meta;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.ssef.EventMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.annotation.Resource;
import jakarta.inject.Inject;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.fail;


@QuarkusTest
@TestMethodOrder(MethodOrderer.MethodName.class)
public class EventMapperTest {
    private final static Logger logger = LoggerFactory.getLogger(EventMapperTest.class);

    @Inject
    @Resource(name = "SchemaMgr")
    SchemaManager schemaManager;

    @Inject
    TestUtils testUtils;


    @Inject
    @Resource(name = "MemoryIdGen")
    MemoryIdGenerator generator;

    private static final String testUserFile1 = "classpath:/data/TestUser-bjensen.json";
    private static final String testUserFile2 = "classpath:/data/TestUser-jsmith.json";

    private static String id1=null,id2=null;
    private static ScimResource res1=null;

    static CreateOp op1=null, op2 = null;
    @Test
    public void a_initTests() {
        logger.info("=============== SCIM Events Handler tests ===============");
/*
        try {
            testUtils.resetMemoryDb(provider);
        } catch (ScimException | BackendException | IOException e) {
            logger.error("Error resetting memory provider: "+e.getMessage());
        }

 */
    }

    @Test
    public void b_MapperTest() {
        id1 = generator.getNewIdentifier();
        id2 = generator.getNewIdentifier();
        res1 = testUtils.loadResource(testUserFile1,"Users");
        ScimResource res2 = testUtils.loadResource(testUserFile2, "Users");
        op1 = prepareCreateUser(res1,id1);
        op2 = prepareCreateUser(res2,id2);

        JsonNode node = EventMapper.MapOperation(op1);


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
}
