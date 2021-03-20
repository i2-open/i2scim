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

package com.independentid.scim.test.devops;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;

public class ScimDevOpsTestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> cmap = new HashMap<>(Map.of(
                "scim.prov.mongo.dbname", "healthTestSCIM",
                "scim.prov.mongo.uri", "mongodb://localhost:27017",

                "quarkus.http.test-port", "0",
                "quarkus.log.min-level","DEBUG",
                "quarkus.log.category.\"com.independentid.scim.test\".level", "INFO",

                "scim.security.enable", "true",
                "scim.security.authen.basic", "true",
                "scim.security.authen.jwt", "true",
                "scim.security.acis","classpath:/schema/aciSecurityTest.json"
        ));
        cmap.putAll(Map.of(
                "scim.event.enable","false"

        ));
        return cmap;


    }

    @Override
    public String getConfigProfile() {
        return "ScimDevOpsTestProfile";
    }
}
