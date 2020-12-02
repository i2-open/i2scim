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

package com.independentid.scim.test.backend;

import com.independentid.scim.backend.mongo.MongoProvider;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

public class ScimMongoTestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "scim.mongodb.test", "true",
                "scim.mongodb.dbname", "testMongoSCIM",
                "scim.mongodb.uri","mongodb://localhost:27017",
                    "scim.json.pretty","true",
                "scim.provider.bean", MongoProvider.class.toString(),
                "scim.security.enable", "false",
                "quarkus.http.test-port","0",
                "logging.level.com.independentid.scim","DEBUG",
                "quarkus.log.category.\"com.independentid.scim\".level","DEBUG"
        );
    }

    @Override
    public String getConfigProfile() {
        return "ScimMongoTestProfile";
    }
}
