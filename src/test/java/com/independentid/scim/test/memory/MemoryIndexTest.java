/*
 * Copyright (c) 2021.
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

package com.independentid.scim.test.memory;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.backend.BackendException;
import com.independentid.scim.backend.BackendHandler;
import com.independentid.scim.backend.memory.IndexResourceType;
import com.independentid.scim.backend.memory.MemoryProvider;
import com.independentid.scim.backend.memory.ValResMap;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.Filter;
import com.independentid.scim.protocol.ListResponse;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.protocol.ScimResponse;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.resource.StringValue;
import com.independentid.scim.resource.Value;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.serializer.JsonUtil;
import com.independentid.scim.test.misc.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.text.ParseException;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@QuarkusTest
@TestProfile(ScimMemoryTestProfile.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class MemoryIndexTest {

    private static final Logger logger = LoggerFactory.getLogger(MemoryIndexTest.class);

    private static final String testUserFile1 = "classpath:/schema/TestUser-bjensen.json";
    private static final String testUserFile2 = "classpath:/schema/TestUser-jsmith.json";

    static Attribute username,emailvalue;
    static ScimResource user1,user2;

    @Inject
    @Resource(name="SchemaMgr")
    SchemaManager smgr;

    @Inject
    BackendHandler handler;

    @Inject
    MemoryProvider provider;

    @Inject
    TestUtils testUtils;

    @Test
    public void a_initializeTest() {
        logger.info("==========   Memory Index Tests ==========");
        try {
            testUtils.resetProvider();
        } catch (ScimException | BackendException | IOException e) {
            org.junit.jupiter.api.Assertions.fail("Failed to reset provider: "+e.getMessage());
        }
        handler.getProvider();  // this should start initialization.

        assertThat(provider).isNotNull();

        assertThat(provider.ready())
                .as("Memory Provider is ready")
                .isTrue();
        logger.info("\tMemory provider running.");
    }

    @Test
    public void b_checkIndex() {
        Map<String, IndexResourceType> imap = provider.getIndexes();
        IndexResourceType userIndex = imap.get("Users");

        assertThat(userIndex)
                .as("Located Users Index")
                .isNotNull();

        username = smgr.findAttribute("User:userName",null);
        emailvalue = smgr.findAttribute("User:emails.value",null);

        assertThat(username).isNotNull();
        assertThat(emailvalue).isNotNull();

        assertThat(userIndex.getPresAttrs().contains(username))
                .as ("Username is configured with presence index")
                .isTrue();
        assertThat(userIndex.getExactAttrs().contains(username))
                .as ("Username is configured with exact index")
                .isTrue();
        assertThat(userIndex.getOrderAttrs().contains(username))
                .as ("Username is configured with ordered index")
                .isTrue();
        assertThat(userIndex.getSubstrAttrs().contains(username))
                .as ("Username is configured with substring index")
                .isTrue();
    }

    @Test
    public void c_indexResource() throws IOException, ScimException, ParseException {
        InputStream userStream = ConfigMgr.findClassLoaderResource(testUserFile1);
        assert userStream != null;
        JsonNode node = JsonUtil.getJsonTree(userStream);
        //,user2;
        user1 = new ScimResource(smgr, node, "Users");

        InputStream userStream2 = ConfigMgr.findClassLoaderResource(testUserFile2);
        node = JsonUtil.getJsonTree(userStream2);
        user2 = new ScimResource(smgr, node, "Users");

        Map<String, IndexResourceType> imap = provider.getIndexes();
        IndexResourceType userIndex = imap.get("Users");
        userIndex.indexResource(user1);
        userIndex.indexResource(user2);

        Map<Value, ValResMap> map = userIndex.getOrderIndex(username);
        assertThat(map)
                .as("Username map is not null")
                .isNotNull();
        StringValue uval = (StringValue) user1.getValue(username);
        ValResMap vrm = map.get(uval);
        assertThat(vrm)
                .as("Value resource map exists for "+uval.value)
                .isNotNull();
        assertThat(vrm.containsId(user1.getId()))
                .as("Index has user 1 id of "+user1.getId())
                .isTrue();
        Value u2val = user2.getValue(username);
        assertThat(vrm.containsId(user2.getId()))
                .as("Index of "+uval.value+ " does not contain user2 id")
                .isFalse();
        ValResMap vrm2 = map.get(u2val);
        assertThat(vrm)
                .as("Confirm users indexed separately (two vrms)")
                .isNotEqualTo(vrm2);

        Map<String, ValResMap> smap = userIndex.getSubstrIndex(username);
        ValResMap svrm1 = smap.get(uval.reverseValue());
        assertThat(svrm1)
                .as("Substring val resource map exists for "+uval.value)
                .isNotNull();
        assertThat(svrm1.containsId(user1.getId()))
                .as("Substring index has user 1 id of "+user1.getId())
                .isTrue();

        StringValue testVal = new StringValue(username,"testUser");
        assertThat(userIndex.checkUniqueAttr(testVal))
                .as("No conflict exists for username 'testUser'")
                .isFalse();
        assertThat(userIndex.checkUniqueAttr(uval))
                .as(uval.toString()+" should be conflicted")
                .isTrue();
    }

    @Test
    public void d_deIndexResource() {
        Map<String, IndexResourceType> imap = provider.getIndexes();
        IndexResourceType userIndex = imap.get("Users");
        userIndex.deIndexResource(user1);
        userIndex.deIndexResource(user2);

        Map<Value, ValResMap> map = userIndex.getOrderIndex(username);
        assertThat(map)
                .as("Username map is not null")
                .isNotNull();
        StringValue uval = (StringValue) user1.getValue(username);
        ValResMap vrm = map.get(uval);
        assertThat(vrm)
                .as("Value resource map exists for "+uval.value)
                .isNull();

        Map<String, ValResMap> smap = userIndex.getSubstrIndex(username);
        ValResMap svrm1 = smap.get(uval.reverseValue());
        assertThat(svrm1)
                .as("Substring val resource map exists for "+uval.value)
                .isNull();

        assertThat(userIndex.checkUniqueAttr(uval))
                .as(uval.toString()+" should not be conflicted post de-index")
                .isFalse();
    }

    @Test
    public void e_loadAndTestSearch() throws ScimException, BackendException, IOException {
        RequestCtx ctx = new RequestCtx("/Users",null,null,smgr);
        ScimResponse resp = provider.create(ctx,user1);
        Assertions.assertThat (resp.getStatus())
                .as("Check user created success")
                .isEqualTo(ScimResponse.ST_CREATED);

        // Attempt duplicate
        ctx = new RequestCtx("/Users",null,null,smgr);
        resp = provider.create(ctx, user1);

        Assertions.assertThat(resp.getStatus())
                .as("Confirm error 400 occurred (uniqueness)")
                .isEqualTo(ScimResponse.ST_BAD_REQUEST);
        String body = getResponseBody(resp,ctx);
        Assertions.assertThat(body)
                .as("Is a uniqueness error")
                .contains(ScimResponse.ERR_TYPE_UNIQUENESS);

        ctx = new RequestCtx("/Users",null,null,smgr);
        resp = provider.create(ctx,user2);
        Assertions.assertThat (resp.getStatus())
                .as("Check user created success")
                .isEqualTo(ScimResponse.ST_CREATED);

        // Check candidates
        ctx = new RequestCtx("/Users",null,null,smgr);
        Map<String, IndexResourceType> imap = provider.getIndexes();
        IndexResourceType userIndex = imap.get("Users");
        Filter filter = Filter.parseFilter("username eq bjensen@example.com",ctx);
        Set<String> candidates = userIndex.getPotentialMatches(filter);
        assertThat(candidates.size())
                .as("Should be 1 candidate")
                .isEqualTo(1);
        assertThat(candidates.contains("2819c223-7f76-453a-919d-413861904646")).isTrue();

        // Now, test searching unindexed attr.  Should return all entries (size = 2)
        filter = Filter.parseFilter("nickname eq \"Babs\"",ctx);
        candidates = userIndex.getPotentialMatches(filter);
        assertThat(candidates.size())
                .as("Should be "+provider.getCount()+" candidates")
                .isEqualTo(2);

        filter = Filter.parseFilter("username eq bjensen@example.com and nickname eq \"Babs\"",ctx);
        candidates = userIndex.getPotentialMatches(filter);
        assertThat(candidates.size())
                .as("Should be 1 candidate due to and clause and indexed username")
                .isEqualTo(1);
        assertThat(candidates.contains("2819c223-7f76-453a-919d-413861904646")).isTrue();

        filter = Filter.parseFilter("username eq bjensen@example.com or username eq jsmith@example.com",ctx);
        candidates = userIndex.getPotentialMatches(filter);
        assertThat(candidates.size())
                .as("Should be 2 candidate due to or clause and indexed username")
                .isEqualTo(2);
        assertThat(candidates.contains("2819c223-7f76-453a-919d-413861904646")).isTrue();

        filter = Filter.parseFilter("username lt test",ctx);
        candidates = userIndex.getPotentialMatches(filter);
        assertThat(candidates.size())
                .as("Should be 2 candidate due LT clause")
                .isEqualTo(2);
        assertThat(candidates.contains("2819c223-7f76-453a-919d-413861904646")).isTrue();

        filter = Filter.parseFilter("username gt bjensen@example.com",ctx);
        candidates = userIndex.getPotentialMatches(filter);
        assertThat(candidates.size())
                .as("Should be 1 candidate due GT clause")
                .isEqualTo(1);
        assertThat(candidates.contains("2819c223-7f76-453a-919d-413861904646")).isFalse();

        filter = Filter.parseFilter("username ge bjensen@example.com",ctx);
        candidates = userIndex.getPotentialMatches(filter);
        assertThat(candidates.size())
                .as("Should be 1 candidate due GT clause")
                .isEqualTo(2);
        assertThat(candidates.contains("2819c223-7f76-453a-919d-413861904646")).isTrue();
    }

    @Test
    public void f_searchtest() throws ScimException {
        RequestCtx ctx = new RequestCtx("/Users",null,"username gt bjensen@example.com",smgr);
        ScimResponse resp = provider.get(ctx);
        assertThat(resp).isInstanceOf(ListResponse.class);
        ListResponse lresp = (ListResponse) resp;
        assertThat(lresp.getSize())
                .isEqualTo(1);

        ctx = new RequestCtx("/Users",null,"username eq bjensen@example.com",smgr);
        resp = provider.get(ctx);
        assertThat(resp).isInstanceOf(ListResponse.class);
        lresp = (ListResponse) resp;
        assertThat(lresp.getSize())
                .isEqualTo(1);

        //This is an unindexed search
        ctx = new RequestCtx("/Users",null,"nickname eq Jim",smgr);
        resp = provider.get(ctx);
        assertThat(resp).isInstanceOf(ListResponse.class);
        lresp = (ListResponse) resp;
        assertThat(lresp.getSize())
                .isEqualTo(1);
    }

    public String getResponseBody(ScimResponse resp,RequestCtx ctx) throws IOException {
        StringWriter respWriter = new StringWriter();
        JsonGenerator gen = JsonUtil.getGenerator(respWriter,false);
        resp.serialize(gen,ctx);
        gen.close();
        return respWriter.toString();
    }
}
