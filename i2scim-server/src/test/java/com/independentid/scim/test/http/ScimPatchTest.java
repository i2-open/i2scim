/*
 * Copyright 2021.  Independent Identity Incorporated
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

package com.independentid.scim.test.http;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.independentid.scim.backend.BackendException;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.*;
import com.independentid.scim.resource.ComplexValue;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.resource.StringValue;
import com.independentid.scim.resource.Value;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.serializer.JsonUtil;
import com.independentid.scim.test.misc.TestUtils;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.client5.http.classic.methods.HttpPatch;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;


/**
 * This tests only basic functionality of SCIM Patch. {@link com.independentid.scim.test.sub.ScimResourceTest} contains
 * the full functionality test. This test checks basic Http requirements and function.
 */
@QuarkusTest
@TestProfile(ScimHttpTestProfile.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class ScimPatchTest {

    private final static Logger logger = LoggerFactory.getLogger(ScimPatchTest.class);

    //private static String userSchemaId = "urn:ietf:params:scim:schemas:core:2.0:User";

    @Inject
    SchemaManager smgr;

    @Inject
    TestUtils testUtils;

    @TestHTTPResource("/")
    URL baseUrl;

    private static String user1url = "", user2url = "", grpUrl = "";

    private static final String testUserFile1 = "classpath:/schema/TestUser-bjensen.json";
    private static final String testUserFile2 = "classpath:/schema/TestUser-jsmith.json";

    static String patchRequestBody;
    static ScimResource res1, res2;

    static JsonPatchRequest jpr;

    /**
     * This test actually resets and re-initializes the SCIM Mongo test database.
     */
    @Test
    public void a_initializeProvider() throws Exception {

        logger.info("========== Scim HTTP CRUD Test ==========");
        logger.info("\tA. Initializing test data");

        try {
            testUtils.resetProvider(true);
        } catch (ScimException | BackendException | IOException e) {
            Assertions.fail("Failed to reset provider: " + e.getMessage());
        }


    }

    /**
     * This test checks that a JSON user can be parsed into a SCIM Resource
     */
    @Test
    public void b_PrepareTestData() throws Exception {

        logger.info("\tB1. Add users and group...");

        try {

            InputStream userStream = ConfigMgr.findClassLoaderResource(testUserFile1);

            JsonNode userNode = JsonUtil.getJsonTree(userStream);
            res1 = new ScimResource(smgr, userNode, "Users");
            URL rUrl = new URL(baseUrl, "/Users");
            String req = rUrl.toString();


            HttpPost post = new HttpPost(req);

            StringEntity reqEntity = new StringEntity(userNode.toString());
            post.setEntity(reqEntity);

            logger.debug("Executing test add for bjensen: " + post.toString());
            //logger.debug(EntityUtils.toString(reqEntity));

            ClassicHttpResponse resp = TestUtils.executeRequest(post);

            Header[] hloc = resp.getHeaders(HttpHeaders.LOCATION);
            if (hloc == null || hloc.length == 0)
                fail("No HTTP Location header in create response");
            else {
                Header loc = hloc[0];
                user1url = loc.getValue();  // This will be used to retrieve the user later
            }
            assertThat(resp.getCode())
                    .as("Create user response status of 201")
                    .isEqualTo(ScimResponse.ST_CREATED);

            userStream = ConfigMgr.findClassLoaderResource(testUserFile2);
            post = new HttpPost(req);
            userNode = JsonUtil.getJsonTree(userStream);
            res2 = new ScimResource(smgr, userNode, "Users");
            reqEntity = new StringEntity(userNode.toString());

            post.setEntity(reqEntity);
            resp = TestUtils.executeRequest(post);

            hloc = resp.getHeaders(HttpHeaders.LOCATION);
            if (hloc == null || hloc.length == 0)
                fail("No HTTP Location header in create response");
            else {
                Header loc = hloc[0];
                user2url = loc.getValue();  // This will be used to retrieve the user later
            }

            assertThat(resp.getCode())
                    .as("Create user response status of 201")
                    .isEqualTo(ScimResponse.ST_CREATED);

        } catch (IOException e) {
            Assertions.fail("Exception occured creating users. " + e.getMessage(), e);
        } catch (ScimException | ParseException e) {
            Assertions.fail("Scim exception occured parsing users: " + e.getMessage(), e);
        }

        String jsonGroup = "{\n" +
                "     \"schemas\": [\"urn:ietf:params:scim:schemas:core:2.0:Group\"],\n" +
                "     \"id\": \"e9e30dba-f08f-4109-8486-d5c6a331660a\",\n" +
                "     \"displayName\": \"TEST Tour Guides\",\n" +
                "     \"members\": [\n";
        jsonGroup = jsonGroup + memberObj(user1url) + "\n]}";

        String req = TestUtils.mapPathToReqUrl(baseUrl, "/Groups");

        HttpPost postGroup = new HttpPost(req);
        StringEntity body = new StringEntity(jsonGroup);
        postGroup.setEntity(body);

        ClassicHttpResponse resp = null;
        try {
            resp = TestUtils.executeRequest(postGroup);
        } catch (IOException e) {
            fail("Failed to create group: " + e.getMessage(), e);
        }
        assert resp != null;
        assertThat(resp.getCode())
                .as("Create user response status of 201")
                .isEqualTo(ScimResponse.ST_CREATED);
        Header[] hloc = resp.getHeaders(HttpHeaders.LOCATION);
        if (hloc == null || hloc.length == 0)
            fail("No HTTP Location header in create response");
        else {
            Header loc = hloc[0];
            grpUrl = loc.getValue();  // This will be used to retrieve the user later
        }
    }

    private String memberObj(String ref) {
        String id = ref.substring(ref.lastIndexOf("/") + 1);
        return "{ \"value\": \"" + id + "\",\n" +
                "    \"$ref\": \"" + ref + "\",\n" +
                "	 \"type\": \"User\" }";
    }

    @Test
    public void c_GroupTest() throws Exception {
        logger.info("\tC. Test Modify Group...");

        ClassicHttpResponse resp = TestUtils.executeGet(baseUrl, grpUrl);

        assert resp != null;
        assertThat(resp.getCode())
                .as("GET Group- Check for status response 200 OK")
                .isEqualTo(ScimResponse.ST_OK);

        String body = EntityUtils.toString(resp.getEntity());

        assertThat(body)
                .as("Check that it is not a ListResponse")
                .doesNotContain(ScimParams.SCHEMA_API_ListResponse);

        assertThat(body)
                .as("Is user bjensen url")
                .contains(user1url);

        assertThat(body)
                .as("Does not have jsmith url")
                .doesNotContain(user2url);
        System.out.println("Entry retrieved:\n" + body);

        ObjectNode reqJson = JsonUtil.getMapper().createObjectNode();
        ArrayNode snode = reqJson.putArray(ScimParams.ATTR_SCHEMAS);
        snode.add(ScimParams.SCHEMA_API_PatchOp);

        ArrayNode anode = reqJson.putArray(ScimParams.ATTR_PATCH_OPS);
        String memStr = memberObj(user2url);
        String opStr = "{\"op\": \"add\", \"path\": \"members\", \"value\": " + memStr + "}";
        JsonNode memNode = JsonUtil.getJsonTree(opStr);

        anode.add(memNode);

        String req = TestUtils.mapPathToReqUrl(baseUrl, grpUrl);
        HttpPatch post = new HttpPatch(req);

        String requestBody = reqJson.toPrettyString();
        logger.info("\t...Patch request\n" + requestBody);
        StringEntity reqEntity = new StringEntity(requestBody);
        post.setEntity(reqEntity);

        logger.info("\t...Patching group to add JSmith");
        //logger.debug(EntityUtils.toString(reqEntity));

        resp = TestUtils.executeRequest(post);
        assertThat(resp.getCode())
                .as("Has HTTP response status of 200 - ok")
                .isEqualTo(ScimResponse.ST_OK);
        String rbody = EntityUtils.toString(resp.getEntity());
        assertThat(rbody)
                .as("Has smith ref of " + user2url)
                .contains(user2url);
        System.out.println("Response:\n" + rbody);
    }

    @Test
    public void ca_GroupAddMembersArray() throws Exception {
        logger.info("\tC-a. Group members add with an array (issue #109)");
        String user1id = user1url.substring(user1url.lastIndexOf('/') + 1);
        String user2id = user2url.substring(user2url.lastIndexOf('/') + 1);

        // Both users are already members: the array add must merge without duplicating or nesting.
        ClassicHttpResponse resp = sendPatch(grpUrl, "[{\"op\":\"add\",\"path\":\"members\",\"value\":["
                + memberObj(user1url) + "," + memberObj(user2url) + "]}]");
        assertThat(resp.getCode()).isEqualTo(ScimResponse.ST_OK);
        JsonNode members = JsonUtil.getJsonTree(EntityUtils.toString(resp.getEntity())).get("members");
        assertFlatValues(members, "value", user1id, user2id);
    }

    @Test
    public void d_CheckPatchUser() throws Exception {
        logger.info("D. Checking Patch User");

        Attribute phone = smgr.findAttribute("User:phoneNumbers", null);
        Attribute valAttr = phone.getSubAttribute("value");
        Attribute typAttr = phone.getSubAttribute("type");
        StringValue val = new StringValue(valAttr, "987-654-3210");
        StringValue type = new StringValue(typAttr, "test");
        Map<Attribute, Value> map = new HashMap<>();
        map.put(valAttr, val);
        map.put(typAttr, type);
        ComplexValue phoneVal = new ComplexValue(phone, map);

        JsonPatchOp patchOp = new JsonPatchOp(JsonPatchOp.OP_ACTION_ADD, "User:phoneNumbers", phoneVal);

        ObjectNode reqJson = JsonUtil.getMapper().createObjectNode();
        ArrayNode snode = reqJson.putArray(ScimParams.ATTR_SCHEMAS);
        snode.add(ScimParams.SCHEMA_API_PatchOp);
        ArrayNode anode = reqJson.putArray(ScimParams.ATTR_PATCH_OPS);
        anode.add(patchOp.toJsonNode());

        RequestCtx ctx = new RequestCtx(user2url, null, null, smgr);
        jpr = new JsonPatchRequest(reqJson, ctx);  // test the Json Parser constructor

        assertThat(jpr.getSize())
                .as("Check one operation parsed")
                .isEqualTo(1);

        patchRequestBody = jpr.toJsonNode().toPrettyString();
        logger.info("\t...JSmith patch request:\n" + patchRequestBody);

        String req = TestUtils.mapPathToReqUrl(baseUrl, user2url);

        HttpPatch patchUser = new HttpPatch(req);
        StringEntity body = new StringEntity(patchRequestBody);
        patchUser.setEntity(body);

        ClassicHttpResponse resp = TestUtils.executeRequest(patchUser);
        assertThat(resp.getCode())
                .as("Patch user response status of 200 OK")
                .isEqualTo(ScimResponse.ST_OK);

        HttpEntity entity = resp.getEntity();
        assertThat(entity)
                .isNotNull();
        String respbody = EntityUtils.toString(entity);

        logger.info("\t...user patch response:\n" + respbody);
        assertThat(respbody)
                .as("JSmith Has the new phone number")
                .contains("987-654-3210");
    }

    /**
     * Regression test for issue #105. A PATCH "add" to a multi-valued attribute supplies a
     * JSON array as its value (RFC 7644 §3.5.2.1). That array parses to a {@link
     * com.independentid.scim.resource.MultiValue} which must be merged into the existing
     * MultiValue, not nested inside it. A nested MultiValue previously corrupted the backend
     * index and produced HTTP 500 (ClassCastException "Unable to compare Value types").
     */
    @Test
    public void da_CheckPatchAddArrayMultiValued() throws Exception {
        logger.info("D-a. Checking Patch add of array-valued multi-valued attribute (issue #105)");

        String newEmail = "issue105@example.com";

        // Build a PatchOp whose "add" value is a JSON ARRAY, as real SCIM clients send.
        ObjectNode reqJson = JsonUtil.getMapper().createObjectNode();
        ArrayNode snode = reqJson.putArray(ScimParams.ATTR_SCHEMAS);
        snode.add(ScimParams.SCHEMA_API_PatchOp);
        ArrayNode ops = reqJson.putArray(ScimParams.ATTR_PATCH_OPS);
        ObjectNode op = ops.addObject();
        op.put("op", "add");
        op.put("path", "emails");
        ArrayNode valArray = op.putArray("value");
        ObjectNode emailNode = valArray.addObject();
        emailNode.put("value", newEmail);
        emailNode.put("type", "other");

        String body = reqJson.toPrettyString();
        logger.info("\t...issue #105 patch request:\n" + body);

        String req = TestUtils.mapPathToReqUrl(baseUrl, user2url);
        HttpPatch patch = new HttpPatch(req);
        patch.setEntity(new StringEntity(body));

        ClassicHttpResponse resp = TestUtils.executeRequest(patch);
        assertThat(resp.getCode())
                .as("Array-valued PATCH add returns 200 OK (was 500 before issue #105 fix)")
                .isEqualTo(ScimResponse.ST_OK);

        String respbody = EntityUtils.toString(resp.getEntity());
        logger.info("\t...issue #105 patch response:\n" + respbody);

        assertThat(respbody)
                .as("The newly added email is present")
                .contains(newEmail);
        assertThat(respbody)
                .as("The pre-existing email is retained")
                .contains("jsmith@example.com");

        // Issue #109: assert on the parsed result, not on substrings -- the emails array must be flat.
        JsonNode emails = JsonUtil.getJsonTree(respbody).get("emails");
        assertFlatValues(emails, "value", "jsmith@example.com", "jim@smithsrule.com", newEmail);
    }

    /** Issue #109: asserts a multi-valued attribute is a flat array of objects holding exactly the expected values. */
    static void assertFlatValues(JsonNode array, String sub, String... expected) {
        assertThat(array).as("multi-valued attribute present").isNotNull();
        assertThat(array.isArray()).isTrue();
        List<String> vals = new ArrayList<>();
        for (JsonNode item : array) {
            assertThat(item.isObject()).as("member is an object, not a nested array: " + array).isTrue();
            vals.add(item.path(sub).asText());
        }
        assertThat(vals).containsExactlyInAnyOrder(expected);
    }

    private ClassicHttpResponse sendPatch(String url, String operationsJson) throws Exception {
        String body = "{\"schemas\":[\"" + ScimParams.SCHEMA_API_PatchOp + "\"],\"Operations\":" + operationsJson + "}";
        HttpPatch patch = new HttpPatch(TestUtils.mapPathToReqUrl(baseUrl, url));
        patch.setEntity(new StringEntity(body));
        return TestUtils.executeRequest(patch);
    }

    @Test
    public void db_UnparseableAddIsInvalidValue() throws Exception {
        logger.info("D-b. Unparseable add value is 400 invalidValue (issue #109)");
        ClassicHttpResponse resp = sendPatch(user2url, "[{\"op\":\"add\",\"path\":\"emails\",\"value\":12345}]");
        assertThat(resp.getCode()).isEqualTo(ScimResponse.ST_BAD_REQUEST);
        assertThat(EntityUtils.toString(resp.getEntity())).contains("invalidValue");
    }

    @Test
    public void dc_ValueFilterOnAbsentAttributeIsNotServerError() throws Exception {
        logger.info("D-c. Value filter on an absent attribute is noTarget or no-op (issue #109)");
        ClassicHttpResponse resp = sendPatch(user1url, "[{\"op\":\"remove\",\"path\":\"x509Certificates\"}]");
        assertThat(resp.getCode()).isIn(ScimResponse.ST_OK, ScimResponse.ST_NOCONTENT);

        resp = sendPatch(user1url, "[{\"op\":\"remove\",\"path\":\"x509Certificates[value eq \\\"abc\\\"]\"}]");
        assertThat(resp.getCode()).as("remove of absent value is a no-op").isIn(ScimResponse.ST_OK, ScimResponse.ST_NOCONTENT);

        resp = sendPatch(user1url, "[{\"op\":\"replace\",\"path\":\"x509Certificates[value eq \\\"abc\\\"]\",\"value\":{\"value\":\"def\"}}]");
        assertThat(resp.getCode()).isEqualTo(ScimResponse.ST_BAD_REQUEST);
        assertThat(EntityUtils.toString(resp.getEntity())).contains("noTarget");
    }

    @Test
    public void dd_UnknownSubAttributeInRemovePathIsBadRequest() throws Exception {
        logger.info("D-d. Unknown sub-attribute in a remove path is 400 (issue #109)");
        ClassicHttpResponse resp = sendPatch(user1url, "[{\"op\":\"remove\",\"path\":\"emails[type eq \\\"work\\\"].bogus\"}]");
        assertThat(resp.getCode()).isEqualTo(ScimResponse.ST_BAD_REQUEST);
        assertThat(EntityUtils.toString(resp.getEntity())).contains(ScimResponse.ERR_TYPE_PATH);
    }

    @Test
    public void de_OpValueIsCaseInsensitive() throws Exception {
        logger.info("D-e. PATCH op values are matched case-insensitively (issue #111)");
        ClassicHttpResponse resp = sendPatch(user2url, "[{\"op\":\"Add\",\"path\":\"title\",\"value\":\"Case Add\"}]");
        assertThat(resp.getCode()).isIn(ScimResponse.ST_OK, ScimResponse.ST_NOCONTENT);
        assertThat(EntityUtils.toString(resp.getEntity())).contains("Case Add");

        resp = sendPatch(user2url, "[{\"op\":\"Replace\",\"path\":\"title\",\"value\":\"Case Replace\"}]");
        assertThat(resp.getCode()).isIn(ScimResponse.ST_OK, ScimResponse.ST_NOCONTENT);
        assertThat(EntityUtils.toString(resp.getEntity())).contains("Case Replace");

        resp = sendPatch(user2url, "[{\"op\":\"REMOVE\",\"path\":\"title\"}]");
        assertThat(resp.getCode()).isIn(ScimResponse.ST_OK, ScimResponse.ST_NOCONTENT);
        String body = EntityUtils.toString(resp.getEntity());
        assertThat(body == null ? "" : body).doesNotContain("Case Replace");

        resp = sendPatch(user2url, "[{\"op\":\"move\",\"path\":\"title\",\"value\":\"x\"}]");
        assertThat(resp.getCode()).isEqualTo(ScimResponse.ST_BAD_REQUEST);
        assertThat(EntityUtils.toString(resp.getEntity())).contains(ScimResponse.ERR_TYPE_BADVAL);
    }

    @Test
    public void df_UndefinedAttributeInPathIsInvalidPath() throws Exception {
        logger.info("D-f. Undefined attribute in a PATCH path is 400 invalidPath (issue #111)");
        ClassicHttpResponse resp = sendPatch(user1url, "[{\"op\":\"replace\",\"path\":\"nosuchattr\",\"value\":\"x\"}]");
        assertThat(resp.getCode()).isEqualTo(ScimResponse.ST_BAD_REQUEST);
        assertThat(EntityUtils.toString(resp.getEntity())).contains(ScimResponse.ERR_TYPE_PATH);

        logger.info("\t... a valid path whose value filter matches nothing is still noTarget");
        resp = sendPatch(user1url, "[{\"op\":\"replace\",\"path\":\"emails[type eq \\\"nomatch\\\"].value\",\"value\":\"z@example.com\"}]");
        assertThat(resp.getCode()).isEqualTo(ScimResponse.ST_BAD_REQUEST);
        assertThat(EntityUtils.toString(resp.getEntity())).contains(ScimResponse.ERR_TYPE_TARGET);
    }

    @Test
    public void e_NoTargetTest() throws Exception {
        logger.info("E. Checking No Target Response");
        // This is a valid request, however, there is no type equal to blah so No_target
        JsonPatchOp faultyValueOp = new JsonPatchOp(JsonPatchOp.OP_ACTION_REMOVE, "phoneNumbers[type eq blah].value", null);
        jpr = new JsonPatchRequest();
        jpr.addOperation(faultyValueOp);

        String req = TestUtils.mapPathToReqUrl(baseUrl, user1url);

        HttpPatch patchUser = new HttpPatch(req);
        StringEntity body = new StringEntity(jpr.toJsonNode().toPrettyString());
        patchUser.setEntity(body);

        ClassicHttpResponse resp = TestUtils.executeRequest(patchUser);
        assertThat(resp.getCode())
                .as("Patch resposne bad request")
                .isEqualTo(ScimResponse.ST_BAD_REQUEST);

        HttpEntity entity = resp.getEntity();
        assertThat(entity)
                .isNotNull();
        String respbody = EntityUtils.toString(entity);

        logger.info("\t...Response to no match:\n" + respbody);
        assertThat(respbody)
                .as("confirm noTarget Error")
                .contains("noTarget");
    }

    @Test
    public void f_InvalidValueTest() throws Exception {
        logger.info("F. Checking Invalid Value Response");
        JsonPatchOp faultyValueOp = new JsonPatchOp(JsonPatchOp.OP_ACTION_REPLACE, "phoneNumbers[type eq blah].value", null);
        jpr = new JsonPatchRequest();
        jpr.addOperation(faultyValueOp);

        String req = TestUtils.mapPathToReqUrl(baseUrl, user1url);

        HttpPatch patchUser = new HttpPatch(req);
        StringEntity body = new StringEntity(jpr.toJsonNode().toPrettyString());
        patchUser.setEntity(body);

        ClassicHttpResponse resp = TestUtils.executeRequest(patchUser);
        assertThat(resp.getCode())
                .as("Patch resposne bad request")
                .isEqualTo(ScimResponse.ST_BAD_REQUEST);

        HttpEntity entity = resp.getEntity();
        assertThat(entity)
                .isNotNull();
        String respbody = EntityUtils.toString(entity);

        logger.info("\t...Response to invalid value request:\n" + respbody);
        assertThat(respbody)
                .as("confirm invalid value error")
                .contains("invalidValue");
    }

    @Test
    public void g_MethodNotAllowedTest() throws Exception {
        logger.info("G. Checking PATCH on Container not allowed");
        JsonPatchOp dummyOp = new JsonPatchOp(JsonPatchOp.OP_ACTION_REMOVE, "phoneNumbers", null);
        jpr = new JsonPatchRequest();
        jpr.addOperation(dummyOp);

        String req = TestUtils.mapPathToReqUrl(baseUrl, "/Users");

        HttpPatch patchUser = new HttpPatch(req);
        StringEntity body = new StringEntity(jpr.toJsonNode().toPrettyString());
        patchUser.setEntity(body);

        ClassicHttpResponse resp = TestUtils.executeRequest(patchUser);
        assertThat(resp.getCode())
                .as("Patch resposne bad request")
                .isEqualTo(ScimResponse.ST_METHODNOTALLOWED);

    }
}
