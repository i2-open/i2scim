/*
 * Copyright 2026.  Independent Identity Incorporated
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.independentid.scim.test.mongo;

import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.backend.mongo.MongoProvider;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.InjectionManager;
import com.independentid.scim.protocol.JsonPatchRequest;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.protocol.ScimResponse;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.serializer.JsonUtil;
import com.independentid.scim.test.misc.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #109: the issue #105 array-valued PATCH add, exercised against the Mongo backend. The patched resource is
 * persisted and re-read, and its multi-valued attributes (core and extension) must be flat arrays of objects.
 */
@QuarkusTest
@TestProfile(ScimMongoTestProfile.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class MongoPatchMultiValueTest {

    static final String ENT = "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User";

    @Inject
    SchemaManager smgr;

    @Inject
    TestUtils testUtils;

    static MongoProvider mp;
    static String userUrl;

    private static List<String> flatValues(JsonNode array) {
        assertThat(array).isNotNull();
        assertThat(array.isArray()).isTrue();
        List<String> vals = new ArrayList<>();
        for (JsonNode item : array) {
            assertThat(item.isObject()).as("member is an object, not a nested array: " + array).isTrue();
            vals.add(item.path("value").asText());
        }
        return vals;
    }

    private JsonNode reread() throws Exception {
        ScimResource res = mp.getResource(new RequestCtx(userUrl, null, null, smgr));
        return JsonUtil.getJsonTree(res.toJsonString());
    }

    @Test
    public void a_createUser() throws Exception {
        testUtils.resetProvider(true);
        mp = (MongoProvider) InjectionManager.getInstance().getProvider();

        InputStream in = ConfigMgr.findClassLoaderResource("classpath:/schema/TestUser-bjensen.json");
        assert in != null;
        ScimResource user = new ScimResource(smgr, JsonUtil.getJsonTree(in), "Users");
        user.setId(null);
        ScimResponse resp = mp.create(new RequestCtx("/Users", null, null, smgr), user);
        assertThat(resp.getStatus()).isEqualTo(ScimResponse.ST_CREATED);
        userUrl = resp.getLocation().substring(resp.getLocation().indexOf("/Users/"));

        assertThat(flatValues(reread().get(ENT).get("manager")))
                .containsExactly("26118915-6090-4610-87e4-49d8ca9f808d");
    }

    @Test
    public void b_arrayAddIsFlatAfterPersist() throws Exception {
        String body = "{\"schemas\":[\"urn:ietf:params:scim:api:messages:2.0:PatchOp\"],\"Operations\":["
                + "{\"op\":\"add\",\"path\":\"emails\",\"value\":[{\"value\":\"issue105@example.com\",\"type\":\"other\"}]},"
                + "{\"op\":\"add\",\"path\":\"" + ENT + ":manager\",\"value\":[{\"value\":\"m2\"}]}]}";
        RequestCtx ctx = new RequestCtx(userUrl, null, null, smgr);
        ScimResponse resp = mp.patch(ctx, new JsonPatchRequest(JsonUtil.getJsonTree(body), ctx));
        assertThat(resp.getStatus()).isEqualTo(ScimResponse.ST_OK);

        JsonNode user = reread();
        assertThat(flatValues(user.get("emails")))
                .containsExactlyInAnyOrder("bjensen@example.com", "babs@jensen.org", "issue105@example.com");
        assertThat(flatValues(user.get(ENT).get("manager")))
                .containsExactlyInAnyOrder("26118915-6090-4610-87e4-49d8ca9f808d", "m2");
    }
}
