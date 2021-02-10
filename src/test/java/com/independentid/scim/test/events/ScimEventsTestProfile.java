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

package com.independentid.scim.test.events;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;

public class ScimEventsTestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> cmap = new HashMap<>(Map.of(
                "scim.mongodb.test", "true",
                "scim.mongodb.dbname", "secTestSCIM",
                "scim.mongodb.uri", "mongodb://localhost:27017",

                "quarkus.log.category.\"com.independentid.scim\".level", "DEBUG",

                "scim.security.enable","false",
                "scim.kafkaLogEventHandler.enable","true",
                "scim.kafkaRepEventHandler.enable","true"
                 ));
        cmap.putAll(Map.of(


        ));
        return cmap;


    }

    @Override
    public String getConfigProfile() {
        return "ScimEventsTestProfile";
    }
}
