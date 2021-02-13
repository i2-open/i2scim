/*
 * Copyright (c) 2020.
 *
 * Confidential and Proprietary
 *
 * This unpublished source code may not be distributed outside
 * “Independent Identity Org”. without express written permission of
 * Phillip Hunt.
 *
 * People at companies that have signed necessary non-disclosure
 * agreements may only distribute to others in the company that are
 * bound by the same confidentiality agreement and distribution is
 * subject to the terms of such agreement.
 */

package com.independentid.scim.test.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.backend.BackendException;
import com.independentid.scim.backend.BackendHandler;
import com.independentid.scim.backend.mongo.MongoProvider;
import com.independentid.scim.core.ConfigMgr;
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
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import javax.inject.Inject;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.ParseException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@QuarkusTest
@TestProfile(ScimMongoTestProfile.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class MongoFilterMapTest {

    static final Logger logger = LoggerFactory.getLogger(MongoFilterMapTest.class);

    @Inject
    @Resource(name = "SchemaMgr")
    SchemaManager smgr;

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

    int[] searchResultCnt = new int[] {
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

            1,1,1,2
    };


    private static final String testUserFile1 = "classpath:/schema/TestUser-bjensen.json";
    private static final String testUserFile2 = "classpath:/schema/TestUser-jsmith.json";

    private static MongoClient mclient = null;

    @Inject
    BackendHandler handler;

    @ConfigProperty(name = "scim.mongodb.uri", defaultValue = "mongodb://localhost:27017")
    String dbUrl;
    @ConfigProperty(name = "scim.mongodb.dbname", defaultValue = "testSCIM")
    String scimDbName;

    static MongoProvider mp = null;

    static ScimResource user1, user2;
    static String user1loc, user2loc;

    @Test
    public void a_mongoProviderInit() throws ScimException, IOException, ParseException {
        logger.info("========== MongoProvider Filter Test ==========");

        if (mclient == null)
            mclient = MongoClients.create(dbUrl);


        MongoDatabase scimDb = mclient.getDatabase(scimDbName);

        scimDb.drop();
        try {
            handler.getProvider().syncConfig(smgr.getSchemas(), smgr.getResourceTypes());
        } catch (IOException | BackendException e) {
            fail("Failed to initialize test Mongo DB: " + scimDbName);
        }

        try {
            mp = (MongoProvider) handler.getProvider();
        } catch (BackendException e) {
            Assertions.fail("Exception occured getting MongoProvider: " + e.getLocalizedMessage());
        }

        assertThat(mp).isNotNull();

        assertThat(mp.ready()).isTrue();
        assertThat(mp.getMongoDbName()).isEqualTo(scimDbName);

        logger.debug("\tLoading sample data.");

        Attribute loginCnt = smgr.findAttribute("loginCnt",null);
        Attribute loginStrength = smgr.findAttribute("loginStrength",null);

        assertThat(loginCnt)
                .as("Check test schema loaded")
                .isNotNull();
        assertThat(loginStrength)
                .as("Check test schema loaded")
                .isNotNull();

        File user1File = ConfigMgr.findClassLoaderResource(testUserFile1);

        assert user1File != null;
        InputStream userStream = new FileInputStream(user1File);
        JsonNode node = JsonUtil.getJsonTree(userStream);
        user1 = new ScimResource(smgr, node, "Users");
        IntegerValue lcnt = new IntegerValue(loginCnt,1234);
        DecimalValue lstr = new DecimalValue(loginStrength,BigDecimal.valueOf(3.4));
        user1.addValue(lcnt);
        user1.addValue(lstr);

        userStream.close();

        File user2File = ConfigMgr.findClassLoaderResource(testUserFile2);
        assert user2File != null;
        userStream = new FileInputStream(user2File);
        node = JsonUtil.getJsonTree(userStream);
        user2 = new ScimResource(smgr, node, "Users");
        lcnt = new IntegerValue(loginCnt,1);
        lstr = new DecimalValue(loginStrength,BigDecimal.valueOf(1.00));
        user2.addValue(lcnt);
        user2.addValue(lstr);
        RequestCtx ctx = new RequestCtx("/Users", null, null, smgr);
        ScimResponse resp = mp.create(ctx, user1);
        assertThat(resp.getStatus())
                .as("Check user1 created")
                .isEqualTo(ScimResponse.ST_CREATED);
        user1loc = resp.getLocation();
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
    public void b_testFilterResource() throws BackendException {
        logger.info("Testing filter match against specific resources");
        for (int i = 0; i < testArray.length; i++) {
            logger.debug("");
            logger.info("Filter (" + i + "):\t" + testArray[i][1]);
            RequestCtx ctx1, ctx2;
            try {
                ctx1 = new RequestCtx(user1loc, null, testArray[i][1], smgr);
                logger.debug("Parsed filt:\t"+ctx1.getFilter().toString());
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
    public void c_testFilterMongoDb() throws BackendException {
        logger.info("Testing filter matches against all Users");
        for (int i = 0; i < testArray.length; i++) {
            logger.debug("");
            logger.info("Filter (" + i + "):\t" + testArray[i][1]);
            RequestCtx ctx;
            try {
                ctx = new RequestCtx("Users", null, testArray[i][1], smgr);
                logger.debug("Parsed filt:\t"+ctx.getFilter().toString());
                ScimResponse resp = mp.get(ctx);

                assertThat(resp)
                        .as("Check for ListResponse")
                        .isInstanceOf(ListResponse.class);
                ListResponse lr = (ListResponse) resp;
                assertThat(lr.getSize())
                        .as("Filter#"+i+" has "+ searchResultCnt[i]+" matches.")
                        .isEqualTo(searchResultCnt[i]);

            } catch (ScimException e) {
                fail("Failed while generating RequestCtx and Filter: " + e.getMessage(), e);
            }

        }

    }

}
