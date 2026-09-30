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

package com.independentid.scim.test.misc;

import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.protocol.ScimParams;
import com.independentid.scim.protocol.ScimResponse;
import com.independentid.scim.serializer.JsonUtil;
import io.quarkus.test.common.http.TestHTTPResource;
import jakarta.inject.Inject;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPatch;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shared regression tests for issue #107: client-input failures must produce a SCIM error response (RFC 7644 §3.12)
 * with the correct status and scimType rather than a bare container 500. Concrete subclasses supply only the
 * {@code @QuarkusTest}/{@code @TestProfile} wiring for the backend under test.
 */
@TestMethodOrder(MethodOrderer.MethodName.class)
public abstract class AbstractErrorHandlingTest {

    static final Logger logger = LoggerFactory.getLogger(AbstractErrorHandlingTest.class);

    protected static final String MALFORMED_JSON = "{bad";

    @Inject
    protected TestUtils testUtils;

    @TestHTTPResource("/")
    protected URL baseUrl;

    /**
     * Hook for subclasses running with security enabled to add credentials to each request. Default: none.
     * @param request The request about to be executed.
     */
    protected void authorize(HttpUriRequestBase request) {
    }

    @Test
    public void a_init() throws Exception {
        logger.info("========== " + getClass().getSimpleName() + " ==========");
        testUtils.resetProvider(true);
    }

    @Test
    public void b_malformedCreateBodyIsInvalidSyntax() throws Exception {
        HttpPost post = new HttpPost(TestUtils.mapPathToReqUrl(baseUrl, "/Users"));
        post.setEntity(new StringEntity(MALFORMED_JSON, ContentType.APPLICATION_JSON));

        assertScimError(post, ScimResponse.ST_BAD_REQUEST, ScimResponse.ERR_TYPE_SYNTAX);
    }

    @Test
    public void b_malformedPutBodyIsInvalidSyntax() throws Exception {
        HttpPut put = new HttpPut(TestUtils.mapPathToReqUrl(baseUrl, "/Users/doesNotMatter"));
        put.setEntity(new StringEntity(MALFORMED_JSON, ContentType.APPLICATION_JSON));

        assertScimError(put, ScimResponse.ST_BAD_REQUEST, ScimResponse.ERR_TYPE_SYNTAX);
    }

    @Test
    public void b_malformedPatchBodyIsInvalidSyntax() throws Exception {
        HttpPatch patch = new HttpPatch(TestUtils.mapPathToReqUrl(baseUrl, "/Users/doesNotMatter"));
        patch.setEntity(new StringEntity(MALFORMED_JSON, ContentType.APPLICATION_JSON));

        assertScimError(patch, ScimResponse.ST_BAD_REQUEST, ScimResponse.ERR_TYPE_SYNTAX);
    }

    @Test
    public void b_malformedBulkBodyIsInvalidSyntax() throws Exception {
        HttpPost post = new HttpPost(TestUtils.mapPathToReqUrl(baseUrl, ScimParams.PATH_BULK));
        post.setEntity(new StringEntity(MALFORMED_JSON, ContentType.APPLICATION_JSON));

        assertScimError(post, ScimResponse.ST_BAD_REQUEST, ScimResponse.ERR_TYPE_SYNTAX);
    }

    @Test
    public void c_badFilterIsInvalidFilter() throws Exception {
        HttpGet get = new HttpGet(TestUtils.mapPathToReqUrl(baseUrl, "/Users?filter=userName%20zz%20%22x%22"));

        assertScimError(get, ScimResponse.ST_BAD_REQUEST, ScimResponse.ERR_TYPE_FILTER);
    }

    @Test
    public void c_badSortOrderIsInvalidValue() throws Exception {
        HttpGet get = new HttpGet(TestUtils.mapPathToReqUrl(baseUrl, "/Users?sortBy=userName&sortOrder=up"));

        assertScimError(get, ScimResponse.ST_BAD_REQUEST, ScimResponse.ERR_TYPE_BADVAL);
    }

    protected static final String BULK_REQUEST_START = "{\"schemas\":[\"urn:ietf:params:scim:api:messages:2.0:BulkRequest\"],";

    protected static String bulkCreateUser(String bulkId, String userName) {
        return "{\"method\":\"POST\",\"path\":\"/Users\",\"bulkId\":\"" + bulkId + "\",\"data\":{"
                + "\"schemas\":[\"urn:ietf:params:scim:schemas:core:2.0:User\"],\"userName\":\"" + userName + "\"}}";
    }

    // A PATCH whose operation type is not a valid SCIM PATCH op.
    private static final String BULK_BAD_PATCH = "{\"method\":\"PATCH\",\"path\":\"/Users/doesNotMatter\",\"data\":{"
            + "\"schemas\":[\"urn:ietf:params:scim:api:messages:2.0:PatchOp\"],"
            + "\"Operations\":[{\"op\":\"frobnicate\",\"path\":\"userName\",\"value\":\"x\"}]}}";

