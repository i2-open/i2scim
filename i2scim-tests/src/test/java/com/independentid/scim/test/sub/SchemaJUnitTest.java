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

package com.independentid.scim.test.sub;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.independentid.scim.backend.BackendException;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.resource.ScimResourceBuilder;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.serializer.JsonUtil;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

@QuarkusTest
@TestProfile(ScimSubComponentTestProfile.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class SchemaJUnitTest {
    private final static Logger logger = LoggerFactory.getLogger(SchemaJUnitTest.class);
    static SchemaManager schemaManager;

    final static String schemaLoc = "/schema/scimSchema.json";
    final static String typeLoc = "/schema/resourceTypes.json";

    @BeforeAll
    public static void init() throws ScimException, IOException {
        JsonUtil.getMapper();
    }

    @Test
    public void testScimResource() throws ScimException, IOException, BackendException {
        ObjectMapper map = JsonUtil.getMapper();
        InputStream stream = ConfigMgr.findClassLoaderResource(schemaLoc);
        int availbytes = stream.available();
        JsonNode node = map.readTree(stream);
        schemaManager = new SchemaManager(schemaLoc,typeLoc);

        logger.info("Schema manager initialized");

        ScimResourceBuilder builder = ScimResource.builder(schemaManager,"User");
        builder.withStringAttribute("username","testUser");

        ScimResource res = builder.build();
        String body = res.toJsonString();

    }
}
