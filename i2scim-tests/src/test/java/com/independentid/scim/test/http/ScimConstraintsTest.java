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
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.serializer.JsonUtil;
import com.independentid.scim.test.misc.TestUtils;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.*;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;


/**
 * This class tests support for HTTP Conditional headers per RFC7232, and SCIM 7644 Sec 3.14
 */
@QuarkusTest
@TestProfile(ScimHttpTestProfile.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class ScimConstraintsTest {

    private final static Logger logger = LoggerFactory.getLogger(ScimConstraintsTest.class);
    final static SimpleDateFormat headDate = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
    //private static String userSchemaId = "urn:ietf:params:scim:schemas:core:2.0:User";

    @Inject
    @Resource(name = "SchemaMgr")
    SchemaManager smgr;

    @Inject
    TestUtils testUtils;

    @TestHTTPResource("/")
    URL baseUrl;

    private static String user1url = "";
    private static String etag;
    private static String moddate;
    private static ScimResource userres;

    private final static Date start = new Date();

    private static final String testUserFile1 = "classpath:/schema/TestUser-bjensen.json";

    /**
     * This test actually resets and re-initializes the SCIM Mongo test database.
     */
    @Test
    public void a_AddEtagTest() {

        logger.info("========== Scim Constraints Test ==========");
        logger.info("\tA. Testing Add Request Headers Etags and Last-Modified");

        try {
            testUtils.resetProvider(true);
        } catch (ScimException | BackendException | IOException e) {
            Assertions.fail("Failed to reset provider: " + e.getMessage());
        }

        try {

            InputStream userStream = ConfigMgr.findClassLoaderResource(testUserFile1);

            URL rUrl = new URL(baseUrl, "/Users");
            String req = rUrl.toString();


            HttpPost post = new HttpPost(req);

            InputStreamEntity reqEntity = new InputStreamEntity(
                    userStream, -1, ContentType.create(ScimParams.SCIM_MIME_TYPE));
            reqEntity.setChunked(false);
            post.setEntity(reqEntity);

            HttpResponse resp = TestUtils.executeRequest(post);

            logger.debug("Response is: " + resp.getStatusLine());
            String body = EntityUtils.toString(resp.getEntity());
            logger.debug("Body:\n" + body);

            JsonNode userNode = JsonUtil.getJsonTree(body);
            userres = new ScimResource(smgr, userNode, "Users");

            Header[] heads = resp.getAllHeaders();
            for (Header head : heads) {
                System.out.println(head.getName() + "\t" + head.getValue());
            }
            Header[] lastmods = resp.getHeaders(ScimParams.HEADER_LASTMOD);
            assertThat(lastmods.length)
                    .as("Confirm last modified returned")
                    .isEqualTo(1);
            moddate = lastmods[0].getValue();

            Header[] etags = resp.getHeaders(ScimParams.HEADER_ETAG);
            assertThat(etags.length)
                    .as("1 Etag was returned")
                    .isEqualTo(1);
            etag = etags[0].getValue();
            System.out.println("Etag:\t" + etag);


            Header[] hloc = resp.getHeaders(HttpHeaders.LOCATION);
            if (hloc == null || hloc.length == 0)
                fail("No HTTP Location header in create response");
            else {
                Header loc = hloc[0];
                assertThat(loc).isNotNull();
                assertThat(loc.getValue())
                        .as("Created object URL created in users endpoint")
                        .contains("/Users/");
                user1url = loc.getValue();  // This will be used to retrieve the user later
            }


        } catch (IOException | ParseException | ScimException e) {
            Assertions.fail("Exception occured creating bjenson. " + e.getMessage(), e);
        }
    }

    /**
     * This test attempts to retrieve the previously created user using the returned location.
     */
    @Test
    public void b_GetEtagTest() throws MalformedURLException {
        String req = TestUtils.mapPathToReqUrl(baseUrl, user1url);

        logger.info("\tB. Test Get Etags and Last-Modified headers");

        HttpUriRequest request = new HttpGet(req);

        try {
            HttpResponse resp = TestUtils.executeRequest(request);

            assertThat(resp.getStatusLine().getStatusCode())
                    .as("GET User - Check for status response 200 OK")
                    .isEqualTo(ScimResponse.ST_OK);

            Header[] lastmods = resp.getHeaders(ScimParams.HEADER_LASTMOD);
            assertThat(lastmods.length)
                    .as("Confirm last modified returned")
                    .isEqualTo(1);
            String getmoddate = lastmods[0].getValue();
            assertThat(getmoddate)
                    .as("Confirm moddate matches")
                    .isEqualTo(moddate);

            Header[] etags = resp.getHeaders(ScimParams.HEADER_ETAG);
            assertThat(etags.length)
                    .as("1 Etag was returned")
                    .isEqualTo(1);
            String get_etag = etags[0].getValue();

            assertThat(get_etag)
                    .as("Check etag is the same as for add")
                    .isEqualTo(etag);

        } catch (IOException e) {
            fail("Exception occured making GET request for bjensen", e);
        }
    }

    @Test
    public void c_GetConstraintsTest() throws IOException {
        String req = TestUtils.mapPathToReqUrl(baseUrl, user1url);

        logger.info("\tC. Get Constraint Tests");

        HttpGet request = new HttpGet(req);
        request.setHeader(ScimParams.HEADER_IFMODSINCE, moddate);

        HttpResponse resp = TestUtils.executeRequest(request);

        assertThat(resp.getStatusLine().getStatusCode())
                .as("Confirm not modified since")
                .isEqualTo(ScimResponse.ST_NOTMODIFIED);

        request = new HttpGet(req);
        request.setHeader(ScimParams.HEADER_IFMODSINCE, headDate.format(start));
        resp = TestUtils.executeRequest(request);

        assertThat(resp.getStatusLine().getStatusCode())
                .as("Response is returned as resource is newer")
                .isEqualTo(ScimResponse.ST_OK);

        logger.info("\t\tTesting IF_NONEMATCH:" + etag);
        request = new HttpGet(req);
        request.setHeader(ScimParams.HEADER_IFNONEMATCH, etag);
        resp = TestUtils.executeRequest(request);
        assertThat(resp.getStatusLine().getStatusCode())
                .as("Check that precondition fails (because it is the same)")
                .isEqualTo(ScimResponse.ST_NOTMODIFIED);

        request = new HttpGet(req);
        request.setHeader(ScimParams.HEADER_IFNONEMATCH, "\"afafafafaf\"");
        resp = TestUtils.executeRequest(request);
        assertThat(resp.getStatusLine().getStatusCode())
                .as("Check that precondition fails (because it is the same)")
                .isEqualTo(ScimResponse.ST_OK);
    }

    @Test
    public void d_PutConstraintsTest() throws IOException, SchemaException {
        String req = TestUtils.mapPathToReqUrl(baseUrl, user1url);

        logger.info("\tD. PUT Constraints Test");

        Attribute unameAttr = smgr.findAttribute("username", null);
        StringValue val = new StringValue(unameAttr, "test1");
        userres.addValue(val);

        String body = userres.toJsonString();

        HttpPut putreq = new HttpPut(req);
        StringEntity reqentity = new StringEntity(body);
        putreq.setEntity(reqentity);
        putreq.setHeader(ScimParams.HEADER_IFUNMODSINCE, headDate.format(start));

        HttpResponse resp = TestUtils.executeRequest(putreq);
        assertThat(resp.getStatusLine().getStatusCode())
                .as("Check for precondition failed")
                .isEqualTo(ScimResponse.ST_PRECONDITION);

        putreq = new HttpPut(req);
        reqentity = new StringEntity(body);
        putreq.setEntity(reqentity);
        putreq.setHeader(ScimParams.HEADER_IFUNMODSINCE, moddate);

        resp = TestUtils.executeRequest(putreq);
        assertThat(resp.getStatusLine().getStatusCode())
                .as("Check for success")
                .isEqualTo(ScimResponse.ST_OK);

        Header[] etags = resp.getHeaders(ScimParams.HEADER_ETAG);
        assertThat(etags.length)
                .as("1 Etag was returned")
                .isEqualTo(1);
        String new_etag = etags[0].getValue();
        assertThat(new_etag)
                .as("Check etag has changed")
                .isNotEqualTo(etag);

        val = new StringValue(unameAttr, "Test2");
        userres.addValue(val);
        body = userres.toJsonString();

        // try a request that fails due to out of date etag
        putreq = new HttpPut(req);
        reqentity = new StringEntity(body);
        putreq.setEntity(reqentity);
        putreq.setHeader(ScimParams.HEADER_IFMATCH, etag);
        resp = TestUtils.executeRequest(putreq);
        assertThat(resp.getStatusLine().getStatusCode())
                .as("Check for etag precondition failed")
                .isEqualTo(ScimResponse.ST_PRECONDITION);

        putreq = new HttpPut(req);
        reqentity = new StringEntity(body);
        putreq.setEntity(reqentity);
        putreq.setHeader(ScimParams.HEADER_IFMATCH, new_etag);
        resp = TestUtils.executeRequest(putreq);
        assertThat(resp.getStatusLine().getStatusCode())
                .as("Check for match success")
                .isEqualTo(ScimResponse.ST_OK);

        // save the last etag for next test
        etags = resp.getHeaders(ScimParams.HEADER_ETAG);
        assertThat(etags.length)
                .as("1 Etag was returned")
                .isEqualTo(1);
        etag = etags[0].getValue();
        Header[] lastmods = resp.getHeaders(ScimParams.HEADER_LASTMOD);
        assertThat(lastmods.length)
                .as("Confirm last modified returned")
                .isEqualTo(1);
        moddate = lastmods[0].getValue();

    }

    @Test
    public void e_PatchConstraintsTest() throws ScimException, IOException {
        logger.info("\tE. Patch Constraints Test");
        String req = TestUtils.mapPathToReqUrl(baseUrl, user1url);
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

        RequestCtx ctx = new RequestCtx(req, smgr);
        JsonPatchRequest jpr = new JsonPatchRequest(reqJson, ctx);  // test the Json Parser constructor

        String patchRequestBody = jpr.toJsonNode().toPrettyString();

        HttpPatch patReq = new HttpPatch(req);
        StringEntity body = new StringEntity(patchRequestBody);
        body.setChunked(false);
        patReq.setEntity(body);
        patReq.setHeader(ScimParams.HEADER_IFUNMODSINCE, headDate.format(start));

        HttpResponse resp = TestUtils.executeRequest(patReq);
        assertThat(resp.getStatusLine().getStatusCode())
                .as("Check for precondition failed")
                .isEqualTo(ScimResponse.ST_PRECONDITION);

        patReq = new HttpPatch(req);
        body = new StringEntity(patchRequestBody);
        patReq.setEntity(body);
        patReq.setHeader(ScimParams.HEADER_IFUNMODSINCE, moddate);

        resp = TestUtils.executeRequest(patReq);
        assertThat(resp.getStatusLine().getStatusCode())
                .as("Check for success")
                .isEqualTo(ScimResponse.ST_OK);

        Header[] etags = resp.getHeaders(ScimParams.HEADER_ETAG);
        assertThat(etags.length)
                .as("1 Etag was returned")
                .isEqualTo(1);
        String new_etag = etags[0].getValue();
        assertThat(new_etag)
                .as("Check etag has changed")
                .isNotEqualTo(etag);

        // Now remove the number that was added
        patchOp = new JsonPatchOp(JsonPatchOp.OP_ACTION_REMOVE, "User:phoneNumbers[type eq test]", null);

        reqJson = JsonUtil.getMapper().createObjectNode();
        snode = reqJson.putArray(ScimParams.ATTR_SCHEMAS);
        snode.add(ScimParams.SCHEMA_API_PatchOp);
        anode = reqJson.putArray(ScimParams.ATTR_PATCH_OPS);
        anode.add(patchOp.toJsonNode());

        jpr = new JsonPatchRequest(reqJson, ctx);  // test the Json Parser constructor
        patchRequestBody = jpr.toJsonNode().toPrettyString();

        // try a request that fails due to out of date etag
        patReq = new HttpPatch(req);
        body = new StringEntity(patchRequestBody);
        patReq.setEntity(body);
        patReq.setHeader(ScimParams.HEADER_IFMATCH, etag);
        resp = TestUtils.executeRequest(patReq);
        assertThat(resp.getStatusLine().getStatusCode())
                .as("Check for etag precondition failed")
                .isEqualTo(ScimResponse.ST_PRECONDITION);


        patReq = new HttpPatch(req);
        body = new StringEntity(patchRequestBody);
        patReq.setEntity(body);
        patReq.setHeader(ScimParams.HEADER_IFMATCH, new_etag);
        resp = TestUtils.executeRequest(patReq);
        assertThat(resp.getStatusLine().getStatusCode())
                .as("Check for match success")
                .isEqualTo(ScimResponse.ST_OK);

    }

    @Test
    public void f_HeadTest() throws IOException {
        String req = TestUtils.mapPathToReqUrl(baseUrl, user1url);

        logger.info("\tF. HEAD Test");

        HttpHead request = new HttpHead(req);

        HttpResponse resp = TestUtils.executeRequest(request);
        assertThat(resp.getStatusLine().getStatusCode())
                .as("Status is returned as OK")
                .isEqualTo(ScimResponse.ST_OK);

        HttpEntity entity = resp.getEntity();
        assertThat(entity)
                .as("Response has no body")
                .isNull();

        Header[] lastmods = resp.getHeaders(ScimParams.HEADER_LASTMOD);
        assertThat(lastmods.length)
                .as("Confirm last modified returned")
                .isEqualTo(1);
        String getmoddate = lastmods[0].getValue();
        assertThat(getmoddate)
                .as("Confirm moddate present")
                .isNotNull();

        Header[] etags = resp.getHeaders(ScimParams.HEADER_ETAG);
        assertThat(etags.length)
                .as("1 Etag was returned")
                .isEqualTo(1);
    }


}
