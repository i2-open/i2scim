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

package com.independentid.scim.test.auth;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;

public class ScimAuthTestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> cmap = new HashMap<>(Map.of(
                "scim.mongodb.test", "true",
                "scim.mongodb.dbname", "secTestSCIM",
                "scim.mongodb.uri", "mongodb://localhost:27017",

                "scim.security.enable", "true",

                "quarkus.http.test-port", "0",
                "quarkus.log.category.\"com.independentid.scim\".level", "DEBUG",
                "quarkus.http.auth.basic", "true",
                "scim.security.authen.basic", "true",
                "scim.security.authen.jwt", "true",
                "scim.security.acis.path","classpath:/schema/aciSecurityTest.json"
        ));
        cmap.putAll(Map.of(


        ));
        return cmap;


    }

    @Override
    public String getConfigProfile() {
        return "ScimAuthenTestProfile";
    }
}
