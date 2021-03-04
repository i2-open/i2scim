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

package com.independentid.scim.test.mongo;

import com.independentid.scim.backend.mongo.MongoProvider;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;

public class ScimMongoTestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String,String> cmap = new HashMap<>( Map.of(
                "scim.prov.mongo.test", "true",
                "scim.prov.mongo.dbname", "testMongoSCIM",
                "scim.prov.mongo.uri","mongodb://localhost:27017",

                "scim.prov.providerClass", MongoProvider.class.getName(),
                "scim.security.enable", "false",
                "quarkus.http.test-port","0",
                "quarkus.log.min-level","DEBUG",
                "logging.level.com.independentid.scim","DEBUG",
                "quarkus.log.category.\"com.independentid.scim\".level","DEBUG",
                "scim.schema.path","classpath:/schema/scimSchemaTest.json"
        ));
        cmap.putAll(Map.of(
                "scim.kafka.log.bootstrap","10.1.10.101:9092",
                "scim.kafka.rep.bootstrap","10.1.10.101:9092",
                "scim.event.enable","false"

        ));
        return cmap;
    }

    @Override
    public String getConfigProfile() {
        return "ScimMongoTestProfile";
    }
}