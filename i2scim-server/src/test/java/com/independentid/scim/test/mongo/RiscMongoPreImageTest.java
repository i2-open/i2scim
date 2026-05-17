package com.independentid.scim.test.mongo;

import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.backend.mongo.MongoProvider;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.InjectionManager;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.protocol.ScimResponse;
import com.independentid.scim.resource.ScimResource;
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
}
