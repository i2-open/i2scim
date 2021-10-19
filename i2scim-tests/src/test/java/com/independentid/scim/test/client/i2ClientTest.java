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

package com.independentid.scim.test.client;

import com.independentid.scim.backend.BackendException;
import com.independentid.scim.client.ResourceBuilder;
import com.independentid.scim.client.ScimReqParams;
import com.independentid.scim.client.i2scimClient;
import com.independentid.scim.client.i2scimResponse;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.err.InvalidSyntaxException;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.JsonPatchRequest;
import com.independentid.scim.protocol.ScimResponse;
import com.independentid.scim.resource.MultiValue;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.resource.Value;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.test.auth.ScimAuthTestProfile;
import com.independentid.scim.test.misc.TestUtils;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.apache.http.HttpStatus;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@QuarkusTest
@TestProfile(ScimAuthTestProfile.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class i2ClientTest {
    private final static Logger logger = LoggerFactory.getLogger(i2ClientTest.class);

    final static String schemaLoc = "/schema/scimSchema.json";
    final static String typeLoc = "/schema/resourceTypes.json";
    private static final String testUserFile1 = "/schema/TestUser-bjensen.json";
    private static final String testUserFile2 = "/schema/TestUser-jsmith.json";

    static String user1Url = null;
    static String user2Url = null;
    static ScimResource user1res = null;

    static String modificationDate = null;
    static String etag = null;

    @ConfigProperty(name = "scim.security.root.username", defaultValue = "admin")
    String rootUser;

    @ConfigProperty(name = "scim.security.root.password", defaultValue = "admin")
    String rootPassword;

    @TestHTTPResource("/")
    URL rootUrl;

    @Inject
    TestUtils testUtils;

    public static String bearer = "Bearer eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJSLURla2xmOU5XSXpRMVVmRTVRNnY5UXRnVnNDQ1ROdE5iRUxnNXZjZ1J3In0.eyJleHAiOjE2MzQ0OTIzMDcsImlhdCI6MTYwMjk1NjMwNywianRpIjoiNWYyNDQ0ZGUtMDVlNi00MDFjLWIzMjYtZjc5YjJiMmZhNmZiIiwiaXNzIjoiaHR0cDovLzEwLjEuMTAuMTA5OjgxODAvYXV0aC9yZWFsbXMvZGV2Iiwic3ViIjoiNDA2MDQ0OWYtNDkxMy00MWM1LTkxYjAtYTRlZjY5MjYxZTY0IiwidHlwIjoiQmVhcmVyIiwiYXpwIjoic2NpbS1zZXJ2ZXItY2xpZW50Iiwic2Vzc2lvbl9zdGF0ZSI6ImE2NGZkNjA3LWU1MzItNGQ0Ni04MGQ2LWE0NTUzYzRjZWQ1OCIsImFjciI6IjEiLCJyZWFsbV9hY2Nlc3MiOnsicm9sZXMiOlsibWFuYWdlciIsIm9mZmxpbmVfYWNjZXNzIl19LCJzY29wZSI6ImZ1bGwgbWFuYWdlciIsImNsaWVudElkIjoic2NpbS1zZXJ2ZXItY2xpZW50IiwiY2xpZW50SG9zdCI6IjEwLjEuMTAuMTE4IiwidXNlcl9uYW1lIjoic2VydmljZS1hY2NvdW50LXNjaW0tc2VydmVyLWNsaWVudCIsImNsaWVudEFkZHJlc3MiOiIxMC4xLjEwLjExOCJ9.Wouztkr7APb2_juPBhMtPbAqmFwQqsDQXYIQBeDpMuWnKGXZZMs17Rpzq8YnVSGfbfyrAduMAK2PAWnw8hxC4cGc0xEVS3lf-KcA5bUr4EnLcPVeQdEPsQ5eLrt_-BSPCQ8ere2fw6-Obv7FJ6aofAlT8LttWvEvkPzo2R0T0aZX8Oh7b15-icAVZ8ER0j7aFQ2k34dAq0Uwn58wakT6MA4qEFxze6GLeBuC4cAqNPYoOkUWTJxu1J_zLFDkpomt_zzx9u0Ig4asaErRyPj-ettElaGXMELZrNsaVbikCHgK7ujwMJDlEhUf8jxM8qwhCuf50-9ZydPAFA8Phj6FkQ";

    static i2scimClient client = null;

    /**
     * This test validates that all 3 initializer constructors work. The first connection is retained for transaction
     * tests.
     */
    @Test
    public void a_InitializeClientTest() {
        logger.info("A. Client Initialization Tests");

        logger.info("\t0. Resetting database");
        try {
            testUtils.resetProvider(true);
        } catch (ScimException | BackendException | IOException e) {
            fail("Exception occurred during database reset: " + e.getMessage(), e);
        }

        logger.info("\t1. Using auto-load schema basic auth");
        UsernamePasswordCredentials cred = new UsernamePasswordCredentials(rootUser, rootPassword);
        try {
            i2scimClient test = new i2scimClient(rootUrl.toString(), cred);
            assertThat(test.hasSearchSupport())
                    .as("Has filter support")
                    .isTrue();
            assertThat(test.hasNoEtagSupport())
                    .as("Supports ETAGS")
                    .isFalse();
            assertThat(test.hasSortSupport())
                    .as("Supports Sort")
                    .isTrue();
            assertThat(test.hasChangePasswordSupport())
                    .as("Supports change password")
                    .isTrue();
            assertThat(test.hasPatchSupport())
                    .as("Supports PATCH")
                    .isTrue();
            client = test;

            SchemaManager smgr = client.getSchemaManager();
            Attribute name = smgr.findAttribute("User:name", null);
            assertThat(name)
                    .as("Check User:name is defined")
                    .isNotNull();
            assertThat(name.getType())
                    .as("Check User:name is complex")
                    .isEqualTo(Attribute.TYPE_Complex);
            assertThat(name.getSubAttributesMap().size())
                    .as("Check User:name has sub attributes.")
                    .isNotEqualTo(0);
        } catch (ScimException e) {
            fail("Failed with SCIM exception: " + e.getMessage(), e);
        } catch (IOException e) {
            fail("Failed with IO exception: " + e.getMessage(), e);
        } catch (URISyntaxException e) {
            fail("Unexpected URI exception: " + e.getMessage(), e);
        }

        logger.info("\t2. Using auto-load schema bearer");
        try {
            i2scimClient test = new i2scimClient(rootUrl.toString(), bearer);
            assertThat(test.hasSearchSupport())
                    .as("Has filter support")
                    .isTrue();
            assertThat(test.hasNoEtagSupport())
                    .as("Supports ETAGS")
                    .isFalse();
            assertThat(test.hasSortSupport())
                    .as("Supports Sort")
                    .isTrue();
            assertThat(test.hasChangePasswordSupport())
                    .as("Supports change password")
                    .isTrue();
            assertThat(test.hasPatchSupport())
                    .as("Supports PATCH")
                    .isTrue();
            test.close();
        } catch (ScimException e) {
            fail("Failed with SCIM exception: " + e.getMessage(), e);
        } catch (IOException e) {
            fail("Failed with IO exception: " + e.getMessage(), e);
        } catch (URISyntaxException e) {
            fail("Unexpected URI exception: " + e.getMessage(), e);
        }

        logger.info("\t3. Using local schema");
        try {
            i2scimClient test = new i2scimClient(rootUrl.toString(), bearer, schemaLoc, typeLoc);
            assertThat(test.hasSearchSupport())
                    .as("Has filter support")
                    .isTrue();
            assertThat(test.hasNoEtagSupport())
                    .as("Supports ETAGS")
                    .isFalse();
            assertThat(test.hasSortSupport())
                    .as("Supports Sort")
                    .isTrue();
            assertThat(test.hasChangePasswordSupport())
                    .as("Supports change password")
                    .isTrue();
            assertThat(test.hasPatchSupport())
                    .as("Supports PATCH")
                    .isTrue();
            test.close();
        } catch (ScimException e) {
            fail("Failed with SCIM exception: " + e.getMessage(), e);
        } catch (IOException e) {
            fail("Failed with IO exception: " + e.getMessage(), e);
        } catch (URISyntaxException e) {
            fail("Unexpected URI exception: " + e.getMessage(), e);
        }
    }

    @Test
    public void b_addTest() throws IOException, ParseException {
        logger.info("B. Performing Add test");

        logger.info("\t1. Builder Tests");

        String addressHollywood = "{\n" +
                "      \"streetAddress\": \"456 Hollywood Blvd\",\n" +
                "      \"locality\": \"Hollywood\",\n" +
                "      \"region\": \"CA\",\n" +
                "      \"postalCode\": \"91608\",\n" +
                "      \"country\": \"USA\",\n" +
                "      \"formatted\": \"456 Hollywood Blvd\\nHollywood, CA 91608 USA\",\n" +
                "      \"type\": \"home\"\n" +
                "     }";

        String phoneNumbers = "[\n" +
                "    {\n" +
                "      \"value\": \"555-555-5555\",\n" +
                "      \"type\": \"work\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"value\": \"555-555-4444\",\n" +
                "      \"type\": \"mobile\"\n" +
                "    }\n" +
                "  ]";

        ResourceBuilder builder = null;
        ScimResource result = null;
        ScimResource orig = null;
        try {
            builder = client.getResourceBuilder("Users")
                    .addStringAttribute("username", "jim123")
                    .addComplexAttribute(client.getComplexValueBuilder("name")
                            .withStringAttribute("familyName", "Smith")
                            .withStringAttribute("givenName", "Jim")
                            .withStringAttribute("middleName", "John")
                            .withStringAttribute("honorificSuffix", "Jr")
                            .buildComplexValue());
            fail("Builder should have thrown Attribute is not a complex attribute schema exception");
        } catch (SchemaException schemaException) {
            String msg = schemaException.getMessage();
            assertThat(msg)
                    .as("Check for wrong attribute/not complex")
                    .contains("is not a complex");
        }


        try {
            builder = client.getResourceBuilder("Users")
                    .addStringAttribute("username", "jim123")
                    .addComplexAttribute(client.getComplexValueBuilder("User:name")
                            .withStringAttribute("familyName", "Smith")
                            .withStringAttribute("givenName", "Jim")
                            .withStringAttribute("middleName", "John")
                            .withStringAttribute("honorificSuffix", "Jr")
                            .buildComplexValue())
                    .addStringAttribute("displayName", "Jim Smith")
                    .addStringAttribute("nickName", "Jim")
                    .addMultiValueAttribute(client.getMultiValueBuilder("emails")
                            .withComplexValue(client.getComplexValueBuilder("emails")
                                    .withStringAttribute("type", "work")
                                    .withStringAttribute("value", "jsmith@example.com")
                                    .withBooleanAttribute("primary", true)
                                    .buildComplexValue())
                            .withComplexValue(client.getComplexValueBuilder("emails")
                                    .withStringAttribute("type", "home")
                                    .withStringAttribute("value", "jimmy@nowhere.com")
                                    .buildComplexValue()
                            ).buildMultiValue())
                    .addMultiValueAttribute(client.getMultiValueBuilder("addresses")
                            .withComplexValue(client.getComplexValueBuilder("addresses")
                                    .withStringAttribute("streetAddress", "100 W Broadway Ave")
                                    .withStringAttribute("locality", "Vancouver")
                                    .withStringAttribute("region", "BC")
                                    .withStringAttribute("country", "CA")
                                    .withStringAttribute("type", "work")
                                    .buildComplexValue())
                            .withJsonString(addressHollywood)
                            .buildMultiValue())
                    .addMultiValueAttribute(client.getMultiValueBuilder("phoneNumbers")
                            .withJsonString(phoneNumbers)
                            .buildMultiValue());
            orig = builder.build();

        } catch (IOException e) {
            fail("IO Error communicating with server: " + e.getMessage());
        } catch (ScimException e) {
            fail("SCIM error: " + e.getMessage());
        } catch (ParseException e) {
            fail("JSON Parsing exception: " + e.getMessage(), e);
        }

        try {
            logger.info("\t2. Builder create Test");
            assert builder != null;
            result = builder.buildAndCreate(null);
        } catch (IOException e) {
            fail("IO Error communicating with server: " + e.getMessage());
        } catch (ScimException e) {
            fail("SCIM error: " + e.getMessage());
        } catch (URISyntaxException e) {
            fail("URI Syntax error", e);
        } catch (ParseException e) {
            fail("JSON Parsing exception: " + e.getMessage(), e);
        }

        assertThat(orig)
                .as("Original SciResource parsed")
                .isNotNull();
        assertThat(orig.getId())
                .as("No identifier exists")
                .isNull();

        assertThat(result)
                .as("Received creation result")
                .isNotNull();
        assertThat(result.getId())
                .as("Has an identifier assigned")
                .isNotNull();

        logger.info("\t3. Duplicate test");

        boolean wasDup = false;
        try {
            // this should cause a Bad Request - duplicate
            builder.buildAndCreate(null);
        } catch (IOException e) {
            fail("IO Error communicating with server: " + e.getMessage());
        } catch (ScimException e) {
            assertThat(e.getScimType())
                    .as("Duplicate error received")
                    .isEqualTo(ScimResponse.ERR_TYPE_UNIQUENESS);
            wasDup = true;
        } catch (URISyntaxException e) {
            fail("URI Syntax error", e);
        } catch (ParseException e) {
            fail("JSON Parsing exception: " + e.getMessage(), e);
        }
        assertThat(wasDup)
                .as("Duplicate was detected")
                .isTrue();

        logger.info("\t4. Create from InputStream");

        // load a pre-existing resource
        InputStream userStream = ConfigMgr.findClassLoaderResource(testUserFile1);
        if (userStream != null) {
            try {
                ScimResource res = client.getResourceBuilder(userStream).build();

                i2scimResponse response = client.create(res, null);

                assertThat(response.hasError())
                        .as("Has no error")
                        .isFalse();
                assertThat(response.getStatus())
                        .as("Has Status Created")
                        .isEqualTo(HttpStatus.SC_CREATED);
                user1Url = response.getLocation();
                modificationDate = response.getLastModification();
                etag = response.getEtag();
                assertThat(user1Url)
                        .as("Check location is not blank")
                        .isNotNull();
            } catch (URISyntaxException e) {
                fail("Unexpected URI Exception: " + e.getMessage());
            } catch (ScimException e) {
                fail("Received SCIM error: " + e.getMessage(), e);
            }
        } else
            fail("Unable to locate: " + testUserFile1);

        userStream = ConfigMgr.findClassLoaderResource(testUserFile2);
        try {
            i2scimResponse response = client.create(client.getResourceBuilder(userStream).build(), null);
            assertThat(response.getStatus())
                    .as("User2 was added.")
                    .isEqualTo(HttpStatus.SC_CREATED);
            user2Url = response.getLocation();
            assertThat(user2Url)
                    .as("Check location is not blank")
                    .isNotNull();
        } catch (ScimException e) {
            fail("Received SCIM error: " + e.getMessage(), e);

        } catch (URISyntaxException e) {
            fail("Unexpected URI Exception: " + e.getMessage());
        }

        logger.info("\t4. Load Resource from File");

        File user1File = new File("target/test-classes" + testUserFile1);  // chop off the leading / to get relative
        assertThat(user1File)
                .as("User1 file was located.")
                .exists();
        try {
            ResourceBuilder buildTest = client.getResourceBuilder(user1File);
        } catch (ScimException e) {
            fail("Unable to load user from file");
        }

    }

    @Test
    public void c_GetTests() throws ScimException, URISyntaxException, IOException, ParseException {
        logger.info("C. GET / Search tests");
        logger.info("\t1. Get specific resource test");

        // This test will cause a single result to be returned from user. This tests the single-result parse mode
        i2scimResponse resp = client.get(user1Url, null);

        assertThat(resp.hasError())
                .as("Has no error")
                .isFalse();
        assertThat(resp.isSingle())
                .as("Is a single result")
                .isTrue();
        assertThat(resp.hasNext())
                .as("Has a result")
                .isTrue();
        user1res = resp.next();

        assertThat(resp.hasNext())
                .as("Has no more entries")
                .isFalse();
        resp.close();

        logger.info("\t2a. GET If Modified test");
        ScimReqParams params = new ScimReqParams();
        params.setHead_ifModSince(modificationDate);
        resp = client.get(user1Url, params);
        assertThat(resp.getStatus())
                .as("Confirm not modified")
                .isEqualTo(HttpStatus.SC_NOT_MODIFIED);
        assertThat(resp.hasError())
                .as("Condition not modified is not an error")
                .isFalse();
        assertThat(resp.hasNext())
                .as("Should have no returned result.")
                .isFalse();
        resp.close();


        logger.info("\t2b. GET If Not Match test");
        params = new ScimReqParams();
        params.setHead_ifNoMatch(etag);
        resp = client.get(user1Url, params);
        assertThat(resp.getStatus())
                .as("Confirm not modified")
                .isEqualTo(HttpStatus.SC_NOT_MODIFIED);
        assertThat(resp.hasError())
                .as("Condition not modified is not an error")
                .isFalse();
        assertThat(resp.hasNext())
                .as("Should have no returned result.")
                .isFalse();
        resp.close();

        logger.info("\t2c. Check faulty pre-condition");
        // Normally a GET with ifUnModSince doesn't make sense.
        params = new ScimReqParams();
        params.setHead_ifUnModSince(modificationDate);
        boolean badDetected = false;
        try {
            client.get(user1Url, params);
        } catch (InvalidSyntaxException e) {
            badDetected = true;
        }
        assertThat(badDetected)
                .as("Check that faulty request detected by client")
                .isTrue();


        logger.info("\t3. Retrieve all users using streaming client");
        // This test will cause a ListResponse structure to be returned. This tests the multi-result parser

        resp = client.get("/Users", null);
        assertThat(resp.hasError())
                .as("Has no error")
                .isFalse();
        assertThat(resp.isSingle())
                .as("Is NOT a single result")
                .isFalse();
        assertThat(resp.hasNext())
                .as("Has a result")
                .isTrue();
        int count = resp.getTotalResults();
        assertThat(count)
                .as("total results equal to 3")
                .isEqualTo(3);

        ArrayList<ScimResource> items = new ArrayList<>();
        resp.forEachRemaining(items::add);
        assertThat(items.size())
                .as("3 items returned.")
                .isEqualTo(3);

    }

    @Test
    public void d_SearchTests() throws ScimException, URISyntaxException, IOException, ParseException {
        logger.info("D. Search Tests");
        logger.info("\t1. Search specific resource using searchGet (username eq bjensen@example.com)");

        i2scimResponse resp = client.searchGet(user1Url, "username eq bjensen@example.com", null);

        assertThat(resp.hasError())
                .as("Has no error")
                .isFalse();
        assertThat(resp.isSingle())
                .as("Is a List result")
                .isFalse();
        assertThat(resp.hasNext())
                .as("Has a result")
                .isTrue();
        ScimResource test1 = resp.next();

        assertThat(resp.hasNext())
                .as("Has no more entries")
                .isFalse();
        resp.close();

        logger.info("\t2. Search specific resource using searchPost (username eq bjensen@example.com)");
        resp = client.searchPost(user1Url, "username eq bjensen@example.com", null);

        assertThat(resp.hasError())
                .as("Has no error")
                .isFalse();
        assertThat(resp.isSingle())
                .as("Is a List result")
                .isFalse();
        assertThat(resp.hasNext())
                .as("Has a result")
                .isTrue();
        ScimResource test2 = resp.next();

        assertThat(resp.hasNext())
                .as("Has no more entries")
                .isFalse();
        resp.close();

        assertThat(test1.equals(test2))
                .as("Check that the same resource is returned")
                .isTrue();

        logger.info("\t3. Search that returns no results.");
        resp = client.searchPost(user1Url, "username eq dummy", null);

        assertThat(resp.hasError())
                .as("Has no error")
                .isFalse();
        assertThat(resp.isSingle())
                .as("Is a List result")
                .isFalse();
        assertThat(resp.hasNext())
                .as("Has no result")
                .isFalse();
        assertThat(resp.getTotalResults())
                .as("Total results is 0")
                .isEqualTo(0);
        resp.close();

        logger.info("\t4. Search to match all User entries");
        resp = client.searchGet("/Users", "meta.resourceType eq User", null);
        assertThat(resp.hasError())
                .as("Has no error")
                .isFalse();
        assertThat(resp.isSingle())
                .as("Is NOT a single result")
                .isFalse();
        assertThat(resp.hasNext())
                .as("Has a result")
                .isTrue();
        int count = resp.getTotalResults();
        assertThat(count)
                .as("total results equal to 3")
                .isEqualTo(3);

        ArrayList<ScimResource> items = new ArrayList<>();
        resp.forEachRemaining(items::add);
        assertThat(items.size())
                .as("3 items returned.")
                .isEqualTo(3);

    }

    @Test
    public void e_PutTests() {
        logger.info("E. Modify with PUT Test");


        try {
            ResourceBuilder builder = client.getResourceBuilder(user1res);
            // This should add an additional ims value (giving 2 values)
            builder.addComplexAttribute(client.getComplexValueBuilder("ims")
                    .withStringAttribute("value", "@barbjans2")
                    .withStringAttribute("type", "twitter")
                    .buildComplexValue());

            ScimResource res = builder.buildAndPut(null);

            Value val = res.getValue("ims");
            assertThat(val)
                    .isNotNull();
            assertThat(val)
                    .isInstanceOf(MultiValue.class);
            MultiValue mval = (MultiValue) val;
            assertThat(mval.size())
                    .isEqualTo(2);
        } catch (IOException e) {
            fail("IO Error communicating with server: " + e.getMessage());
        } catch (ScimException e) {
            fail("SCIM error: " + e.getMessage());
        } catch (URISyntaxException e) {
            fail("URI Syntax error", e);
        } catch (ParseException e) {
            fail("JSON Parsing exception: " + e.getMessage(), e);
        }
    }

    @Test
    public void f_PatchTest() throws SchemaException {
        logger.info("F. Modify with PATCH Test");

        try {
            Thread.sleep(1500);  // wait to avoid timing issue
        } catch (InterruptedException ignore) {
        }

        JsonPatchRequest req = client.getPatchRequestBuilder()
                .withRemoveOperation("ims[type eq \"twitter\"]")
                .withReplaceOperation("ims[type eq aim]", client.getComplexValueBuilder("ims").withStringAttribute("type", "abc")
                        .withStringAttribute("value", "tobedefined").buildComplexValue()).build();
        try {
            i2scimResponse resp = client.patch(user1Url, req, null);

            assertThat(resp.hasError())
                    .as("Patch has no errors")
                    .isFalse();
            assertThat(resp.hasNext())
                    .as("Has returned a result")
                    .isTrue();

            if (resp.getStatus() == HttpStatus.SC_OK) {
                ScimResource res = resp.next();
                assertThat(resp.hasNext())
                        .as("Check only one item returned.")
                        .isFalse();
                Value val = res.getValue("ims");
                assertThat(val)
                        .isInstanceOf(MultiValue.class);
                MultiValue mval = (MultiValue) val;
                assertThat(mval.size())
                        .as("ims has 1 value left")
                        .isEqualTo(1);

            } else if (resp.getStatus() == HttpStatus.SC_NO_CONTENT) {
                logger.info("Returned with on content response.  No returned representation.");
            } else
                fail("Return with incorrect status: " + resp.getStatus());

        } catch (IOException e) {
            fail("IO Error communicating with server: " + e.getMessage());
        } catch (ScimException e) {
            fail("SCIM error: " + e.getMessage());
        } catch (URISyntaxException e) {
            fail("URI Syntax error", e);
        } catch (ParseException e) {
            fail("JSON Parsing exception: " + e.getMessage(), e);
        }
    }

    @Test
    public void g_DeleteTest() {
        logger.info("G. DELETE Test");
        try {
            i2scimResponse resp = client.delete(user2Url, null);
            assertThat(resp.getStatus())
                    .as("No content response")
                    .isEqualTo(HttpStatus.SC_NO_CONTENT);

            assertThat(resp.hasError())
                    .as("Delete has no errors")
                    .isFalse();
            assertThat(resp.hasNext())
                    .as("Has no returned result")
                    .isFalse();

            resp = client.delete(user2Url, null);
            assertThat(resp.getStatus())
                    .as("No content response")
                    .isEqualTo(HttpStatus.SC_NOT_FOUND);

            assertThat(resp.hasError())
                    .as("Failed delete is an error")
                    .isTrue();
            assertThat(resp.hasNext())
                    .as("Has no returned result")
                    .isFalse();
            resp.close();
        } catch (IOException e) {
            fail("IO Error communicating with server: " + e.getMessage());
        } catch (ScimException e) {
            fail("SCIM error: " + e.getMessage());
        } catch (URISyntaxException e) {
            fail("URI Syntax error", e);
        } catch (ParseException e) {
            fail("JSON Parsing exception: " + e.getMessage(), e);
        }
    }

    @Test
    public void h_HeadTest() {
        logger.info("H. HTTP HEAD Test");

        try {

            i2scimResponse resp = client.headInfo(user1Url, null);
            assertThat(resp.getStatus())
                    .as("Returned status is OK")
                    .isEqualTo(HttpStatus.SC_OK);
            assertThat(resp.getLastModification())
                    .as("Has last modification")
                    .isNotNull();

            assertThat(resp.getLastModification().equals(modificationDate))
                    .as("User1Url has been modified")
                    .isFalse();
            assertThat(resp.getEtag())
                    .isNotNull();
            assertThat(resp.getEtag())
                    .isNotEqualTo(etag);

        } catch (IOException e) {
            fail("IO Error communicating with server: " + e.getMessage());
        } catch (ScimException e) {
            fail("SCIM error: " + e.getMessage());
        } catch (URISyntaxException e) {
            fail("URI Syntax error", e);
        } catch (ParseException e) {
            fail("JSON Parsing exception: " + e.getMessage(), e);
        }
    }

    @Test
    public void i_authenticateTest() {
        UsernamePasswordCredentials cred = new UsernamePasswordCredentials("bjensen@example.com","t1meMa$heen");
        try {
            boolean res = client.authenticateUser(cred);
            assertThat(res)
                    .isNotNull();
            assertThat(res)
                    .as("was authenticated")
                    .isTrue();
        } catch (ScimException | URISyntaxException | IOException | ParseException e) {
            fail("Received exception during authentication: "+e.getMessage(),e);
        }

        cred = new UsernamePasswordCredentials("bjensen@example.com","wrong");
        try {
            boolean res = client.authenticateUser(cred);
            assertThat(res)
                    .isNotNull();
            assertThat(res)
                    .as("was NOT authenticated")
                    .isFalse();
        } catch (ScimException | URISyntaxException | IOException | ParseException e) {
            fail("Received exception during authentication: "+e.getMessage(),e);
        }

        cred = new UsernamePasswordCredentials("dummy","wrong");
        try {
            boolean res = client.authenticateUser(cred);
            assertThat(res)
                    .isNotNull();
            assertThat(res)
                    .as("was NOT authenticated")
                    .isFalse();
        } catch (ScimException | URISyntaxException | IOException | ParseException e) {
            fail("Received exception during authentication: "+e.getMessage(),e);
        }


    }
}
