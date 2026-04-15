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
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.ScimParams;
import com.independentid.scim.protocol.ScimResponse;
import com.independentid.scim.test.misc.TestUtils;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
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
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;


@QuarkusTest
@TestProfile(ScimHttpTestProfile.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class ScimGroupCRUDTest {

    private final static Logger logger = LoggerFactory.getLogger(ScimGroupCRUDTest.class);

    //private static String userSchemaId = "urn:ietf:params:scim:schemas:core:2.0:User";

    @Inject
    TestUtils testUtils;

    @TestHTTPResource("/")
    URL baseUrl;

    private static String user1url = "", user2url = "", grpUrl = "";

    private static final String testUserFile1 = "classpath:/schema/TestUser-bjensen.json";
    private static final String testUserFile2 = "classpath:/schema/TestUser-jsmith.json";

    /**
     * This test actually resets and re-initializes the SCIM Mongo test database.
     */
    @Test
    public void a_initializeMongo() throws Exception {

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
    public void b_PrepareUsers() throws Exception {

        logger.info("\tB1. Add two users...");

        try {

            InputStream userStream = ConfigMgr.findClassLoaderResource(testUserFile1);

            URL rUrl = new URL(baseUrl, "/Users");
            String req = rUrl.toString();


            HttpPost post = new HttpPost(req);

            InputStreamEntity reqEntity = new InputStreamEntity(
                    userStream, -1, ContentType.create(ScimParams.SCIM_MIME_TYPE));
            post.setEntity(reqEntity);

            logger.debug("Executing test add for bjensen: " + post.getMethod() + " " + post.getUri());
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
            reqEntity = new InputStreamEntity(
                    userStream, -1, ContentType.create(ScimParams.SCIM_MIME_TYPE));
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
            Assertions.fail("Exception occured creating bjenson. " + e.getMessage(), e);
        }
    }

    private String memberObj(String ref) {
        String id = ref.substring(ref.lastIndexOf("/") + 1);
        return "{ \"value\": \"" + id + "\",\n" +
                "    \"$ref\": \"" + ref + "\"}";
    }

    @Test
    public void c_createGroupTest() throws Exception {
        logger.info("\tC. Creating Group...");
        String jsonGroup = "{\n" +
                "     \"schemas\": [\"urn:ietf:params:scim:schemas:core:2.0:Group\"],\n" +
                "     \"id\": \"e9e30dba-f08f-4109-8486-d5c6a331660a\",\n" +
                "     \"displayName\": \"TEST Tour Guides\",\n" +
                "     \"members\": [\n";
        jsonGroup = jsonGroup + memberObj(user1url) + ",\n" + memberObj(user2url) + "\n]}";

        String req = TestUtils.mapPathToReqUrl(baseUrl, "/Groups");

        HttpPost postGroup = new HttpPost(req);
        StringEntity body = new StringEntity(jsonGroup);
        postGroup.setEntity(body);

        ClassicHttpResponse resp = TestUtils.executeRequest(postGroup);
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

    @Test
    public void d_getGroupTest() throws Exception {
        logger.info("\tD. Get Group...");

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
                .as("Contains an extension value Tour Operations")
                .contains("Tour Guides");

        System.out.println("Entry retrieved:\n" + body);
    }

    @Test
    public void e_getUserTest() throws Exception {
        logger.info("\tE. Check Groups on User...");

        ClassicHttpResponse resp = TestUtils.executeGet(baseUrl, user1url);

        assert resp != null;
        assertThat(resp.getCode())
                .as("GET Group- Check for status response 200 OK")
                .isEqualTo(ScimResponse.ST_OK);

        String body = EntityUtils.toString(resp.getEntity());

        assertThat(body)
                .as("contains dynamic url for Tour Guides")
                .contains(grpUrl);

        assertThat(body)
                .as("has displayname TEST Tour Guides")
                .contains("\"TEST Tour Guides\"");

        assertThat(body)
                .as("still has original group US Employees")
                .contains("\"US Employees\"");

        System.out.println("Entry retrieved:\n" + body);
    }

    @Test
    public void f_getUserFilterTest() throws Exception {
        logger.info("\tF. Search filter for groups on User...");


        ClassicHttpResponse resp = TestUtils.executeGet(baseUrl, user2url + "?filter=" + URLEncoder.encode("groups.$ref eq " + grpUrl, StandardCharsets.UTF_8));

        assert resp != null;
        assertThat(resp.getCode())
                .as("GET Group- Check for status response 200 OK")
                .isEqualTo(ScimResponse.ST_OK);

        String body = EntityUtils.toString(resp.getEntity());

        assertThat(body)
                .as("contains dynamic url for Tour Guides")
                .contains(grpUrl);

        assertThat(body)
                .as("has displayname Tour Guides")
                .contains("\"Tour Guides\"");

        System.out.println("Entry retrieved:\n" + body);
    }


}
