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

package com.independentid.scim.test.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.backend.BackendException;
import com.independentid.scim.backend.memory.MemoryProvider;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.InjectionManager;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.ListResponse;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.protocol.ScimResponse;
import com.independentid.scim.resource.DecimalValue;
import com.independentid.scim.resource.IntegerValue;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.serializer.JsonUtil;
import com.independentid.scim.test.misc.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.annotation.Resource;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.ParseException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@QuarkusTest
@TestProfile(ScimMemoryTestProfile.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class MemoryFilterMapTest {

    static final Logger logger = LoggerFactory.getLogger(MemoryFilterMapTest.class);

    @Inject
    @Resource(name = "SchemaMgr")
    SchemaManager smgr;

    @Inject
    TestUtils testUtils;

    /**
     * Test filters from RFC7644, figure 2
     */
    String[][] testArray = new String[][]{
            {"Users", "userName eq \"bjensen@example.com\""},  // basic attribute filter
            {"Users", "displayName eq \"Jim Smith\""},  //testing for parsing spaces
            {null, "urn:ietf:params:scim:schemas:core:2.0:User:name.familyName co \"ense\""},
            {"Users", "name.familyName co \"Smith\""},
            {"Users", "userName sw \"Jsmi\""},

            {"Users", "password eq \"t1meMa$heen\""},
            {"Users", "title pr"},
            {null, "undefinedattr eq bleh"},
            {"Users", "title pr and userType eq \"Employee\""},
            {"Users", "title pr or userType eq \"Intern\""},

            {"Users", "userType eq \"Employee\" and (emails co \"example.com\" or emails.value co \"jensen.org\")"},
            {"Users", "userType eq \"Employee\" and not ( emails co \"example.com(\" Or emails.value cO \"ex)ample.org\")"},
            {"Users", "userType ne \"Employee\" and not ( emails co example.com( Or emails.value cO ex)ample.org)"},
            {"Users", "userType eq \"Employee\" and (emails.type eq \"work\")"},
            {"Users", "userType eq \"Employee\" and emails[type eq \"work\" and value co \"@example.com\"]"},

            {"Users", "emails[type eq \"home\" and value co \"@jensen.org\"] or ims[type eq \"aim\" and value co \"jsmith345\"]"},
            {"Users", "userName eq bjensen@example.com and password eq t1meMa$heen"},
            {"Users", "meta.created lt 2020-01-01T00:00:00Z"},
            {"Users", "meta.resourceType eq User and meta.created eq 2010-01-23T04:56:22Z"},
            {"Users", "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:manager.$ref eq /Users/26118915-6090-4610-87e4-49d8ca9f808d"},

            {"Users", "x509certificates eq \"MIIDQzCCAqygAwIBAgICEAAwDQYJKoZIhvcNAQEFBQAwTjELMAkGA1UEBhMCVVMxEzARBgNVBAgMCkNhbGlmb3JuaWExFDASBgNVBAoMC2V4YW1wbGUuY29tMRQwEgYDVQQDDAtleGFtcGxlLmNvbTAeFw0xMTEwMjIwNjI0MzFaFw0xMjEwMDQwNjI0MzFaMH8xCzAJBgNVBAYTAlVTMRMwEQYDVQQIDApDYWxpZm9ybmlhMRQwEgYDVQQKDAtleGFtcGxlLmNvbTEhMB8GA1UEAwwYTXMuIEJhcmJhcmEgSiBKZW5zZW4gSUlJMSIwIAYJKoZIhvcNAQkBFhNiamVuc2VuQGV4YW1wbGUuY29tMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA7Kr+Dcds/JQ5GwejJFcBIP682X3xpjis56AK02bc1FLgzdLI8auoR+cC9/Vrh5t66HkQIOdA4unHh0AaZ4xL5PhVbXIPMB5vAPKpzz5iPSi8xO8SL7I7SDhcBVJhqVqr3HgllEG6UClDdHO7nkLuwXq8HcISKkbT5WFTVfFZzidPl8HZ7DhXkZIRtJwBweq4bvm3hM1Os7UQH05ZS6cVDgweKNwdLLrT51ikSQG3DYrl+ft781UQRIqxgwqCfXEuDiinPh0kkvIi5jivVu1Z9QiwlYEdRbLJ4zJQBmDrSGTMYn4lRc2HgHO4DqB/bnMVorHB0CC6AV1QoFK4GPe1LwIDAQABo3sweTAJBgNVHRMEAjAAMCwGCWCGSAGG+EIBDQQfFh1PcGVuU1NMIEdlbmVyYXRlZCBDZXJ0aWZpY2F0ZTAdBgNVHQ4EFgQU8pD0U0vsZIsaA16lL8En8bx0F/gwHwYDVR0jBBgwFoAUdGeKitcaF7gnzsNwDx708kqaVt0wDQYJKoZIhvcNAQEFBQADgYEAA81SsFnOdYJtNg5Tcq+/ByEDrBgnusx0jloUhByPMEVkoMZ3J7j1ZgI8rAbOkNngX8+pKfTiDz1RC4+dx8oU6Za+4NJXUjlL5CvV6BEYb1+QAEJwitTVvxB/A67g42/vzgAtoRUeDov1+GFiBZ+GNF/cAYKcMtGcrs2i97ZkJMo=\""},
            {"Users", "active eq true and username eq bjensen@example.com"},
            {"Users", "active pr"},
            {"Users", "ims eq someaimhandle"},
            {"Users", "loginCnt eq 1234"},

            {"Users", "loginCnt gt 0 and loginCnt lt 1000"},
            {"Users", "loginStrength gt 3.1"},
            {"Users", "loginStrength eq 1.0"},
            {"Users", "meta.lastModified gt 2020-01-01T00:00:00Z"}

    };

    boolean[][] matches = new boolean[][]{
            {true, false},
            {false, true},
            {true, false},
            {false, true},
            {false, true},

            {true, true},
            {true, true},
            {false, false},
            {true, true},
            {true, true},

            {true, true},
            {true, true},
            {false, false},
            {true, true},
            {true, true},

            {true, true},
            {true, false},
            {true, true},
            {true, true},
            {true, false},

            {true, true},
            {true, false},
            {true, true},
            {true, false},
            {true, false},

            {false, true},
            {true, false},
            {false, true},
            {true, true}

    };

    int[] searchResultCnt = new int[]{
            1,
            1,
            1,
            1,
            1,

            2,
            2,
            0,
            2,
            2,

            2,
            2,
            0,
            2,
            2,

            2,
            1,
            2,
            2,
            1,

            2,
            1,
            2,
            1,
            1,

            1, 1, 1, 2
    };


    private static final String testUserFile1 = "classpath:/schema/TestUser-bjensen.json";
    private static final String testUserFile2 = "classpath:/schema/TestUser-jsmith.json";


    static MemoryProvider mp = null;

    static ScimResource user1, user2;
    static String user1loc, user2loc;

    @Test
    public void a_memProviderInit() throws ScimException, IOException, ParseException, BackendException {
        logger.info("========== MemoryProvider Filter Test ==========");

        try {
            testUtils.resetProvider(true);
        } catch (ScimException | BackendException | IOException e) {
            Assertions.fail("Failed to reset provider: " + e.getMessage());
        }
        mp = (MemoryProvider) InjectionManager.getInstance().getProvider();

        logger.info("\t* Running initial persistance provider checks");
        assertThat(mp).as("MemoryProvider is defined.").isNotNull();

        assertThat(mp.ready()).as("MemoryProvider is ready").isTrue();

        logger.debug("\tLoading sample data.");

        Attribute loginCnt = smgr.findAttribute("loginCnt", null);
        Attribute loginStrength = smgr.findAttribute("loginStrength", null);

        assertThat(loginCnt)
                .as("Check test schema loaded")
                .isNotNull();
        assertThat(loginStrength)
                .as("Check test schema loaded")
                .isNotNull();

        InputStream userStream = ConfigMgr.findClassLoaderResource(testUserFile1);
        JsonNode node = JsonUtil.getJsonTree(userStream);
        user1 = new ScimResource(smgr, node, "Users");
        user1.setId(null);  // When adding directly, id must be NULL!
        IntegerValue lcnt = new IntegerValue(loginCnt, 1234);
        DecimalValue lstr = new DecimalValue(loginStrength, BigDecimal.valueOf(3.4));
        user1.addValue(lcnt);
        user1.addValue(lstr);

        userStream.close();

        userStream = ConfigMgr.findClassLoaderResource(testUserFile2);
        node = JsonUtil.getJsonTree(userStream);
        user2 = new ScimResource(smgr, node, "Users");
        user2.setId(null);  // When adding directly, id must be NULL!
        lcnt = new IntegerValue(loginCnt, 1);
        lstr = new DecimalValue(loginStrength, BigDecimal.valueOf(1.00));
        user2.addValue(lcnt);
        user2.addValue(lstr);
        RequestCtx ctx = new RequestCtx("/Users", null, null, smgr);

        ScimResponse resp = mp.create(ctx, user1);
        assertThat(resp.getStatus())
                .as("Check user1 created")
                .isEqualTo(ScimResponse.ST_CREATED);
        user1loc = resp.getLocation();

        // We should generate a new CTX with each request (new transid).
        ctx = new RequestCtx("/Users", null, null, smgr);
        resp = mp.create(ctx, user2);
        assertThat(resp.getStatus())
                .as("Check user2 created")
                .isEqualTo(ScimResponse.ST_CREATED);
        user2loc = resp.getLocation();
    }

    /**
     * This filter test applies the filter directly against a specific scim resource (after it is retrieved). This is
     * usually resolved at the SCIM layer rather than within Mongo.
     */
    @Test
    public void b_testFilterById() {
        logger.info("Testing filter match against specific resources");
        for (int i = 0; i < testArray.length; i++) {
            logger.debug("");
            logger.info("Filter (" + i + "):\t" + testArray[i][1]);
            RequestCtx ctx1, ctx2;
            try {
                ctx1 = new RequestCtx(user1loc, null, testArray[i][1], smgr);
                logger.debug("Parsed filt:\t" + ctx1.getFilter().toString());
                ctx2 = new RequestCtx(user2loc, null, testArray[i][1], smgr);
                ScimResource res1 = mp.getResource(ctx1);
                ScimResource res2 = mp.getResource(ctx2);

                if (matches[i][0])
                    assertThat(res1)
                            .as("Checking user 1 filter#" + i)
                            .isNotNull();
                else
                    assertThat(res1)
                            .as("Checking user 1 filter#" + i)
                            .isNull();
                if (matches[i][1])
                    assertThat(res2)
                            .as("Checking user 2 filter#" + i)
                            .isNotNull();
                else
                    assertThat(res2)
                            .as("Checking user 2 filter#" + i)
                            .isNull();

            } catch (ScimException e) {
                fail("Failed while generating RequestCtx and Filter: " + e.getMessage(), e);
            }

        }

    }

    /**
     * This test searches for multiple results at the container level. This means all filters are mapped to mongo and
     * appiled by Mongo
     */
    @Test
    public void c_testFilterByContainer() {
        logger.info("Testing filter matches against all Users");
        for (int i = 0; i < testArray.length; i++) {
            logger.debug("");
            logger.info("Filter (" + i + "):\t" + testArray[i][1]);
            RequestCtx ctx;
            try {
                ctx = new RequestCtx("Users", null, testArray[i][1], smgr);
                logger.debug("Parsed filt:\t" + ctx.getFilter().toString());
                ScimResponse resp = mp.get(ctx);

                assertThat(resp)
                        .as("Check for ListResponse")
                        .isInstanceOf(ListResponse.class);
                ListResponse lr = (ListResponse) resp;
                assertThat(lr.getSize())
                        .as("Filter#" + i + " has " + searchResultCnt[i] + " matches.")
                        .isEqualTo(searchResultCnt[i]);

            } catch (ScimException e) {
                fail("Failed while generating RequestCtx and Filter: " + e.getMessage(), e);
            }

        }

    }

}