    // A create whose data value is a JSON string rather than a resource object.
    protected static final String BULK_BAD_DATA = "{\"method\":\"POST\",\"path\":\"/Users\",\"bulkId\":\"badData\","
            + "\"data\":\"notAResource\"}";

    // A create carrying a binary attribute value that is not valid base64.
    private static final String BULK_BAD_BINARY = "{\"method\":\"POST\",\"path\":\"/Users\",\"bulkId\":\"badBinary\","
            + "\"data\":{\"schemas\":[\"urn:ietf:params:scim:schemas:core:2.0:User\"],\"userName\":\"bulkBinary\","
            + "\"x509Certificates\":[{\"value\":\"%%% not base64 %%%\"}]}}";

    // An operation without a method.
    private static final String BULK_NO_METHOD = "{\"path\":\"/Users\",\"bulkId\":\"noMethod\"}";

    @Test
    public void e_bulkIsolatesMalformedOperations() throws Exception {
        String body = BULK_REQUEST_START + "\"Operations\":["
                + bulkCreateUser("good1", "bulkGood1") + ","
                + BULK_BAD_PATCH + ","
                + BULK_BAD_DATA + ","
                + BULK_BAD_BINARY + ","
                + BULK_NO_METHOD + ","
                + bulkCreateUser("good2", "bulkGood2") + "]}";

        JsonNode ops = assertBulkResponse(body);
        assertThat(ops.size()).as("one result per operation").isEqualTo(6);

        assertBulkOpSuccess(ops.get(0), "POST", "good1", 201);
        assertBulkOpError(ops.get(1), "PATCH", null);
        assertBulkOpError(ops.get(2), "POST", "badData");
        assertBulkOpError(ops.get(3), "POST", "badBinary");
        assertBulkOpError(ops.get(4), null, "noMethod");
        assertBulkOpSuccess(ops.get(5), "POST", "good2", 201);
    }

    @Test
    public void e_bulkStopsAtFailOnErrors() throws Exception {
        String body = BULK_REQUEST_START + "\"failOnErrors\":1,\"Operations\":["
                + BULK_BAD_DATA + ","
                + bulkCreateUser("good3", "bulkGood3") + "]}";

        JsonNode ops = assertBulkResponse(body);
        assertThat(ops.size()).as("processing stops once failOnErrors is reached").isEqualTo(1);
        assertBulkOpError(ops.get(0), "POST", "badData");
    }

    // Issue #110: a binary attribute value that is not valid base64 is a client error (400 invalidValue).
    private static final String BAD_BASE64 = "%%% not base64 %%%";

    private static final String BAD_CERTS = "\"x509Certificates\":[{\"value\":\"" + BAD_BASE64 + "\"}]";

    private static String userJson(String userName, String extra) {
        return "{\"schemas\":[\"urn:ietf:params:scim:schemas:core:2.0:User\"],\"userName\":\"" + userName + "\""
                + (extra == null ? "" : "," + extra) + "}";
    }

    private static String patchJson(String operation) {
        return "{\"schemas\":[\"urn:ietf:params:scim:api:messages:2.0:PatchOp\"],\"Operations\":[" + operation + "]}";
    }

    /**
     * Creates a user over HTTP and returns its location path (e.g. /Users/123).
     */
    protected String createUser(String userName) throws Exception {
        HttpPost post = new HttpPost(TestUtils.mapPathToReqUrl(baseUrl, "/Users"));
        post.setEntity(new StringEntity(userJson(userName, null), ContentType.APPLICATION_JSON));
        authorize(post);
        ClassicHttpResponse resp = TestUtils.executeRequest(post);
        String body = resp.getEntity() == null ? "" : EntityUtils.toString(resp.getEntity());
        assertThat(resp.getCode()).as("create " + userName + ": " + body).isEqualTo(ScimResponse.ST_CREATED);
        String id = JsonUtil.getJsonTree(body).path(ScimParams.ATTR_ID).asText();
        return "/Users/" + id;
    }

    private void assertInvalidBase64(HttpUriRequestBase request) throws Exception {
        JsonNode err = assertScimError(request, ScimResponse.ST_BAD_REQUEST, ScimResponse.ERR_TYPE_BADVAL);
        assertThat(err.path("detail").asText()).as("detail names the base64 problem").containsIgnoringCase("base64");
    }

    @Test
    public void f_invalidBase64OnCreateIsInvalidValue() throws Exception {
        HttpPost post = new HttpPost(TestUtils.mapPathToReqUrl(baseUrl, "/Users"));
        post.setEntity(new StringEntity(userJson("b64Create", BAD_CERTS), ContentType.APPLICATION_JSON));

        assertInvalidBase64(post);
    }

    @Test
    public void f_invalidBase64OnPutIsInvalidValue() throws Exception {
        String path = createUser("b64Put");
        HttpPut put = new HttpPut(TestUtils.mapPathToReqUrl(baseUrl, path));
        put.setEntity(new StringEntity(userJson("b64Put", BAD_CERTS), ContentType.APPLICATION_JSON));

        assertInvalidBase64(put);
    }

