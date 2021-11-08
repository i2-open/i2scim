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

package com.independentid.scim.test.devops;


import com.independentid.scim.backend.BackendException;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.ScimParams;
import com.independentid.scim.protocol.ScimResponse;
import com.independentid.scim.test.misc.TestUtils;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;


@QuarkusTest
@TestProfile(ScimDevOpsTestProfile.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class ScimHealthTest {

    private final static Logger logger = LoggerFactory.getLogger(ScimHealthTest.class);

    public static String bearer;

    @Inject
    TestUtils testUtils;

    @TestHTTPResource("/")
    URL baseUrl;

    //private static String user1url = "";

    private static final String testUserFile1 = "classpath:/schema/TestUser-bjensen.json";
    private static final String testUserFile2 = "classpath:/schema/TestUser-jsmith.json";


    private static String bJensonUrl = null;
    private static String jSmithUrl = null;

    /**
     * This test actually resets and re-initializes the SCIM Mongo test database.
     */
    @Test
    public void a_initializeProvider() {
        bearer = testUtils.getAuthToken("admin",true);

        logger.info("========== Scim Mongo CRUD Test ==========");
        logger.info("\tA. Initializing test dataset");

        try {
            testUtils.resetProvider(true);
        } catch (ScimException | BackendException | IOException e) {
            Assertions.fail("Failed to reset provider: " + e.getMessage());
        }

    }

    @Test
    public void b1_initialHealthCheckJwt() throws IOException {
        URL rUrl = new URL(baseUrl, "/q/health");
        HttpGet get = new HttpGet(rUrl.toString());
        get.addHeader(HttpHeaders.AUTHORIZATION, bearer);

        HttpResponse resp = TestUtils.executeRequest(get);

        assertThat(resp.getStatusLine().getStatusCode())
                .as("Check health response received ok")
                .isEqualTo(ScimResponse.ST_OK);

        HttpEntity entity = resp.getEntity();

        String body = EntityUtils.toString(entity);
        logger.debug("Health: \n" + body);
        assertThat(body)
                .as("Check that server is up")
                .contains("\"status\": \"UP\"");
        assertThat(body)
                .as("Provider is ready")
                .contains("\"scim.provider.ready\": true");
    }

    @Test
    public void b2_initialHealthCheckAnon() throws IOException {
        URL rUrl = new URL(baseUrl, "/q/health");
        HttpGet get = new HttpGet(rUrl.toString());
        //get.addHeader(HttpHeaders.AUTHORIZATION, bearer);

        HttpResponse resp = TestUtils.executeRequest(get);

        assertThat(resp.getStatusLine().getStatusCode())
                .as("Check health response received ok")
                .isEqualTo(ScimResponse.ST_OK);

        rUrl = new URL(baseUrl, "/q/health");
        get = new HttpGet(rUrl.toString());
        //get.addHeader(HttpHeaders.AUTHORIZATION, bearer);

        resp = TestUtils.executeRequest(get);

        assertThat(resp.getStatusLine().getStatusCode())
                .as("Check health response received ok")
                .isEqualTo(ScimResponse.ST_OK);

    }

    @Test
    public void c1_addUserTest_JWTadmin() throws IOException {

        // Perform add with JWT bearer authorization
        logger.info("B2. Attempting add bjensen as with JWT Bearer with role admin (SHOULD SUCCEED)");

        InputStream userStream;

        URL rUrl = new URL(baseUrl, "/Users");
        String req = rUrl.toString();

        HttpPost post = new HttpPost(req);
        userStream = ConfigMgr.findClassLoaderResource(testUserFile1);
        assert userStream != null;
        InputStreamEntity reqEntity = new InputStreamEntity(
                userStream, -1, ContentType.create(ScimParams.SCIM_MIME_TYPE));
        reqEntity.setChunked(false);
        post.setEntity(reqEntity);
        post.addHeader(HttpHeaders.AUTHORIZATION, bearer);

        HttpResponse resp = TestUtils.executeRequest(post);

        logger.debug("Response is: " + resp.getStatusLine());
        String body = EntityUtils.toString(resp.getEntity());
        logger.debug("Body:\n" + body);

        Header[] heads = resp.getAllHeaders();
        for (Header head : heads) {
            logger.debug(head.getName() + "\t" + head.getValue());
        }

        Header[] hloc = resp.getHeaders(HttpHeaders.LOCATION);
        if (hloc == null || hloc.length == 0)
            fail("No HTTP Location header in create response");
        else {
            Header loc = hloc[0];
            assertThat(loc).isNotNull();
            assertThat(loc.getValue())
                    .as("Created object URL created in users endpoint")
                    .contains("/Users/");
            bJensonUrl = loc.getValue();  // This will be used to retrieve the user later
        }


        assertThat(resp.getStatusLine().getStatusCode())
                .as("Create user response status of 201")
                .isEqualTo(ScimResponse.ST_CREATED);

        assertThat(body)
                .as("Check that it is not a ListResponse")
                .doesNotContain(ScimParams.SCHEMA_API_ListResponse);

        assertThat(body)
                .as("Is user bjensen")
                .contains("bjensen@example.com");

        // Check that the extension attributes were parsed and returned
        assertThat(body)
                .as("Contains the correct extension")
                .contains("urn:ietf:params:scim:schemas:extension:enterprise:2.0:User");
        assertThat(body)
                .as("Contains an extension value Tour Operations")
                .contains("Tour Operations");
    }

    @Test
    public void c2_addJSmith_JWTadmin() throws IOException {
        /*  Add User JSmith */
        logger.info("B4. Add another User JSmith");

        InputStream userStream;

        URL rUrl = new URL(baseUrl, "/Users");
        String req = rUrl.toString();

        userStream = ConfigMgr.findClassLoaderResource(testUserFile2);
        HttpPost post = new HttpPost(req);
        post.addHeader(HttpHeaders.AUTHORIZATION, bearer);
        assert userStream != null;
        InputStreamEntity reqEntity = new InputStreamEntity(
                userStream, -1, ContentType.create(ScimParams.SCIM_MIME_TYPE));
        reqEntity.setChunked(false);
        post.setEntity(reqEntity);
        HttpResponse resp = TestUtils.executeRequest(post);
        assertThat(resp.getStatusLine().getStatusCode())
                .as("Check JSmith added")
                .isEqualTo(ScimResponse.ST_CREATED);
        Header[] headers = resp.getHeaders(HttpHeaders.LOCATION);
        if (headers.length > 0)
            jSmithUrl = headers[0].getValue();
        else
            fail("Missing location in creation response for JSmith");
    }

    @Test
    public void d_metricsCheckJwt() throws IOException {
        URL rUrl = new URL(baseUrl, "/metrics/base");
        HttpGet get = new HttpGet(rUrl.toString());
        get.addHeader(HttpHeaders.AUTHORIZATION, bearer);
        get.addHeader(HttpHeaders.ACCEPT, "application/json");
        HttpResponse resp = TestUtils.executeRequest(get);
        HttpEntity entity = resp.getEntity();

        String body = EntityUtils.toString(entity);
        logger.info("/metrics/base\n" + body);

        assertThat(resp.getStatusLine().getStatusCode())
                .as("Check health response received ok")
                .isEqualTo(ScimResponse.ST_OK);

        rUrl = new URL(baseUrl, "/metrics/application");
        get = new HttpGet(rUrl.toString());
        get.addHeader(HttpHeaders.AUTHORIZATION, bearer);
        get.addHeader(HttpHeaders.ACCEPT, "application/json");
        resp = TestUtils.executeRequest(get);
        body = EntityUtils.toString(resp.getEntity());
        logger.info("/metrics/application\n" + body);
        assertThat(body)
                .as("Confirm 2 create operations")
                .contains("\"com.independentid.scim.server.ScimV2Servlet.scim.ops.create.count\": 2,");

        rUrl = new URL(baseUrl, "/metrics");
        get = new HttpGet(rUrl.toString());
        get.addHeader(HttpHeaders.AUTHORIZATION, bearer);
        get.addHeader(HttpHeaders.ACCEPT, "application/json");
        resp = TestUtils.executeRequest(get);
        body = EntityUtils.toString(resp.getEntity());
        logger.info("/metrics\n" + body);

    }


}
