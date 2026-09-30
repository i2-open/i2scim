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
import com.independentid.scim.protocol.JsonPatchRequest;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.serializer.JsonUtil;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Multi-valued attribute PATCH semantics (issue #109, RFC 7644 §3.5.2 / RFC 7643 §2.4). Exercised through the public
 * {@link ScimResource#modifyResource(JsonPatchRequest, RequestCtx)} seam; results are asserted on the parsed JSON form
 * of the resource.
 */
@QuarkusTest
@TestProfile(ScimSubComponentTestProfile.class)
public class ScimPatchMultiValueTest {

    static final String BJENSEN = "classpath:/schema/TestUser-bjensen.json";
    static final String ENT = "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User";

    @Inject
    SchemaManager smgr;

    private ScimResource loadUser(String file) throws Exception {
        InputStream in = ConfigMgr.findClassLoaderResource(file);
        assert in != null;
        JsonNode node = JsonUtil.getJsonTree(in);
        return new ScimResource(smgr, node, "Users");
    }

    private ScimResource bjensen() throws Exception {
        return loadUser(BJENSEN);
    }

    /** Applies a PATCH request whose Operations array is supplied as JSON text. */
    private void patch(ScimResource res, String operationsJson) throws Exception {
        String body = "{\"schemas\":[\"urn:ietf:params:scim:api:messages:2.0:PatchOp\"],\"Operations\":"
                + operationsJson + "}";
        RequestCtx ctx = new RequestCtx("/Users/" + res.getId(), null, null, smgr);
        JsonPatchRequest jpr = new JsonPatchRequest(JsonUtil.getJsonTree(body), ctx);
        res.modifyResource(jpr, ctx);
    }

    private static JsonNode json(ScimResource res) throws Exception {
        return JsonUtil.getJsonTree(res.toJsonString());
    }

    /** The values of the given sub-attribute across all members of a multi-valued attribute. */
    private static List<String> subValues(JsonNode array, String sub) {
        List<String> out = new ArrayList<>();
        if (array == null)
            return out;
        for (JsonNode item : array)
            out.add(item.path(sub).asText(null));
        return out;
    }

    @Test
    public void addWithValueFilterAndObjectValueDoesNotRemoveMatchedValue() throws Exception {
        ScimResource res = bjensen();

        patch(res, "[{\"op\":\"add\",\"path\":\"emails[type eq \\\"work\\\"]\",\"value\":{\"display\":\"Work mail\"}}]");

        JsonNode emails = json(res).get("emails");
        assertThat(emails.isArray()).isTrue();
        assertThat(subValues(emails, "value"))
                .as("add never removes existing values")
                .containsExactlyInAnyOrder("bjensen@example.com", "babs@jensen.org");
    }

    @Test
    public void addWithValueFilterMergesObjectIntoMatchedValue() throws Exception {
        ScimResource res = bjensen();

        patch(res, "[{\"op\":\"add\",\"path\":\"emails[type eq \\\"work\\\"]\",\"value\":{\"display\":\"Work mail\"}}]");

        JsonNode work = null;
        for (JsonNode e : json(res).get("emails"))
            if ("work".equals(e.path("type").asText()))
                work = e;
        assertThat(work).isNotNull();
        assertThat(work.path("display").asText()).isEqualTo("Work mail");
        assertThat(work.path("value").asText()).isEqualTo("bjensen@example.com");
    }

    @Test
    public void addWithUnparseableValueIsInvalidValue() throws Exception {
        ScimResource res = bjensen();

        assertThatThrownBy(() -> patch(res, "[{\"op\":\"add\",\"path\":\"emails\",\"value\":12345}]"))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> patch(res, "[{\"op\":\"add\",\"path\":\"emails\",\"value\":[\"plain\"]}]"))
                .isInstanceOf(InvalidValueException.class);
        assertThat(subValues(json(res).get("emails"), "value"))
                .containsExactlyInAnyOrder("bjensen@example.com", "babs@jensen.org");
    }

    private static void assertFlatObjects(JsonNode array) {
        assertThat(array).isNotNull();
        assertThat(array.isArray()).isTrue();
        for (JsonNode item : array)
            assertThat(item.isObject()).as("multi-valued members are objects, not nested arrays: " + array).isTrue();
    }

    @Test
    public void extensionMultiValuedAttributeIsFlatAfterParse() throws Exception {
        ScimResource res = bjensen();

        JsonNode manager = json(res).get(ENT).get("manager");
        assertFlatObjects(manager);
        assertThat(subValues(manager, "value")).containsExactly("26118915-6090-4610-87e4-49d8ca9f808d");
    }

    @Test
    public void arrayAddToPopulatedExtensionMultiValuedAttributeIsFlat() throws Exception {
        ScimResource res = bjensen();

        patch(res, "[{\"op\":\"add\",\"path\":\"" + ENT + ":manager\",\"value\":[{\"value\":\"m2\"},{\"value\":\"m3\"}]}]");

        JsonNode manager = json(res).get(ENT).get("manager");
        assertFlatObjects(manager);
        assertThat(subValues(manager, "value"))
                .containsExactlyInAnyOrder("26118915-6090-4610-87e4-49d8ca9f808d", "m2", "m3");
    }

    @Test
    public void arrayAddToEmptyExtensionMultiValuedAttributeIsFlat() throws Exception {
        ScimResource res = bjensen();
        patch(res, "[{\"op\":\"remove\",\"path\":\"" + ENT + ":manager\"}]");
        assertThat(json(res).path(ENT).has("manager")).isFalse();

        patch(res, "[{\"op\":\"add\",\"path\":\"" + ENT + ":manager\",\"value\":[{\"value\":\"m2\"},{\"value\":\"m3\"}]}]");

        JsonNode manager = json(res).get(ENT).get("manager");
        assertFlatObjects(manager);
        assertThat(subValues(manager, "value")).containsExactlyInAnyOrder("m2", "m3");
    }

    @Test
    public void arrayAddToCoreMultiValuedAttributeIsFlat() throws Exception {
        ScimResource res = bjensen();

        patch(res, "[{\"op\":\"add\",\"path\":\"emails\",\"value\":[{\"value\":\"a@example.com\",\"type\":\"other\"},{\"value\":\"b@example.com\",\"type\":\"other\"}]}]");
        JsonNode emails = json(res).get("emails");
        assertFlatObjects(emails);
        assertThat(subValues(emails, "value")).containsExactlyInAnyOrder("bjensen@example.com", "babs@jensen.org",
                "a@example.com", "b@example.com");

        patch(res, "[{\"op\":\"remove\",\"path\":\"emails\"}]");
        patch(res, "[{\"op\":\"add\",\"path\":\"emails\",\"value\":[{\"value\":\"c@example.com\"}]}]");
        emails = json(res).get("emails");
        assertFlatObjects(emails);
        assertThat(subValues(emails, "value")).containsExactly("c@example.com");
    }

    @Test
    public void addingAnExistingValueLeavesAttributeUnchanged() throws Exception {
        ScimResource res = bjensen();
        JsonNode before = json(res).get("emails");

        patch(res, "[{\"op\":\"add\",\"path\":\"emails\",\"value\":[{\"value\":\"babs@jensen.org\",\"type\":\"home\"}]}]");

        JsonNode after = json(res).get("emails");
        assertThat(after.size()).isEqualTo(before.size());
        assertThat(subValues(after, "value")).containsExactlyInAnyOrder("bjensen@example.com", "babs@jensen.org");
    }

    @Test
    public void nestedArrayPersistedByEarlierReleasesIsFlattenedOnLoad() throws Exception {
        String body = "{\"schemas\":[\"urn:ietf:params:scim:schemas:core:2.0:User\",\"" + ENT + "\"],"
                + "\"userName\":\"nested\",\"" + ENT + "\":{\"manager\":[[{\"value\":\"m1\"}],{\"value\":\"m2\"}]}}";
        ScimResource res = new ScimResource(smgr, JsonUtil.getJsonTree(body), "Users");

        JsonNode manager = json(res).get(ENT).get("manager");
        assertFlatObjects(manager);
        assertThat(subValues(manager, "value")).containsExactlyInAnyOrder("m1", "m2");
    }

    private static List<String> primaries(JsonNode array) {
        List<String> out = new ArrayList<>();
        for (JsonNode item : array)
            if (item.path("primary").asBoolean(false))
                out.add(item.path("value").asText());
        return out;
    }

    @Test
    public void replaceWithArrayAndNoFilterReplacesAllValues() throws Exception {
        ScimResource res = bjensen();

        patch(res, "[{\"op\":\"replace\",\"path\":\"emails\",\"value\":[{\"value\":\"x@example.com\",\"type\":\"work\"},{\"value\":\"y@example.com\",\"type\":\"home\"}]}]");

        JsonNode emails = json(res).get("emails");
        assertFlatObjects(emails);
        assertThat(subValues(emails, "value")).containsExactlyInAnyOrder("x@example.com", "y@example.com");
    }

    @Test
    public void replaceWithValueFilterReplacesOnlyMatchedValue() throws Exception {
        ScimResource res = bjensen();

        patch(res, "[{\"op\":\"replace\",\"path\":\"emails[type eq \\\"home\\\"]\",\"value\":{\"value\":\"new@jensen.org\",\"type\":\"home\"}}]");

        assertThat(subValues(json(res).get("emails"), "value"))
                .containsExactlyInAnyOrder("bjensen@example.com", "new@jensen.org");
    }

    @Test
    public void replaceWithUnmatchedValueFilterIsNoTarget() throws Exception {
        ScimResource res = bjensen();

        assertThatThrownBy(() -> patch(res, "[{\"op\":\"replace\",\"path\":\"emails[type eq \\\"nothing\\\"]\",\"value\":{\"value\":\"z@example.com\"}}]"))
                .isInstanceOf(NoTargetException.class);
        assertThat(subValues(json(res).get("emails"), "value"))
                .containsExactlyInAnyOrder("bjensen@example.com", "babs@jensen.org");
    }

    @Test
    public void valueFilterOnAbsentAttributeIsNoTargetOrNoOp() throws Exception {
        ScimResource res = bjensen();
        patch(res, "[{\"op\":\"remove\",\"path\":\"emails\"}]");

        // remove of an absent value is a no-op (RFC 7644 §3.5.2.2)
        patch(res, "[{\"op\":\"remove\",\"path\":\"emails[type eq \\\"work\\\"]\"}]");
        assertThatThrownBy(() -> patch(res, "[{\"op\":\"replace\",\"path\":\"emails[type eq \\\"work\\\"]\",\"value\":{\"value\":\"z@example.com\"}}]"))
                .isInstanceOf(NoTargetException.class);
        assertThatThrownBy(() -> patch(res, "[{\"op\":\"replace\",\"path\":\"emails[type eq \\\"work\\\"].value\",\"value\":\"z@example.com\"}]"))
                .isInstanceOf(NoTargetException.class);
        assertThatThrownBy(() -> patch(res, "[{\"op\":\"add\",\"path\":\"emails[type eq \\\"work\\\"]\",\"value\":{\"display\":\"d\"}}]"))
                .isInstanceOf(NoTargetException.class);
        assertThatThrownBy(() -> patch(res, "[{\"op\":\"add\",\"path\":\"emails[type eq \\\"work\\\"].display\",\"value\":\"d\"}]"))
                .isInstanceOf(NoTargetException.class);
        assertThat(json(res).has("emails")).isFalse();
    }

    @Test
    public void addingPrimaryValueClearsOtherPrimaries() throws Exception {
        ScimResource res = bjensen();

        patch(res, "[{\"op\":\"add\",\"path\":\"emails\",\"value\":[{\"value\":\"p@example.com\",\"primary\":true}]}]");

        JsonNode emails = json(res).get("emails");
        assertThat(emails.size()).isEqualTo(3);
        assertThat(primaries(emails)).containsExactly("p@example.com");
    }

    @Test
    public void replacingPrimarySubAttributeClearsOtherPrimaries() throws Exception {
        ScimResource res = bjensen();

        patch(res, "[{\"op\":\"replace\",\"path\":\"emails[type eq \\\"home\\\"].primary\",\"value\":true}]");

        JsonNode emails = json(res).get("emails");
        assertThat(emails.size()).isEqualTo(2);
        assertThat(primaries(emails)).containsExactly("babs@jensen.org");

        patch(res, "[{\"op\":\"replace\",\"path\":\"emails[type eq \\\"work\\\"]\",\"value\":{\"value\":\"bjensen@example.com\",\"type\":\"work\",\"primary\":true}}]");
        emails = json(res).get("emails");
        assertThat(emails.size()).isEqualTo(2);
        assertThat(primaries(emails)).containsExactly("bjensen@example.com");
    }

    @Test
    public void twoPrimaryValuesInOneRequestIsInvalidValue() throws Exception {
        ScimResource res = bjensen();

        assertThatThrownBy(() -> patch(res, "[{\"op\":\"add\",\"path\":\"emails\",\"value\":[{\"value\":\"p@example.com\",\"primary\":true},{\"value\":\"q@example.com\",\"primary\":true}]}]"))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> patch(res, "[{\"op\":\"replace\",\"path\":\"emails\",\"value\":[{\"value\":\"p@example.com\",\"primary\":true},{\"value\":\"q@example.com\",\"primary\":true}]}]"))
                .isInstanceOf(InvalidValueException.class);
        assertThat(primaries(json(res).get("emails"))).containsExactly("bjensen@example.com");
    }

    @Test
    public void unknownSubAttributeInRemovePathIsBadRequest() throws Exception {
        ScimResource res = bjensen();

        assertThatThrownBy(() -> patch(res, "[{\"op\":\"remove\",\"path\":\"emails[type eq \\\"work\\\"].bogus\"}]"))
                .isInstanceOf(ScimException.class)
                .satisfies(e -> assertThat(((ScimException) e).getStatus()).isEqualTo(400));
        assertThat(subValues(json(res).get("emails"), "value"))
                .containsExactlyInAnyOrder("bjensen@example.com", "babs@jensen.org");
    }
}
