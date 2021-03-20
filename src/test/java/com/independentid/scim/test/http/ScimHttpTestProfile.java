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

package com.independentid.scim.test.http;

import com.independentid.scim.backend.memory.MemoryProvider;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;

public class ScimHttpTestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> cmap = new HashMap<>(Map.of(
                "scim.schema.path","/schema/scimSchemaTest.json",  //this test schema changes some default returns for testing

                "scim.prov.providerClass", MemoryProvider.class.getName(),
                "scim.prov.memory.maxbackups", "2",
                "scim.prov.memory.backup.mins","5",

                //"scim.prov.mongo.dbname", "testHttpSCIM",
                //"scim.prov.mongo.uri","mongodb://localhost:27017",
                //"scim.prov.providerClass","com.independentid.scim.backend.mongo.MongoProvider",

                "quarkus.http.test-port","0",
                "quarkus.log.min-level","DEBUG",
                "logging.level.com.independentid.scim","INFO",
                "quarkus.log.category.\"com.independentid.scim.test\".level","INFO"
        ));
        cmap.putAll(Map.of(
                "scim.json.pretty","true",
                "scim.security.enable", "false",
                "scim.event.enable","false"
        ));
        return cmap;
    }

    @Override
    public String getConfigProfile() {
        return "ScimHttpTestProfile";
    }
}
