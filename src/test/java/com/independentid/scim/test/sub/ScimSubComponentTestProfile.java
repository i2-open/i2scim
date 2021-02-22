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

package com.independentid.scim.test.sub;

import com.independentid.scim.backend.memory.MemoryProvider;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

public class ScimSubComponentTestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "scim.test.configOnly","true",
                "scim.json.pretty","true",

                "scim.security.enable", "false",
                "logging.level.com.independentid.scim","DEBUG",
                "quarkus.log.category.\"com.independentid.scim\".level","DEBUG",
                "scim.prov.providerClass", MemoryProvider.class.getName(),
                "scim.prov.persist.schema","false"
        );
    }

    @Override
    public String getConfigProfile() {
        return "ScimSubComponentConfigTestProfile";
    }
}
