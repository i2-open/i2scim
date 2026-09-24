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

package com.independentid.scim.test.misc;

import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.backend.IScimProvider;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.protocol.ListResponse;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.protocol.ScimParams;
import com.independentid.scim.protocol.ScimResponse;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.serializer.JsonUtil;
import jakarta.annotation.Resource;
import jakarta.inject.Inject;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shared regression tests for issue #99: filtering on externalId must work regardless of filter casing or the casing
 * of the externalId attribute in a previously persisted common schema. Concrete subclasses supply only the
 * {@code @QuarkusTest}/{@code @TestProfile} wiring and the provider under test.
 */
@TestMethodOrder(MethodOrderer.MethodName.class)
public abstract class AbstractExternalIdFilterTest {

    static final Logger logger = LoggerFactory.getLogger(AbstractExternalIdFilterTest.class);

    private static final String testUserFile1 = "classpath:/schema/TestUser-bjensen.json";
    private static final String testUserFile2 = "classpath:/schema/TestUser-jsmith.json";
    protected static final String EXT_ID = "ext-99-bjensen";

    @Inject
    @Resource(name = "SchemaMgr")
    protected SchemaManager smgr;

    @Inject
    protected TestUtils testUtils;

    static IScimProvider mp = null;
    static String user1loc;

    /**
     * @return The backend provider under test, as selected by the subclass's test profile.
     */
    protected abstract IScimProvider provider();

    @Test
    public void a_init() throws Exception {
        logger.info("========== " + getClass().getSimpleName() + " ==========");
        testUtils.resetProvider(true);
        mp = provider();

        user1loc = createUser(testUserFile1, EXT_ID);
        createUser(testUserFile2, "ext-99-jsmith");
    }

    private String createUser(String file, String extId) throws Exception {
        JsonNode node;
        try (InputStream userStream = ConfigMgr.findClassLoaderResource(file)) {
            node = JsonUtil.getJsonTree(userStream);
        }
        ScimResource user = new ScimResource(smgr, node, "Users");
        user.setId(null);
        user.setExternalId(extId);
        RequestCtx ctx = new RequestCtx("/Users", null, null, smgr);
        ScimResponse resp = mp.create(ctx, user);
        assertThat(resp.getStatus())
                .as("Check user created")
                .isEqualTo(ScimResponse.ST_CREATED);
        return resp.getLocation();
    }

    private void assertSingleMatch(String filter) throws Exception {
        RequestCtx ctx = new RequestCtx("Users", null, filter, smgr);
        ScimResponse resp = mp.get(ctx);
        assertThat(resp)
                .as("Check for ListResponse")
                .isInstanceOf(ListResponse.class);
        ListResponse lr = (ListResponse) resp;
        assertThat(lr.getSize())
                .as("Filter '" + filter + "' returns exactly one user")
                .isEqualTo(1);
        ScimResource res = lr.getResults().get(0);
        assertThat(res.getExternalId()).isEqualTo(EXT_ID);
        assertThat(user1loc).endsWith(res.getId());
    }

    @Test
    public void b_commonSchemaNamesExternalIdCamelCase() {
        Attribute attr = smgr.findAttribute(ScimParams.ATTR_EXTID, null);
        assertThat(attr).isNotNull();
        assertThat(attr.getName())
                .as("Common schema names the attribute externalId (RFC7643 Sec 3.1)")
                .isEqualTo(ScimParams.ATTR_EXTID);
    }

    @Test
    public void c_filterExternalId() throws Exception {
        assertSingleMatch("externalId eq \"" + EXT_ID + "\"");
    }

    @Test
    public void d_filterExternalIdOtherCasing() throws Exception {
        assertSingleMatch("EXTERNALID eq \"" + EXT_ID + "\"");
        assertSingleMatch("externalid eq \"" + EXT_ID + "\"");
    }

    /**
     * Simulates a deployment whose persisted common schema still names the attribute "externalid" (pre-fix). The
     * provider must still match the stored externalId value (for Mongo, the filter mapping must query the canonical
     * stored field "externalId").
     */
    @Test
    public void e_filterExternalIdWithStaleSchemaName() throws Exception {
        Attribute attr = smgr.findAttribute(ScimParams.ATTR_EXTID, null);
        String origName = attr.getName();
        String origSchema = attr.getSchema();
        try {
            attr.setName("externalid");
            attr.setPath(origSchema, null);
            assertThat(attr.getRelativePath()).isEqualTo("externalid");

            assertSingleMatch("externalId eq \"" + EXT_ID + "\"");
        } finally {
            attr.setName(origName);
            attr.setPath(origSchema, null);
        }
        assertThat(attr.getRelativePath()).isEqualTo(ScimParams.ATTR_EXTID);
    }
}
