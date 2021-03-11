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

package com.independentid.scim.test.memory;

import com.independentid.scim.backend.memory.MemoryProvider;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;

public class ScimMemoryTestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String,String> cmap = new HashMap<>(  Map.of(
                "scim.schema.path","classpath:/schema/scimSchemaTest.json",

                "scim.prov.providerClass", MemoryProvider.class.getName(),
                "scim.prov.memory.maxbackups", "2",
                "scim.prov.memory.backup.mins","1",

                "quarkus.http.test-port","0",
                "quarkus.log.min-level","DEBUG",
                "logging.level.com.independentid.scim","DEBUG",
                "quarkus.log.category.\"com.independentid.scim\".level","DEBUG"
        ));
        cmap.putAll(Map.of(
                "scim.security.enable", "false",
                "scim.event.enable","false"

        ));
        return cmap;
    }

    @Override
    public String getConfigProfile() {
        return "ScimMemoryTestProfile";
    }
}
