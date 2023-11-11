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

package com.independentid.scim.test.auth;


import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.backend.BackendException;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.ListResponse;
import com.independentid.scim.protocol.ScimParams;
import com.independentid.scim.protocol.ScimResponse;
import com.independentid.scim.resource.ExtensionValues;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.resource.StringValue;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.serializer.JsonUtil;
import com.independentid.scim.test.misc.TestUtils;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.annotation.Resource;
import jakarta.inject.Inject;
import org.apache.http.*;
import org.apache.http.client.methods.*;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;


@QuarkusTest
@TestProfile(ScimAuthTestProfile.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class ScimAuthZCRUDTest {

    private final static Logger logger = LoggerFactory.getLogger(ScimAuthZCRUDTest.class);

    public static String bearer;

    @Inject
    @Resource(name = "SchemaMgr")
    SchemaManager smgr;

    @Inject
    ConfigMgr cmgr;

    @Inject
    TestUtils testUtils;

    @TestHTTPResource("/")
    URL baseUrl;

    @ConfigProperty(name = "scim.security.root.username", defaultValue = "admin")
    String rootUser;

    @ConfigProperty(name = "scim.security.root.password", defaultValue = "admin")
    String rootPassword;

    //private static String user1url = "";

    private static final String testUserFile1 = "classpath:/schema/TestUser-bjensen.json";
    private static final String testUserFile2 = "classpath:/schema/TestUser-jsmith.json";
    private static final String testPass = "t1meMa$heen";

    private static String bJensonUrl = null;
    private static String jSmithUrl = null;

    private synchronized HttpResponse execute(HttpUriRequest req) throws IOException {

        return TestUtils.executeRequest(req);
    }

    /**
     * This test actually resets and re-initializes the SCIM Mongo test database.
     */
    @Test
    public void a_initializeAuthTests() {
        bearer = testUtils.getAuthToken("admin",true);

        //PasswordToken.init(parser,"AyM1SysPpbyDfgZld3umj1qzKObwVMko","TESTER",10000,PasswordToken.ALG_PBKDF2);
        logger.info("========== Scim Authorize CRUD Test ==========");
        logger.info("\tA. Initializing test database.");


        // Reset the Mongo database
        try {
            testUtils.resetProvider(true);
        } catch (ScimException | BackendException | IOException e) {
            fail("Unable to restart test database: " + e.getMessage());
        }


    }

    /**
     * This test checks that a JSON user can be parsed into a SCIM Resource
     */
    @Test
    public void b1_AddUserByAnonymous() {

        try {
            logger.info("B1. Attempting add BJensen as anonymous (SHOULD FAIL)");

            InputStream userStream = ConfigMgr.findClassLoaderResource(testUserFile1);

            URL rUrl = new URL(baseUrl, "/Users");
            String req = rUrl.toString();

            HttpPost post = new HttpPost(req);

            // This section should fail, anonymous request

            assert userStream != null;
            InputStreamEntity reqEntity = new InputStreamEntity(
                    userStream, -1, ContentType.create(ScimParams.SCIM_MIME_TYPE));
            reqEntity.setChunked(false);
            post.setEntity(reqEntity);

            logger.debug("Executing test add for bjensen: " + post.getRequestLine());
            //logger.debug(EntityUtils.toString(reqEntity));

            HttpResponse resp = execute(post);
            int statcode = resp.getStatusLine().getStatusCode();
            assertThat(statcode)
                    .as("Anonymous request should be unauthorized")
                    .isEqualTo(HttpStatus.SC_UNAUTHORIZED);
            userStream.close();


        } catch (IOException e) {
            logger.error("Unexpected error: " + e.getLocalizedMessage(), e);
            Assertions.fail("Exception occured creating bjenson. " + e.getMessage(), e);
        }
    }

    @Test
    public void b2_addUserTest_JWTadmin() throws IOException {

        // Perform add with JWT bearer authorization
        logger.info("B2. Attempting add bjensen as with JWT Bearer with role admin (SHOULD SUCCEED)");

        InputStream userStream;

        URL rUrl = new URL(baseUrl, "/Users");
        String req = rUrl.toString();
        logger.info("\tRequest URI for add: " + req);

        HttpPost post = new HttpPost(req);
        userStream = ConfigMgr.findClassLoaderResource(testUserFile1);
        assert userStream != null;
        InputStreamEntity reqEntity = new InputStreamEntity(
                userStream, -1, ContentType.create(ScimParams.SCIM_MIME_TYPE));
        reqEntity.setChunked(false);
        post.setEntity(reqEntity);
        post.addHeader(HttpHeaders.AUTHORIZATION, bearer);

        Header[] heads = post.getAllHeaders();

        System.err.println("Request:\n" + post);
        System.err.println("Headers:\n" + Arrays.toString(heads));
        //System.out.println("Body:\n"+EntityUtils.toString(post.getEntity()));

        HttpResponse resp = execute(post);

        logger.info("\tResponse is: " + resp.getStatusLine());
        String body = EntityUtils.toString(resp.getEntity());
        logger.debug("Body:\n" + body);

        heads = resp.getAllHeaders();
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
    public void b3_addDuplicateUserTest_JWTadmin() throws IOException {

        logger.info("B3. Attempt to add User BJensen again (uniquenes test)...");

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
        /* Repeat add test
         */

        // Attempt to repeat the operation. It should fail due to non-unique username match
        post = new HttpPost(req);
        post.addHeader(HttpHeaders.AUTHORIZATION, bearer);
        userStream = ConfigMgr.findClassLoaderResource(testUserFile1);
        assert userStream != null;
        reqEntity = new InputStreamEntity(
                userStream, -1, ContentType.create(ScimParams.SCIM_MIME_TYPE));
        reqEntity.setChunked(false);
        post.setEntity(reqEntity);

        HttpResponse resp = execute(post);

        assertThat(resp.getStatusLine().getStatusCode())
                .as("Confirm error 400 occurred (uniqueness)")
                .isEqualTo(ScimResponse.ST_BAD_REQUEST);
        String body = EntityUtils.toString(resp.getEntity());
        assertThat(body)
                .as("Is a uniqueness error")
                .contains(ScimResponse.ERR_TYPE_UNIQUENESS);

        userStream.close();

    }

    @Test
    public void b4_addJSmith_JWTadmin() throws IOException {
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
        HttpResponse resp = execute(post);
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
    public void c1_ScimGetUserAnonymous() throws MalformedURLException {
        String req = TestUtils.mapPathToReqUrl(baseUrl, bJensonUrl);

        logger.info("\tC1. Retrieving user (anonymous) from backend using: " + req);

        HttpUriRequest request = new HttpGet(req);
        //request.addHeader(HttpHeaders.AUTHORIZATION, bearer);

        try {
            HttpResponse resp = execute(request);

            assertThat(resp.getStatusLine().getStatusCode())
                    .as("GET User - Check for status unauthorized.")
                    .isEqualTo(ScimResponse.ST_UNAUTHORIZED);


        } catch (IOException e) {
            fail("Exception occured making GET request for bjensen", e);
        }
    }

    /**
     * This test attempts to retrieve the previously created user using the returned location.
     */
    @Test
    public void c2_ScimGetUser_JWTadmin() throws MalformedURLException {
        String req = TestUtils.mapPathToReqUrl(baseUrl, bJensonUrl);

        logger.info("C2. Retrieving user from backend (Bearer JWTadmin) using: " + req);

        HttpUriRequest request = new HttpGet(req);
        request.addHeader(HttpHeaders.AUTHORIZATION, bearer);

        try {
            HttpResponse resp = execute(request);
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

            // Check that the extension attributes were blocked
            assertThat(body)
                    .as("Contains an extension value Tour Operations")
                    .contains("Tour Operations");


            logger.debug("Entry retrieved:\n" + body);

            // Check that the result can be parsed as a SCIM object
            JsonNode jres = JsonUtil.getJsonTree(body);
            ScimResource res = new ScimResource(smgr, jres, "Users");
            ExtensionValues ext = res.getExtensionValues("urn:ietf:params:scim:schemas:extension:enterprise:2.0:User");
            assertThat(ext)
                    .as("Check the enterprise user extension present")
                    .isNotNull();
            StringValue val = (StringValue) ext.getValue("division");
            String value = val.toString();
            assertThat(value)
                    .as("Check value of division is 'Theme Park'.")
                    .isEqualTo("Theme Park");


        } catch (IOException | ParseException | ScimException e) {
            fail("Exception occured making GET request for bjensen", e);
        }
    }

    /**
     * This test attempts to retrieve the previously created user using the returned location.
     */
    @Test
    public void c3_ScimGetUser_RootBasic() throws MalformedURLException {
        String req = TestUtils.mapPathToReqUrl(baseUrl, bJensonUrl);

        logger.info("C3. Retrieving user from backend (with root basic auth) using: " + req);

        HttpUriRequest request = new HttpGet(req);

        String username = cmgr.getRootUser();
        String pass = cmgr.getRootPassword();

        String auth = "Basic " + Base64.getEncoder().encodeToString((username + ":" + pass).getBytes());

        request.addHeader(HttpHeaders.AUTHORIZATION, auth);

        try {
            HttpResponse resp = execute(request);
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


            logger.debug("Entry retrieved:\n" + body);

            // Check that the result can be parsed as a SCIM object
            JsonNode jres = JsonUtil.getJsonTree(body);
            ScimResource res = new ScimResource(smgr, jres, "Users");
            ExtensionValues ext = res.getExtensionValues("urn:ietf:params:scim:schemas:extension:enterprise:2.0:User");
            assertThat(ext)
                    .as("Check the enterprise user extension present")
                    .isNotNull();
            StringValue val = (StringValue) ext.getValue("division");
            String value = val.toString();
            assertThat(value)
                    .as("Check value of division is 'Theme Park'.")
                    .isEqualTo("Theme Park");


        } catch (IOException | ParseException | ScimException e) {
            fail("Exception occured making GET request for bjensen", e);
        }
    }

    @Test
    public void c4_ScimGetUsersAsBJensen() throws MalformedURLException {
        String req = TestUtils.mapPathToReqUrl(baseUrl, bJensonUrl);

        logger.info("C4a. Retrieving self (as bjensent@example.com BASIC auth) from backend using: " + req);

        HttpUriRequest request = new HttpGet(req);
        String username = "bjensen@example.com";

        String auth = "Basic " + Base64.getEncoder().encodeToString((username + ":" + testPass).getBytes());

        request.addHeader(HttpHeaders.AUTHORIZATION, auth);

        try {
            HttpResponse resp = execute(request);
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
                    .as("Contains userName value as permitted by aci")
                    .contains("\"userName\"");
            assertThat(body)
                    .as("Check that unauthorized attributes not returned (e.g. \"ims\"")
                    .doesNotContain("\"ims\"");


            logger.debug("Entry retrieved:\n" + body);


            //try without authorization (should fail)
            req = TestUtils.mapPathToReqUrl(baseUrl, jSmithUrl);

            logger.info("C4b. Retrieving jsmith (as anonymous) from backend using: " + req);

            request = new HttpGet(req);


            resp = execute(request);

            assertThat(resp.getStatusLine().getStatusCode())
                    .as("GET User - Check for status response 401 unauthorized")
                    .isEqualTo(ScimResponse.ST_UNAUTHORIZED);

            req = TestUtils.mapPathToReqUrl(baseUrl, jSmithUrl);

            logger.info("C4c. Retrieving jsmith (as bjensen@example.com BASIC auth) from backend using: " + req);

            request = new HttpGet(req);
            request.addHeader(HttpHeaders.AUTHORIZATION, auth);

            resp = execute(request);
            entity = resp.getEntity();

            assertThat(resp.getStatusLine().getStatusCode())
                    .as("GET User - Check for status response 200 OK")
                    .isEqualTo(ScimResponse.ST_OK);

            body = EntityUtils.toString(entity);

            assertThat(body)
                    .as("Check that it is not a ListResponse")
                    .doesNotContain(ScimParams.SCHEMA_API_ListResponse);

            assertThat(body)
                    .as("Is user jsmith")
                    .contains("jsmith@example.com");

            assertThat(body)
                    .as("Check that unauthorized attributes not returned (e.g. \"ims\"")
                    .doesNotContain("\"ims\"");
            assertThat(body)
                    .as("Check that unauthorized attributes not returned (e.g. \"nickName\"")
                    .doesNotContain("\"nickName\"");

            assertThat(body)
                    .as("Contains userName value as permitted by aci")
                    .contains("\"userName\"");


            logger.debug("Entry retrieved:\n" + body);

        } catch (IOException e) {
            fail("Exception occured making GET request for bjensen", e);
        }
    }

    /**
     * This test tries to search for the previously created user by searching on filter name
     */
    @Test
    public void d1_ScimSearchUserAsJwtAdminTest() throws MalformedURLException {

        logger.info("D1. Search using GET for user from backend with filter=UserName eq bjensen@example.com");

        String req = TestUtils.mapPathToReqUrl(baseUrl,
                "/Users?filter=" + URLEncoder.encode("UserName eq bjensen@example.com", StandardCharsets.UTF_8));

        HttpUriRequest request = new HttpGet(req);
        request.addHeader(HttpHeaders.AUTHORIZATION, bearer);
        try {
            HttpResponse resp = execute(request);
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

            // Check that the result can be parsed as a SCIM object
            JsonNode jres = JsonUtil.getJsonTree(body);
            jres = jres.get("Resources").get(0);
            ScimResource res = new ScimResource(smgr, jres, "Users");

            ExtensionValues ext = res.getExtensionValues("urn:ietf:params:scim:schemas:extension:enterprise:2.0:User");
            assertThat(ext)
                    .as("Check the enterprise user extension present")
                    .isNotNull();
            StringValue val = (StringValue) ext.getValue("division");
            assertThat(val.toString())
                    .as("Check value of division is 'Theme Park'.")
                    .isEqualTo("Theme Park");

        } catch (IOException | ParseException | ScimException e) {
            fail("Exception occured making GET filter request for bjensen", e);
        }
    }

    /**
     * This test searches for the previously created user by searching on filter name and uses POST
     */
    @Test
    public void d2_ScimSearchUserTest() throws MalformedURLException {

        logger.info("D2. POST Search user from backend with filter=UserName eq bjensen@example.com");


        String req = TestUtils.mapPathToReqUrl(baseUrl,
                "/Users/.search");
        //?filter="+URLEncoder.encode("UserName eq bjensen@example.com",StandardCharsets.UTF_8);

        HttpPost request = new HttpPost(req);
        request.addHeader(HttpHeaders.AUTHORIZATION, bearer);

        request.setHeader("Content-type", ScimParams.SCIM_MIME_TYPE);
        request.setHeader("Accept", ScimParams.SCIM_MIME_TYPE);
        try {
            StringWriter writer = new StringWriter();
            JsonGenerator gen = JsonUtil.getGenerator(writer, true);

            gen.writeStartObject();
            gen.writeArrayFieldStart("schemas");
            gen.writeString(ScimParams.SCHEMA_API_SearchRequest);
            gen.writeEndArray();

            gen.writeStringField("filter", "userName eq bjensen@example.com");
            gen.writeArrayFieldStart("attributes");
            gen.writeString("userName");
            gen.writeString("name");
            gen.writeEndArray();

            gen.writeEndObject();
            gen.close();
            writer.close();

            StringEntity sEntity = new StringEntity(writer.toString(), ContentType.create(ScimParams.SCIM_MIME_TYPE));

            request.setEntity(sEntity);

            HttpResponse resp = execute(request);
            HttpEntity entity = resp.getEntity();

            assertThat(resp.getStatusLine().getStatusCode())
                    .as("GET User - Check for status response 200 OK")
                    .isEqualTo(ScimResponse.ST_OK);

            String body = EntityUtils.toString(entity);

            assertThat(body)
                    .as("Check query response is a ListResponse")
                    .contains(ScimParams.SCHEMA_API_ListResponse);

            assertThat(body)
                    .as("givenName sub attribute of name is present")
                    .contains("\"givenName\"");

            assertThat(body)
                    .as("Is user bjensen")
                    .contains("bjensen@example.com");
            logger.debug("Entry retrieved:\n" + body);

        } catch (IOException e) {
            fail("Exception occured making POST Search filter request for bjensen", e);
        }
    }

    @Test
    public void e_ScimSearchValPathUserTest() throws MalformedURLException {

        logger.info("\tD. Searching user from backend with filter=UserName eq bjensen@example.com and addresses[country eq \\\"USA\\\" and type eq \\\"home\\\"]");

        String req = TestUtils.mapPathToReqUrl(baseUrl,
                "/Users?filter=" + URLEncoder.encode("UserName eq bjensen@example.com and addresses[country eq \"USA\" and type eq \"home\"]", StandardCharsets.UTF_8));

        HttpUriRequest request = new HttpGet(req);
        request.addHeader(HttpHeaders.AUTHORIZATION, bearer);

        try {
            HttpResponse resp = execute(request);
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

            // Check that the result can be parsed as a SCIM object
            JsonNode jres = JsonUtil.getJsonTree(body);
            jres = jres.get("Resources").get(0);
            ScimResource res = new ScimResource(smgr, jres, "Users");

            ExtensionValues ext = res.getExtensionValues("urn:ietf:params:scim:schemas:extension:enterprise:2.0:User");
            assertThat(ext)
                    .as("Check the enterprise user extension present")
                    .isNotNull();
            StringValue val = (StringValue) ext.getValue("division");
            assertThat(val.toString())
                    .as("Check value of division is 'Theme Park'.")
                    .isEqualTo("Theme Park");

        } catch (IOException | ParseException | ScimException e) {
            fail("Exception occured making GET filter request for bjensen", e);
        }
    }

    @Test
    public void f_updateUserTest() throws MalformedURLException {


        String req = TestUtils.mapPathToReqUrl(baseUrl, bJensonUrl);
        logger.info("\tF. Modify user with PUT Test at: " + req);

        HttpUriRequest request = new HttpGet(req);

        try {
            // first try anonymous test
            logger.debug("\t\tAnonymous sub-test");
            HttpResponse resp = execute(request);

            assertThat(resp.getStatusLine().getStatusCode())
                    .as("Confirm annonymous is unauthorized")
                    .isEqualTo(ScimResponse.ST_UNAUTHORIZED);

            logger.debug("\t\tBJsensen self-update sub-test");
            request = new HttpGet(req);
            String username = "bjensen@example.com";
            String auth = "Basic " + Base64.getEncoder().encodeToString((username + ":" + testPass).getBytes());
            request.addHeader(HttpHeaders.AUTHORIZATION, auth);

            resp = execute(request);
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
            logger.debug("Entry retrieved:\n" + body);

            // Check that the result can be parsed as a SCIM object
            JsonNode jres = JsonUtil.getJsonTree(body);
            ScimResource res = new ScimResource(smgr, jres, "Users");

            Attribute name = res.getAttribute("displayName", null);

            // Modify the result and put back
            String dname = "\"Babs (TEST) Jensen\"";
            JsonNode node = JsonUtil.getJsonTree(dname);
            //node.get("displayName");
            StringValue newval = new StringValue(name, node);
            //res.removeValue(name);
            res.addValue(newval);

            HttpPut put = new HttpPut(req);
            put.addHeader(HttpHeaders.AUTHORIZATION, auth);
            entity = new StringEntity(res.toJsonString(), ContentType.create(ScimParams.SCIM_MIME_TYPE));
            put.setEntity(entity);

            resp = execute(put);
            assertThat(resp.getStatusLine().getStatusCode())
                    .as("PUT User - Check for status response 200 OK")
                    .isEqualTo(ScimResponse.ST_OK);

            body = EntityUtils.toString(entity);

            assertThat(body)
                    .as("Check that PUT response is not a ListResponse")
                    .doesNotContain(ScimParams.SCHEMA_API_ListResponse);

            assertThat(body)
                    .as("Contains test value")
                    .contains("Babs (TEST)");
            logger.debug("Entry retrieved:\n" + body);
        } catch (IOException | ParseException | ScimException e) {
            fail("Exception occured making GET request for bjensen", e);
        }
    }

    @Test
    public void g_ScimDeleteUserTest() throws MalformedURLException {

        String req = TestUtils.mapPathToReqUrl(baseUrl, bJensonUrl);
        logger.info("\tG. Deleting user at: " + req);

        HttpUriRequest request = new HttpDelete(req);
        request.addHeader(HttpHeaders.AUTHORIZATION, bearer);

        try {
            HttpResponse resp = execute(request);

            // confirm status 204 per RFC7644 Sec 3.6
            assertThat(resp.getStatusLine().getStatusCode())
                    .as("Confirm succesfull deletion of user")
                    .isEqualTo(ScimResponse.ST_NOCONTENT);

            // Try to retrieve the deleted object. Should return 404
            request = new HttpGet(req);
            request.addHeader(HttpHeaders.AUTHORIZATION, bearer);
            resp = execute(request);
            assertThat(resp.getStatusLine().getStatusCode())
                    .as("Confirm deleted user was not findable")
                    .isEqualTo(ScimResponse.ST_NOTFOUND);

            // Try delete of non-existent object, should be 404
            request = new HttpDelete(req);
            request.addHeader(HttpHeaders.AUTHORIZATION, bearer);
            resp = execute(request);
            assertThat(resp.getStatusLine().getStatusCode())
                    .as("Confirm not found when deleting non-existent resource")
                    .isEqualTo(ScimResponse.ST_NOTFOUND);


        } catch (IOException e) {
            fail("Exception occured in DELETE test for bjensen", e);
        }
    }

    @Test
    public void h_ConfigEndpointsTest() throws IOException {
        String req = TestUtils.mapPathToReqUrl(baseUrl, "/ServiceProviderConfig");
        logger.info("\tH. Testing Config Endpoints");
        HttpUriRequest request = new HttpGet(req);
        request.addHeader(HttpHeaders.AUTHORIZATION, bearer);
        HttpResponse resp = execute(request);
        HttpEntity body = resp.getEntity();
        String res = EntityUtils.toString(body);

        logger.debug("ServiceProviderConfig res:\n" + res);
        assertThat(res)
                .as("ServiceProviderConfig does not contain ListResponse")
                .doesNotContain(ScimParams.SCHEMA_API_ListResponse);

        assertThat(res)
                .as("Has schemas ServiceProviderConfig")
                .contains(ScimParams.SCHEMA_SCHEMA_ServiceProviderConfig);

        req = TestUtils.mapPathToReqUrl(baseUrl, "/Schemas");
        request = new HttpGet(req);
        request.addHeader(HttpHeaders.AUTHORIZATION, bearer);
        resp = execute(request);
        body = resp.getEntity();

        res = EntityUtils.toString(body);
        logger.debug("Response returned: \n" + res);

        assertThat(res).isNotNull();

        assertThat(res)
                .as("Is a List Response")
                .startsWith("{\n" +
                        "  \"schemas\" : [ \"urn:ietf:params:scim:api:messages:2.0:ListResponse\" ]");
        assertThat(res)
                .as("Contains correct items per page(7)")
                .contains("\"itemsPerPage\" : 7,");
        assertThat(res)
                .as("Contains correct total numbrt of results (7)")
                .contains("\"totalResults\" : 7,");
        assertThat(res)
                .as("Confirtm List Response Type")
                .contains(ListResponse.SCHEMA_LISTRESP);

        String auth = "Basic " + Base64.getEncoder().encodeToString((rootUser + ":" + rootPassword).getBytes());
        req = TestUtils.mapPathToReqUrl(baseUrl, "/Schemas");
        request = new HttpGet(req);
        request.addHeader(HttpHeaders.AUTHORIZATION, auth);
        resp = execute(request);
        body = resp.getEntity();

        res = EntityUtils.toString(body);
        logger.debug("Response returned: \n" + res);

        assertThat(res).isNotNull();

        assertThat(res)
                .as("Is a List Response")
                .startsWith("{\n" +
                        "  \"schemas\" : [ \"urn:ietf:params:scim:api:messages:2.0:ListResponse\" ]");
        assertThat(res)
                .as("Contains correct items per page(7)")
                .contains("\"itemsPerPage\" : 7,");
        assertThat(res)
                .as("Contains correct total numbrt of results (7)")
                .contains("\"totalResults\" : 7,");
        assertThat(res)
                .as("Confirtm List Response Type")
                .contains(ListResponse.SCHEMA_LISTRESP);
    }

}
