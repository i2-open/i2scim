/*
 * Copyright 2026.  Independent Identity Incorporated
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
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.independentid.scim.backend.memory.IndexResourceType;
import com.independentid.scim.backend.memory.MemoryProvider;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.protocol.Filter;
import com.independentid.scim.protocol.JsonPatchRequest;
import com.independentid.scim.protocol.ListResponse;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.protocol.ScimResponse;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.resource.StringValue;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.serializer.JsonUtil;
import com.independentid.scim.test.misc.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.annotation.Resource;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

/**
 * Issue #110: a failure part-way through a memory-provider modify (PUT/PATCH) must leave every index exactly as it
 * was before the request. Failures are injected into the Users {@link IndexResourceType} via a Mockito spy that shares
 * the real index maps.
 */
@QuarkusTest
@TestProfile(ScimMemoryTestProfile.class)
public class MemoryModifyAtomicityTest {

    private static final Logger logger = LoggerFactory.getLogger(MemoryModifyAtomicityTest.class);

    private static final String TEST_USER = "classpath:/schema/TestUser-bjensen.json";
    private static final String ORIG_USERNAME = "bjensen@example.com";
    private static final String ORIG_EMAIL = "babs@jensen.org";
    private static final String NEW_USERNAME = "changed@example.com";
    private static final String NEW_EMAIL = "changed@jensen.org";

    @Inject
    @Resource(name = "SchemaMgr")
    SchemaManager smgr;

    @Inject
    MemoryProvider provider;

    @Inject
    TestUtils testUtils;

    IndexResourceType realIndex;
    String id;

    @BeforeEach
    public void setUp() throws Exception {
        testUtils.resetProvider(true);
        realIndex = provider.getIndexes().get("Users");

        InputStream userStream = ConfigMgr.findClassLoaderResource(TEST_USER);
        assert userStream != null;
        ScimResource user = new ScimResource(smgr, JsonUtil.getJsonTree(userStream), "Users");
        ScimResponse resp = provider.create(new RequestCtx("/Users", null, null, smgr), user);
        assertThat(resp.getStatus()).as("test user created").isEqualTo(ScimResponse.ST_CREATED);
        id = user.getId();
    }

    @AfterEach
    public void tearDown() {
        if (realIndex != null)
            provider.getIndexes().put("Users", realIndex);
    }

    /**
     * Replaces the Users index with a spy (sharing the real index maps) whose first matching call runs {@code before}
     * and then throws, simulating a runtime failure such as the #105 ClassCastException during index comparison.
     */
    private void injectFailure(Consumer<IndexResourceType> stubber) {
        IndexResourceType spy = Mockito.spy(realIndex);
        stubber.accept(spy);
        provider.getIndexes().put("Users", spy);
    }

    private static Answer<Void> failOnce(boolean runRealFirst) {
        AtomicBoolean fired = new AtomicBoolean(false);
        return invocation -> {
            if (fired.compareAndSet(false, true)) {
                if (runRealFirst)
                    invocation.callRealMethod();
                throw new ClassCastException("Injected index failure");
            }
            invocation.callRealMethod();
            return null;
        };
    }

    private JsonPatchRequest renamePatch(RequestCtx ctx) throws Exception {
        JsonNode node = JsonUtil.getJsonTree("{\"schemas\":[\"urn:ietf:params:scim:api:messages:2.0:PatchOp\"],"
                + "\"Operations\":["
                + "{\"op\":\"replace\",\"path\":\"userName\",\"value\":\"" + NEW_USERNAME + "\"},"
                + "{\"op\":\"replace\",\"path\":\"emails\",\"value\":[{\"value\":\"" + NEW_EMAIL + "\",\"type\":\"home\"}]}"
                + "]}");
        return new JsonPatchRequest(node, ctx);
    }

    private void patchExpectingFailure() throws Exception {
        RequestCtx ctx = new RequestCtx("Users", id, null, smgr);
        JsonPatchRequest req = renamePatch(ctx);
        assertThatThrownBy(() -> provider.patch(ctx, req))
                .as("injected failure propagates")
                .isInstanceOf(ClassCastException.class);
    }

    private void putExpectingFailure() throws Exception {
        RequestCtx readCtx = new RequestCtx("Users", id, null, smgr);
        ScimResource current = provider.getResource(readCtx);
        ObjectNode body = (ObjectNode) current.toJsonNode(readCtx);
        body.put("userName", NEW_USERNAME);
        body.set("emails", JsonUtil.getJsonTree("[{\"value\":\"" + NEW_EMAIL + "\",\"type\":\"home\"}]"));
        ScimResource replacement = new ScimResource(smgr, body, "Users");

        RequestCtx ctx = new RequestCtx("Users", id, null, smgr);
        assertThatThrownBy(() -> provider.put(ctx, replacement))
                .as("injected failure propagates")
                .isInstanceOf(ClassCastException.class);
    }

