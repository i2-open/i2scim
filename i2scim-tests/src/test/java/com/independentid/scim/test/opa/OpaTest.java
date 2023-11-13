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
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.test.misc.TestUtils;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.annotation.Resource;
import jakarta.inject.Inject;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
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

    @TestHTTPResource("/")
    URL rootUrl;

    @Inject
    @Resource(name = "ConfigMgr")
    ConfigMgr config;

    @Inject
    TestUtils testUtils;

    @ConfigProperty(name = "scim.security.root.username", defaultValue = "admin")
    String rootUser;

    @ConfigProperty(name = "scim.security.root.password", defaultValue = "admin")
    String rootPassword;

    public static String bearer;

    static i2scimClient client = null;

    static Process opaServer = null;


    /**
     * This test validates that all 3 initializer constructors work. The first connection is retained for transaction
     * tests.
     */
    @Test
    public void a_InitializeClientTest() {
        try {
            logger.info("Starting OPA Server for testing...");

            logger.info("  Opa URL = "+config.getOpaUrl());

            String opaCmd = "sh ./opa/opa_start.sh";
            //String opaCmd = "ls";
            opaServer = Runtime.getRuntime()
                    .exec(opaCmd);
            if (opaServer.isAlive()) {
                // Give time for OPA to start!
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException ignored) {
                }
                logger.info("OPA Server started.");
                return;
            }
            // Otherwise print diagnostics...

            InputStream stream = opaServer.getErrorStream();
            byte[] errBytes = stream.readAllBytes();
            if (errBytes.length > 0) {
                String output = new String(errBytes);
                logger.error("OPA Errors at startup:\n"+output);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
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

        if (opaServer != null)
            opaServer.destroy();
    }


}
