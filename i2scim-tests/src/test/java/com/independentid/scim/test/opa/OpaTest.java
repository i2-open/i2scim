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

package com.independentid.scim.test.opa;

import com.independentid.scim.backend.BackendException;
import com.independentid.scim.client.i2scimClient;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.test.misc.TestUtils;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@QuarkusTest
@TestProfile(OpaTestProfile.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class OpaTest {
    private final static Logger logger = LoggerFactory.getLogger(OpaTest.class);

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

    public static String bearer;

    static i2scimClient client = null;

    /**
     * This test validates that all 3 initializer constructors work. The first connection is retained for transaction
     * tests.
     */
    @Test
    public void a_InitializeClientTest() {
        bearer = testUtils.getAuthToken("admin",true);

        logger.info("A. Client Initialization Tests");

        logger.info("\t0. Resetting database");
        try {
            testUtils.resetProvider(true);
        } catch (ScimException | BackendException | IOException e) {
            fail("Exception occurred during database reset: " + e.getMessage(), e);
        }


        UsernamePasswordCredentials cred = new UsernamePasswordCredentials(rootUser, rootPassword);
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
            client = test;


        } catch (ScimException e) {
            fail("Failed with SCIM exception: " + e.getMessage(), e);
        } catch (IOException e) {
            fail("Failed with IO exception: " + e.getMessage(), e);
        } catch (URISyntaxException e) {
            fail("Unexpected URI exception: " + e.getMessage(), e);
        }

    }


}
