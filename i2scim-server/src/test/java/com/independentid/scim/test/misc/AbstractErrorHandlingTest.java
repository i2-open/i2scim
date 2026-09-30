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

    /**
     * Executes the request and asserts that the response carries the expected HTTP status and a SCIM Error body with
     * the matching status and (when not null) scimType.
     * @return the parsed SCIM Error body
     */
    protected JsonNode assertScimError(HttpUriRequestBase request, int status, String scimType) throws Exception {
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
