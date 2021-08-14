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


import com.independentid.scim.backend.BackendException;
import com.independentid.scim.backend.BackendHandler;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.ScimParams;
import com.independentid.scim.protocol.ScimResponse;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.test.misc.TestUtils;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;


@QuarkusTest
@TestProfile(ScimHttpTestProfile.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class ScimAttributeQualTest {

    private final static Logger logger = LoggerFactory.getLogger(ScimAttributeQualTest.class);

    //private static String userSchemaId = "urn:ietf:params:scim:schemas:core:2.0:User";

    @Inject
    @Resource(name = "SchemaMgr")
    SchemaManager smgr;

    @Inject
    BackendHandler handler;

    @Inject
    TestUtils testUtils;

    @TestHTTPResource("/")
    URL baseUrl;

    private static String user1url = "";

    private final static String testUserFile1 = "classpath:/schema/TestUser-bjensen.json";

    private static boolean isInit = false;

    /**
     * Do test setup by re-initializes the SCIM Mongo test database.
     */
    @Test
    public void a_initializeTestProvider() {
        if (isInit)
            return;
        logger.info("========== Attribute Qualifier Tests ==========");
        logger.info("\tA. Resetting and loading test data");

        try {
            testUtils.resetProvider(true);
        } catch (ScimException | BackendException | IOException e) {
            Assertions.fail("Failed to reset provider: " + e.getMessage());
        }

        loadTestUser();

        if (logger.isDebugEnabled()) {
            Attribute mname = smgr.findAttribute("User:name.middleName", null);
            if (mname != null)
                logger.debug("\t\tUser:name.middleName returnability is: " + mname.getReturned());
        }
        isInit = true;

    }

    /**
     * This test checks that a JSON user can be parsed into a SCIM Resource
     */

    private void loadTestUser() {

        logger.info("\t * Add User BJensen...");
        CloseableHttpClient client = HttpClients.createDefault();

        try {

            InputStream userStream = ConfigMgr.findClassLoaderResource(testUserFile1);

            String req = TestUtils.mapPathToReqUrl(baseUrl, "/Users");

            HttpPost post = new HttpPost(req);

            InputStreamEntity reqEntity = new InputStreamEntity(
                    userStream, -1, ContentType.create(ScimParams.SCIM_MIME_TYPE));
            reqEntity.setChunked(false);
            post.setEntity(reqEntity);

            CloseableHttpResponse resp = client.execute(post);

            assertThat(resp.getStatusLine().getStatusCode())
                    .as("Create user response status of 201")
                    .isEqualTo(ScimResponse.ST_CREATED);

            Header[] hloc = resp.getHeaders(HttpHeaders.LOCATION);

            user1url = hloc[0].getValue();
        } catch (IOException e) {
            Assertions.fail("Exception occured creating bjenson. " + e.getMessage(), e);
        } finally {
            try {
                client.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    /**
     * This test attempts to retrieve the previously created user using the returned location.
     */
    @Test
    public void b_ScimGetUserTest() throws MalformedURLException {

        assertThat(isInit)
                .as("Check test databse initialized")
                .isTrue();

        String req = TestUtils.mapPathToReqUrl(baseUrl, user1url);

        logger.info("\tB. Retrieve user from backend using: " + req);

        CloseableHttpClient client = HttpClients.createDefault();

        HttpUriRequest request = new HttpGet(req);

        try {
            CloseableHttpResponse resp = client.execute(request);
            HttpEntity entity = resp.getEntity();

            assertThat(resp.getStatusLine().getStatusCode())
                    .as("GET User - Check for status response 200 OK")
                    .isEqualTo(ScimResponse.ST_OK);

            String body = EntityUtils.toString(entity);

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

            Attribute middleName = smgr.findAttribute("User:name.middleName", null);
            assertThat(middleName).isNotNull();
            assertThat(middleName.getReturned())
                    .as("Test schema should have returnable on request")
                    .isEqualTo(Attribute.RETURNED_request);

            // Now check for "middleName" which is not returned by default.
            assertThat(body)
                    .as("name.middleName should not be present test")
                    .doesNotContain("middleName");

            logger.debug("Entry returned with no middleName\n" + body);

            resp.close();

        } catch (IOException e) {
            fail("Exception occured making GET request for bjensen", e);
        }
    }

    @Test
    public void c_ScimGetUserInclTest() throws MalformedURLException {

        assertThat(isInit)
                .as("Check test databse initialized")
                .isTrue();

        String req = TestUtils.mapPathToReqUrl(baseUrl,
                user1url + "?attributes=userName,name.middleName");

        logger.info("\tC. Retrieve user from backend with specific attrs using: " + req);

        CloseableHttpClient client = HttpClients.createDefault();

        HttpUriRequest request = new HttpGet(req);

        try {
            CloseableHttpResponse resp = client.execute(request);
            HttpEntity entity = resp.getEntity();

            assertThat(resp.getStatusLine().getStatusCode())
                    .as("GET User - Check for status response 200 OK")
                    .isEqualTo(ScimResponse.ST_OK);

            String body = EntityUtils.toString(entity);

            assertThat(body)
                    .as("Check that it is not a ListResponse")
                    .doesNotContain(ScimParams.SCHEMA_API_ListResponse);

            assertThat(body)
                    .as("Is user bjensen")
                    .contains("bjensen@example.com");

            assertThat(body)
                    .as("Contains name.middleName")
                    .contains("\"middleName\"");

            assertThat(body)
                    .as("Does not contain familyName")
                    .doesNotContain("familyName");

            logger.debug("Entry returned with only userName and middleName\n" + body);
            // Check that the extension attributes were parsed and returned


            resp.close();

        } catch (IOException e) {
            fail("Exception occured making GET request for bjensen", e);
        }
    }

    /**
     * This test tries to search for the previously created user by searching on filter name
     */
    @Test
    public void d_ScimSearchUserExcludeTest() throws MalformedURLException {
        assertThat(isInit)
                .as("Check test databse initialized")
                .isTrue();

        logger.info("\tD. Searching user from backend with filter=UserName eq bjensen@example.com and exclude attrs");
        CloseableHttpClient client = HttpClients.createDefault();

        String req = TestUtils.mapPathToReqUrl(baseUrl,
                "/Users?filter=" + URLEncoder.encode("UserName eq bjensen@example.com", StandardCharsets.UTF_8) + "&excludedAttributes=meta,name");

        HttpUriRequest request = new HttpGet(req);

        try {
            CloseableHttpResponse resp = client.execute(request);
            HttpEntity entity = resp.getEntity();

            assertThat(resp.getStatusLine().getStatusCode())
                    .as("GET User - Check for status response 200 OK")
                    .isEqualTo(ScimResponse.ST_OK);

            String body = EntityUtils.toString(entity);

            assertThat(body)
                    .as("Check query response is a ListResponse")
                    .contains(ScimParams.SCHEMA_API_ListResponse);

            assertThat(body)
                    .as("Is user bjensen")
                    .contains("bjensen@example.com");
            logger.debug("Entry retrieved:\n" + body);

            // Check that the extension attributes were parsed and returned
            assertThat(body)
                    .as("Contains the correct extension")
                    .contains("urn:ietf:params:scim:schemas:extension:enterprise:2.0:User");
            assertThat(body)
                    .as("Contains an extension value Tour Operations")
                    .contains("Tour Operations");

            assertThat(body)
                    .as("Does not have a meta attribue")
                    .doesNotContain("\"meta\"");
            assertThat(body)
                    .as("Does not have a name attribue")
                    .doesNotContain("\"name\"");


            resp.close();

        } catch (IOException e) {
            fail("Exception occured making GET filter request for bjensen", e);
        }
    }

    @Test
    public void e_ScimGetUserInclExtTest() throws MalformedURLException {

        assertThat(isInit)
                .as("Check test databse initialized")
                .isTrue();

        String req = TestUtils.mapPathToReqUrl(baseUrl,
                user1url + "?attributes=userName,name.middleName,organization,manager.displayName");

        logger.info("\tE. Include/Exclude Extension Attrs Test: " + req);

        CloseableHttpClient client = HttpClients.createDefault();

        HttpUriRequest request = new HttpGet(req);

        try {
            CloseableHttpResponse resp = client.execute(request);
            HttpEntity entity = resp.getEntity();

            assertThat(resp.getStatusLine().getStatusCode())
                    .as("GET User - Check for status response 200 OK")
                    .isEqualTo(ScimResponse.ST_OK);

            String body = EntityUtils.toString(entity);
            logger.debug("Entry returned with 4 attributes including extensions:\n" + body);

            assertThat(body)
                    .as("Check that it is not a ListResponse")
                    .doesNotContain(ScimParams.SCHEMA_API_ListResponse);

            assertThat(body)
                    .as("Is user bjensen")
                    .contains("bjensen@example.com");

            assertThat(body)
                    .as("Contains name.middleName")
                    .contains("\"middleName\"");

            assertThat(body)
                    .as("Does not contain familyName")
                    .doesNotContain("familyName");

            assertThat(body)
                    .as("Contains enterprise organization")
                    .contains("\"organization\"");

            assertThat(body)
                    .as("Contains manager.doisplayName")
                    .contains("John Smith");

            assertThat(body)
                    .as("Does not contain manager.$ref")
                    .doesNotContain("/Users/26118915-6090-4610-87e4-49d8ca9f808d");

            assertThat(body)
                    .as("Does not contain manager.$ref")
                    .doesNotContain("\"$ref\"");

            resp.close();

        } catch (IOException e) {
            fail("Exception occured making GET request for bjensen", e);
        }
    }


}
