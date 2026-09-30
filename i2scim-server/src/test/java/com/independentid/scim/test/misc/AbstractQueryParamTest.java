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
import com.independentid.scim.protocol.ListResponse;
import com.independentid.scim.protocol.ScimParams;
import com.independentid.scim.protocol.ScimResponse;
import com.independentid.scim.serializer.JsonUtil;
import io.quarkus.test.common.http.TestHTTPResource;
import jakarta.inject.Inject;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shared regression tests for issue #108: invalid {@code startIndex}, {@code count} and filter values must return a
 * 400 SCIM error or be normalised per RFC 7644 §3.4.2.4 — never a 500. Concrete subclasses supply only the
 * {@code @QuarkusTest}/{@code @TestProfile} wiring for the backend under test.
 */
@TestMethodOrder(MethodOrderer.MethodName.class)
public abstract class AbstractQueryParamTest {

    static final Logger logger = LoggerFactory.getLogger(AbstractQueryParamTest.class);

    @Inject
    protected TestUtils testUtils;

    @TestHTTPResource("/")
    protected URL baseUrl;

    private static final int USER_COUNT = 3;

    private static final String SEARCH_PATH = "/Users/.search";

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
    public void b_loadUsers() throws Exception {
        for (int i = 1; i <= USER_COUNT; i++) {
            HttpPost post = new HttpPost(TestUtils.mapPathToReqUrl(baseUrl, "/Users"));
            post.setEntity(new StringEntity("{\"schemas\":[\"urn:ietf:params:scim:schemas:core:2.0:User\"],"
                    + "\"userName\":\"pageUser" + i + "\",\"loginCnt\":" + i + ",\"loginStrength\":" + i + ".5}",
                    ContentType.APPLICATION_JSON));
            authorize(post);
            ClassicHttpResponse resp = TestUtils.executeRequest(post);
            EntityUtils.consume(resp.getEntity());
            assertThat(resp.getCode()).as("create pageUser" + i).isEqualTo(ScimResponse.ST_CREATED);
        }
    }

    /* ---------- startIndex / count: non-integer values ---------- */

    @Test
    public void f_nonNumericStartIndexIsInvalidValue() throws Exception {
        assertScimError(get("/Users?startIndex=abc"), ScimResponse.ST_BAD_REQUEST, ScimResponse.ERR_TYPE_BADVAL);
    }

    @Test
    public void f_nonNumericCountIsInvalidValue() throws Exception {
        assertScimError(get("/Users?count=abc"), ScimResponse.ST_BAD_REQUEST, ScimResponse.ERR_TYPE_BADVAL);
    }

    @Test
    public void f_searchBodyNonNumericStartIndexIsInvalidValue() throws Exception {
        assertScimError(search("\"startIndex\":\"abc\""), ScimResponse.ST_BAD_REQUEST, ScimResponse.ERR_TYPE_BADVAL);
    }

    @Test
    public void f_searchBodyNonNumericCountIsInvalidValue() throws Exception {
        assertScimError(search("\"count\":\"abc\""), ScimResponse.ST_BAD_REQUEST, ScimResponse.ERR_TYPE_BADVAL);
    }

    @Test
    public void f_searchBodyFractionalCountIsInvalidValue() throws Exception {
        assertScimError(search("\"count\":1.5"), ScimResponse.ST_BAD_REQUEST, ScimResponse.ERR_TYPE_BADVAL);
    }

    /* ---------- startIndex / count: out of range values (RFC 7644 §3.4.2.4) ---------- */

    @Test
    public void g_startIndexZeroIsTreatedAsOne() throws Exception {
        assertSamePageAsStartIndexOne("/Users?sortBy=userName&count=2&startIndex=0");
    }

    @Test
    public void g_negativeStartIndexIsTreatedAsOne() throws Exception {
        assertSamePageAsStartIndexOne("/Users?sortBy=userName&count=2&startIndex=-5");
    }

    @Test
    public void g_searchBodyStartIndexZeroIsTreatedAsOne() throws Exception {
        JsonNode list = assertListResponse(search("\"sortBy\":\"userName\",\"count\":2,\"startIndex\":0"));
        JsonNode expected = assertListResponse(get("/Users?sortBy=userName&count=2&startIndex=1"));
        assertThat(list.path(ListResponse.ATTR_STARTINDEX).asInt()).isEqualTo(1);
        assertThat(userNames(list)).isEqualTo(userNames(expected));
    }

    @Test
    public void g_negativeCountReturnsNoResources() throws Exception {
        assertCountOnly(get("/Users?count=-1"));
    }

    @Test
    public void g_zeroCountReturnsNoResources() throws Exception {
        assertCountOnly(get("/Users?count=0"));
    }

    @Test
    public void g_searchBodyNegativeCountReturnsNoResources() throws Exception {
        assertCountOnly(search("\"count\":-1"));
    }

    @Test
    public void g_omittedCountReturnsAllResources() throws Exception {
        JsonNode list = assertListResponse(get("/Users"));
        assertThat(list.path(ListResponse.ATTR_TOTRES).asInt()).isGreaterThanOrEqualTo(USER_COUNT);
        assertThat(list.path(ListResponse.ATTR_RESOURCES).size())
                .isEqualTo(list.path(ListResponse.ATTR_TOTRES).asInt());
    }

    /* ---------- filters ---------- */

    @Test
    public void h_bareAttributeFilterIsInvalidFilter() throws Exception {
        assertScimError(get("/Users?filter=userName"), ScimResponse.ST_BAD_REQUEST, ScimResponse.ERR_TYPE_FILTER);
    }

