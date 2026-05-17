package com.independentid.scim.test.mongo;

import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.backend.mongo.MongoProvider;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.InjectionManager;
import com.independentid.scim.protocol.JsonPatchOp;
import com.independentid.scim.protocol.JsonPatchRequest;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.protocol.ScimResponse;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.resource.StringValue;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.serializer.JsonUtil;
import com.independentid.scim.test.misc.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Confirms {@link MongoProvider#delete} captures the removed resource as the
 * pre-image on {@link RequestCtx} (ADR-0001) so RISC Account Purged has
 * subject material. Slice 1 (#87).
 */
@QuarkusTest
@TestProfile(ScimMongoTestProfile.class)
public class RiscMongoPreImageTest {
    private static final Logger logger = LoggerFactory.getLogger(RiscMongoPreImageTest.class);

    private static final String testUserFile1 = "classpath:/schema/TestUser-bjensen.json";

    @Inject
    SchemaManager smgr;

    @Inject
    TestUtils testUtils;

    @Test
    public void mongoDeleteCapturesPreImage() throws Exception {
        logger.info("========== MongoProvider pre-image capture (RISC) ==========");
        testUtils.resetProvider(true);
        MongoProvider mp = (MongoProvider) InjectionManager.getInstance().getProvider();
        assertThat(mp).isNotNull();
        assertThat(mp.ready()).isTrue();

        InputStream userStream = ConfigMgr.findClassLoaderResource(testUserFile1);
        assertThat(userStream).isNotNull();
        JsonNode node = JsonUtil.getJsonTree(userStream);
        ScimResource user = new ScimResource(smgr, node, "Users");
        user.setId(null);  // Mongo issues its own id

        RequestCtx createCtx = new RequestCtx("/Users", null, null, smgr);
        ScimResponse createResp = mp.create(createCtx, user);
        assertThat(createResp.getStatus())
                .as("User created").isEqualTo(ScimResponse.ST_CREATED);
        String userUrl = createResp.getLocation();

        RequestCtx deleteCtx = new RequestCtx(userUrl, null, null, smgr);
        ScimResponse delResp = mp.delete(deleteCtx);
        assertThat(delResp.getStatus())
                .as("User deleted").isEqualTo(ScimResponse.ST_NOCONTENT);

        ScimResource preImage = deleteCtx.getPreImageResource();
        assertThat(preImage)
                .as("Pre-image captured by MongoProvider.delete()").isNotNull();
        assertThat(preImage.getResourceType())
                .as("Pre-image is the deleted User").isEqualTo("User");
    }

    @Test
    public void mongoPutAndPatchCapturePreImage() throws Exception {
        logger.info("========== MongoProvider put/patch pre-image capture (RISC) ==========");
        testUtils.resetProvider(true);
        MongoProvider mp = (MongoProvider) InjectionManager.getInstance().getProvider();
        assertThat(mp).isNotNull();
        assertThat(mp.ready()).isTrue();

        InputStream userStream = ConfigMgr.findClassLoaderResource(testUserFile1);
        assertThat(userStream).isNotNull();
        ScimResource user = new ScimResource(smgr, JsonUtil.getJsonTree(userStream), "Users");
        user.setId(null);
        RequestCtx createCtx = new RequestCtx("/Users", null, null, smgr);
        ScimResponse createResp = mp.create(createCtx, user);
        assertThat(createResp.getStatus())
                .as("User created").isEqualTo(ScimResponse.ST_CREATED);
        String userUrl = createResp.getLocation();

        // PUT replaces the resource — the pre-image must be the prior state.
        InputStream replaceStream = ConfigMgr.findClassLoaderResource(testUserFile1);
        ScimResource replacement = new ScimResource(smgr, JsonUtil.getJsonTree(replaceStream), "Users");
        replacement.setId(null);
        RequestCtx putCtx = new RequestCtx(userUrl, null, null, smgr);
        ScimResponse putResp = mp.put(putCtx, replacement);
        assertThat(putResp.getStatus()).as("PUT succeeded").isLessThan(300);
        assertThat(putCtx.getPreImageResource())
                .as("Pre-image captured by MongoProvider.put()").isNotNull();
        assertThat(putCtx.getPreImageResource().getResourceType())
                .as("PUT pre-image is a User").isEqualTo("User");

        // PATCH mutates the resource — the pre-image must be the prior state.
        Attribute titleAttr = smgr.findAttribute("User:title", null);
        JsonPatchRequest jpr = new JsonPatchRequest();
        jpr.addOperation(new JsonPatchOp(JsonPatchOp.OP_ACTION_REPLACE, "title",
                new StringValue(titleAttr, "Changed Title")));
        RequestCtx patchCtx = new RequestCtx(userUrl, null, null, smgr);
        ScimResponse patchResp = mp.patch(patchCtx, jpr);
        assertThat(patchResp.getStatus()).as("PATCH succeeded").isLessThan(300);
        assertThat(patchCtx.getPreImageResource())
                .as("Pre-image captured by MongoProvider.patch()").isNotNull();
        assertThat(patchCtx.getPreImageResource().getResourceType())
                .as("PATCH pre-image is a User").isEqualTo("User");
    }
}