    private Set<String> search(String filter) throws Exception {
        RequestCtx ctx = new RequestCtx("/Users", null, filter, smgr);
        return realIndex.getPotentialMatches(Filter.parseFilter(filter, ctx));
    }

    private int count(String filter) throws Exception {
        ScimResponse resp = provider.get(new RequestCtx("/Users", null, filter, smgr));
        assertThat(resp).isInstanceOf(ListResponse.class);
        return ((ListResponse) resp).getSize();
    }

    private void assertOriginalIntact() throws Exception {
        provider.getIndexes().put("Users", realIndex);

        ScimResource stored = provider.getResource(new RequestCtx("Users", id, null, smgr));
        assertThat(stored).as("resource still findable by id").isNotNull();
        Attribute userName = smgr.findAttribute("User:userName", null);
        assertThat(stored.getValue(userName).toString()).as("stored userName unchanged").isEqualTo(ORIG_USERNAME);

        assertThat(search("userName eq \"" + ORIG_USERNAME + "\"")).as("indexed by original userName").containsExactly(id);
        assertThat(search("emails.value eq \"" + ORIG_EMAIL + "\"")).as("indexed by original email").containsExactly(id);
        assertThat(search("userName pr")).as("presence index").containsExactly(id);
        assertThat(search("userName sw \"bjensen\"")).as("substring index").containsExactly(id);
        assertThat(search("userName eq \"" + NEW_USERNAME + "\"")).as("new userName not indexed").isEmpty();
        assertThat(search("emails.value eq \"" + NEW_EMAIL + "\"")).as("new email not indexed").isEmpty();

        assertThat(count("userName eq \"" + ORIG_USERNAME + "\"")).as("search finds original userName").isEqualTo(1);
        assertThat(count("userName eq \"" + NEW_USERNAME + "\"")).as("search does not find new userName").isEqualTo(0);

        assertThat(realIndex.checkUniqueAttr(new StringValue(userName, NEW_USERNAME)))
                .as("new userName does not hold a uniqueness slot").isFalse();
        assertThat(realIndex.checkUniqueAttr(new StringValue(userName, ORIG_USERNAME)))
                .as("original userName still holds its uniqueness slot").isTrue();
    }

    @Test
    public void patchFailureWhileIndexingModifiedResourceRestoresIndex() throws Exception {
        logger.info("Injecting failure after the modified resource is (partly) indexed during PATCH");
        injectFailure(spy -> Mockito.doAnswer(failOnce(true)).when(spy).indexResource(any()));
        patchExpectingFailure();
        assertOriginalIntact();
    }

    @Test
    public void patchFailureBeforeIndexingModifiedResourceRestoresIndex() throws Exception {
        injectFailure(spy -> Mockito.doAnswer(failOnce(false)).when(spy).indexResource(any()));
        patchExpectingFailure();
        assertOriginalIntact();
    }

    @Test
    public void patchFailureWhileDeIndexingOriginalRestoresIndex() throws Exception {
        injectFailure(spy -> Mockito.doAnswer(failOnce(true)).when(spy).deIndexResource(any()));
        patchExpectingFailure();
        assertOriginalIntact();
    }

    @Test
    public void putFailureWhileIndexingModifiedResourceRestoresIndex() throws Exception {
        injectFailure(spy -> Mockito.doAnswer(failOnce(true)).when(spy).indexResource(any()));
        putExpectingFailure();
        assertOriginalIntact();
    }

    @Test
    public void successfulPatchAfterRecoveryStillWorks() throws Exception {
        injectFailure(spy -> Mockito.doAnswer(failOnce(true)).when(spy).indexResource(any()));
        patchExpectingFailure();
        provider.getIndexes().put("Users", realIndex);

        RequestCtx ctx = new RequestCtx("Users", id, null, smgr);
        ScimResponse resp = provider.patch(ctx, renamePatch(ctx));
        assertThat(resp.getStatus()).as("retry succeeds").isEqualTo(ScimResponse.ST_OK);
        assertThat(search("userName eq \"" + NEW_USERNAME + "\"")).containsExactly(id);
        assertThat(search("userName eq \"" + ORIG_USERNAME + "\"")).isEmpty();
        assertThat(search("emails.value eq \"" + ORIG_EMAIL + "\"")).isEmpty();
    }
}