    @Test
    public void h_bareAttributeFilterInSearchBodyIsInvalidFilter() throws Exception {
        assertScimError(search("\"filter\":\"userName\""), ScimResponse.ST_BAD_REQUEST, ScimResponse.ERR_TYPE_FILTER);
    }

    @Test
    public void h_badDateFilterValueIsInvalidFilter() throws Exception {
        assertBadFilter("meta.lastModified gt \"notadate\"");
    }

    @Test
    public void h_badDateFilterValueInLogicExpressionIsInvalidFilter() throws Exception {
        assertBadFilter("meta.lastModified gt \"notadate\" and userName pr");
    }

    @Test
    public void h_badIntegerFilterValueIsInvalidFilter() throws Exception {
        assertBadFilter("loginCnt gt abc");
    }

    @Test
    public void h_badDecimalFilterValueIsInvalidFilter() throws Exception {
        assertBadFilter("loginStrength gt abc");
    }

    @Test
    public void h_badBinaryFilterValueIsInvalidFilter() throws Exception {
        assertBadFilter("x509Certificates.value eq \"%%% not base64 %%%\"");
    }

    @Test
    public void h_validTypedFilterValuesStillMatch() throws Exception {
        assertMatches("meta.lastModified gt \"2000-01-01T00:00:00Z\" and userName sw \"pageUser\"", USER_COUNT);
        assertMatches("loginCnt gt 1 and userName sw \"pageUser\"", USER_COUNT - 1);
        assertMatches("loginStrength lt 2.0 and userName sw \"pageUser\"", 1);
    }

    /* ---------- helpers ---------- */

    private HttpGet get(String pathAndQuery) throws Exception {
        return new HttpGet(TestUtils.mapPathToReqUrl(baseUrl, pathAndQuery));
    }

    private HttpPost search(String members) throws Exception {
        HttpPost post = new HttpPost(TestUtils.mapPathToReqUrl(baseUrl, SEARCH_PATH));
        post.setEntity(new StringEntity("{\"schemas\":[\"" + ScimParams.SCHEMA_API_SearchRequest + "\"],"
                + members + "}", ContentType.APPLICATION_JSON));
        return post;
    }

    private static String filterQuery(String filter) {
        return "/Users?filter=" + URLEncoder.encode(filter, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private void assertBadFilter(String filter) throws Exception {
        assertScimError(get(filterQuery(filter)), ScimResponse.ST_BAD_REQUEST, ScimResponse.ERR_TYPE_FILTER);
        assertScimError(search("\"filter\":" + JsonUtil.getMapper().writeValueAsString(filter)),
                ScimResponse.ST_BAD_REQUEST, ScimResponse.ERR_TYPE_FILTER);
    }

    private void assertMatches(String filter, int expected) throws Exception {
        JsonNode list = assertListResponse(get(filterQuery(filter)));
        assertThat(list.path(ListResponse.ATTR_TOTRES).asInt()).as("matches for " + filter).isEqualTo(expected);
    }

    private void assertSamePageAsStartIndexOne(String pathAndQuery) throws Exception {
        JsonNode list = assertListResponse(get(pathAndQuery));
        JsonNode expected = assertListResponse(get("/Users?sortBy=userName&count=2&startIndex=1"));
        assertThat(list.path(ListResponse.ATTR_STARTINDEX).asInt()).as("startIndex reported").isEqualTo(1);
        assertThat(list.path(ListResponse.ATTR_RESOURCES).size()).as("page size").isEqualTo(2);
        assertThat(userNames(list)).isEqualTo(userNames(expected));
    }

    private void assertCountOnly(HttpUriRequestBase request)
            throws Exception {
        JsonNode list = assertListResponse(request);
        assertThat(list.path(ListResponse.ATTR_TOTRES).asInt()).as("totalResults").isGreaterThanOrEqualTo(USER_COUNT);
        assertThat(list.path(ListResponse.ATTR_RESOURCES).size()).as("no Resources returned").isZero();
        assertThat(list.path(ListResponse.ATTR_ITEMPERPAGE).asInt()).as("itemsPerPage").isZero();
    }

    private void assertScimError(HttpUriRequestBase request, int status, String scimType) throws Exception {
        authorize(request);
        ClassicHttpResponse resp = TestUtils.executeRequest(request);
        String body = resp.getEntity() == null ? "" : EntityUtils.toString(resp.getEntity());
        logger.info(request.getMethod() + " " + request.getRequestUri() + " -> " + resp.getCode() + "\n" + body);
        assertThat(resp.getCode())
                .as("HTTP status for " + request.getMethod() + " " + request.getRequestUri())
                .isEqualTo(status);
        AbstractErrorHandlingTest.assertScimErrorBody(JsonUtil.getJsonTree(body), status, scimType);
    }

    private static String userNames(JsonNode list) {
        StringBuilder buf = new StringBuilder();
        for (JsonNode res : list.path(ListResponse.ATTR_RESOURCES))
            buf.append(res.path("userName").asText()).append(',');
        return buf.toString();
    }

    private JsonNode assertListResponse(HttpUriRequestBase request)
            throws Exception {
        authorize(request);
        ClassicHttpResponse resp = TestUtils.executeRequest(request);
        String body = resp.getEntity() == null ? "" : EntityUtils.toString(resp.getEntity());
        logger.info(request.getMethod() + " " + request.getRequestUri() + " -> " + resp.getCode() + "\n" + body);
        assertThat(resp.getCode()).as("HTTP status for " + request.getRequestUri()).isEqualTo(ScimResponse.ST_OK);
        JsonNode list = JsonUtil.getJsonTree(body);
        assertThat(list.path(ScimParams.ATTR_SCHEMAS).toString()).contains(ScimResponse.SCHEMA_LISTRESP);
        return list;
    }
}
