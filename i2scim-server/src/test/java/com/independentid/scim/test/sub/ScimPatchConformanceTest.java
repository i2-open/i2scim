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

package com.independentid.scim.test.sub;

import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.err.InvalidValueException;
import com.independentid.scim.core.err.NoTargetException;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.JsonPatchOp;
import com.independentid.scim.protocol.JsonPatchRequest;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.protocol.ScimResponse;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.serializer.JsonUtil;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RFC 7644 PATCH conformance/interop (issue #111): case-insensitive {@code op} values and {@code invalidPath} for
 * attributes not defined in the schema. Exercised through
 * {@link ScimResource#modifyResource(JsonPatchRequest, RequestCtx)}.
 */
@QuarkusTest
@TestProfile(ScimSubComponentTestProfile.class)
public class ScimPatchConformanceTest {

    static final String BJENSEN = "classpath:/schema/TestUser-bjensen.json";

    @Inject
    SchemaManager smgr;

    private ScimResource bjensen() throws Exception {
        InputStream in = ConfigMgr.findClassLoaderResource(BJENSEN);
        assert in != null;
        return new ScimResource(smgr, JsonUtil.getJsonTree(in), "Users");
    }

    private JsonPatchRequest request(ScimResource res, String operationsJson) throws Exception {
        String body = "{\"schemas\":[\"urn:ietf:params:scim:api:messages:2.0:PatchOp\"],\"Operations\":"
                + operationsJson + "}";
        RequestCtx ctx = new RequestCtx("/Users/" + res.getId(), null, null, smgr);
        return new JsonPatchRequest(JsonUtil.getJsonTree(body), ctx);
    }

    private void patch(ScimResource res, String operationsJson) throws Exception {
        RequestCtx ctx = new RequestCtx("/Users/" + res.getId(), null, null, smgr);
        res.modifyResource(request(res, operationsJson), ctx);
    }

    private static JsonNode json(ScimResource res) throws Exception {
        return JsonUtil.getJsonTree(res.toJsonString());
    }

    @Test
    public void capitalisedOpValuesBehaveLikeLowercase() throws Exception {
        ScimResource res = bjensen();

        patch(res, "[{\"op\":\"Add\",\"path\":\"title\",\"value\":\"Boss\"}]");
        assertThat(json(res).path("title").asText()).isEqualTo("Boss");

        patch(res, "[{\"op\":\"Replace\",\"path\":\"displayName\",\"value\":\"Barbara\"}]");
        assertThat(json(res).path("displayName").asText()).isEqualTo("Barbara");

        patch(res, "[{\"op\":\"REMOVE\",\"path\":\"nickName\"}]");
        assertThat(json(res).has("nickName")).isFalse();
    }

    @Test
    public void opValueIsNormalisedToLowercase() throws Exception {
        ScimResource res = bjensen();
        JsonPatchRequest jpr = request(res, "[{\"op\":\"Add\",\"path\":\"title\",\"value\":\"Boss\"},"
                + "{\"op\":\"rEpLaCe\",\"path\":\"title\",\"value\":\"Chief\"},"
                + "{\"op\":\"REMOVE\",\"path\":\"nickName\"}]");

        var iter = jpr.iterator();
        assertThat(iter.next().op).isEqualTo(JsonPatchOp.OP_ACTION_ADD);
        assertThat(iter.next().op).isEqualTo(JsonPatchOp.OP_ACTION_REPLACE);
        assertThat(iter.next().op).isEqualTo(JsonPatchOp.OP_ACTION_REMOVE);
    }

    @Test
    public void unknownOpIsStillInvalidValue() throws Exception {
        ScimResource res = bjensen();
        assertThatThrownBy(() -> request(res, "[{\"op\":\"move\",\"path\":\"title\",\"value\":\"x\"}]"))
                .isInstanceOf(InvalidValueException.class)
                .extracting(e -> ((ScimException) e).getScimType())
                .isEqualTo(ScimResponse.ERR_TYPE_BADVAL);
    }

    @Test
    public void undefinedAttributeInPathIsInvalidPath() throws Exception {
        ScimResource res = bjensen();
        for (String op : new String[]{
                "{\"op\":\"replace\",\"path\":\"nosuchattr\",\"value\":\"x\"}",
                "{\"op\":\"add\",\"path\":\"nosuchattr\",\"value\":\"x\"}",
                "{\"op\":\"remove\",\"path\":\"nosuchattr\"}",
                "{\"op\":\"replace\",\"path\":\"name.nosuchsub\",\"value\":\"x\"}",
                "{\"op\":\"replace\",\"path\":\"emails[type eq \\\"work\\\"].nosuchsub\",\"value\":\"x\"}"}) {
            assertThatThrownBy(() -> patch(res, "[" + op + "]"))
                    .as(op)
                    .isInstanceOf(ScimException.class)
                    .satisfies(e -> {
                        ScimException se = (ScimException) e;
                        assertThat(se.getScimType()).isEqualTo(ScimResponse.ERR_TYPE_PATH);
                        assertThat(se.getStatus()).isEqualTo(ScimResponse.ST_BAD_REQUEST);
                    });
        }
    }

    @Test
    public void valueFilterMatchingNothingIsStillNoTarget() throws Exception {
        ScimResource res = bjensen();
        assertThatThrownBy(() -> patch(res,
                "[{\"op\":\"replace\",\"path\":\"emails[type eq \\\"nomatch\\\"].value\",\"value\":\"z@example.com\"}]"))
                .isInstanceOf(NoTargetException.class);
    }
}