    @Test
    public void f_invalidBase64OnPatchIsInvalidValue() throws Exception {
        String path = createUser("b64Patch");
        String[] operations = {
                "{\"op\":\"add\",\"path\":\"x509Certificates\",\"value\":[{\"value\":\"" + BAD_BASE64 + "\"}]}",
                "{\"op\":\"replace\",\"path\":\"x509Certificates\",\"value\":[{\"value\":\"" + BAD_BASE64 + "\"}]}",
                "{\"op\":\"add\",\"value\":{" + BAD_CERTS + "}}",
                "{\"op\":\"replace\",\"value\":{" + BAD_CERTS + "}}"
        };
        for (String operation : operations) {
            HttpPatch patch = new HttpPatch(TestUtils.mapPathToReqUrl(baseUrl, path));
            patch.setEntity(new StringEntity(patchJson(operation), ContentType.APPLICATION_JSON));

            assertInvalidBase64(patch);
        }
    }

    /**
     * Posts a bulk request and asserts a 200 BulkResponse.
     * @return the BulkResponse Operations array
     */
    protected JsonNode assertBulkResponse(String body) throws Exception {
        HttpPost post = new HttpPost(TestUtils.mapPathToReqUrl(baseUrl, ScimParams.PATH_BULK));
        post.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
        authorize(post);
        ClassicHttpResponse resp = TestUtils.executeRequest(post);
        String respBody = resp.getEntity() == null ? "" : EntityUtils.toString(resp.getEntity());
        logger.info("POST /Bulk -> " + resp.getCode() + "\n" + respBody);

        assertThat(resp.getCode()).as("Bulk HTTP status").isEqualTo(ScimResponse.ST_OK);
        JsonNode bulkResp = JsonUtil.getJsonTree(respBody);
        assertThat(bulkResp.path(ScimParams.ATTR_SCHEMAS).toString()).contains(ScimParams.SCHEMA_API_BulkResponse);
        JsonNode ops = bulkResp.path("Operations");
        assertThat(ops.isArray()).as("Operations array present").isTrue();
        return ops;
    }

    private static void assertBulkOpCommon(JsonNode op, String method, String bulkId) {
        if (method != null)
            assertThat(op.path("method").asText()).as("bulk op method").isEqualTo(method);
        if (bulkId != null)
            assertThat(op.path("bulkId").asText()).as("bulk op bulkId").isEqualTo(bulkId);
    }

    private static void assertBulkOpSuccess(JsonNode op, String method, String bulkId, int status) {
        assertBulkOpCommon(op, method, bulkId);
        assertThat(op.path("status").asText()).as("bulk op status for " + bulkId).isEqualTo(String.valueOf(status));
        assertThat(op.path("location").asText()).as("bulk op location for " + bulkId).isNotEmpty();
    }

    protected static void assertBulkOpError(JsonNode op, String method, String bulkId) {
        assertBulkOpError(op, method, bulkId, ScimResponse.ST_BAD_REQUEST);
    }

    protected static void assertBulkOpError(JsonNode op, String method, String bulkId, int status) {
        assertBulkOpCommon(op, method, bulkId);
        assertThat(op.path("status").asText()).as("bulk op status for " + op).isEqualTo(String.valueOf(status));
        assertScimErrorBody(op.path("response"), status, null);
    }

    /**
     * Executes the request and asserts that the response carries the expected HTTP status and a SCIM Error body with
     * the matching status and (when not null) scimType.
     * @return the parsed SCIM Error body
     */
    protected JsonNode assertScimError(HttpUriRequestBase request, int status, String scimType) throws Exception {
        authorize(request);
        ClassicHttpResponse resp = TestUtils.executeRequest(request);
        String body = resp.getEntity() == null ? "" : EntityUtils.toString(resp.getEntity());
        logger.info(request.getMethod() + " " + request.getRequestUri() + " -> " + resp.getCode() + "\n" + body);

        assertThat(resp.getCode())
                .as("HTTP status for " + request.getMethod() + " " + request.getRequestUri())
                .isEqualTo(status);
        return assertScimErrorBody(JsonUtil.getJsonTree(body), status, scimType);
    }

    protected static JsonNode assertScimErrorBody(JsonNode err, int status, String scimType) {
        assertThat(err.path(ScimParams.ATTR_SCHEMAS).toString())
                .as("SCIM Error schema present")
                .contains(ScimResponse.SCHEMA_ERROR);
        assertThat(err.path("status").asText())
                .as("SCIM Error status")
                .isEqualTo(String.valueOf(status));
        if (scimType != null)
            assertThat(err.path("scimType").asText())
                    .as("SCIM Error scimType")
                    .isEqualTo(scimType);
        assertThat(err.path("detail").asText())
                .as("SCIM Error detail")
                .isNotEmpty();
        return err;
    }
}
