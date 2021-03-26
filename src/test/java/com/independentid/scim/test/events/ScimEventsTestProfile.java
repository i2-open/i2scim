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

import com.independentid.scim.backend.mongo.MongoProvider;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;

public class ScimEventsTestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> cmap = new HashMap<>(Map.of(
                "scim.prov.providerClass", MongoProvider.class.getName(),

                "scim.prov.mongo.dbname", "eventTestSCIM",
                "scim.prov.mongo.uri", "mongodb://localhost:27017",

                "quarkus.log.min-level","DEBUG",
                "logging.level.com.independentid.scim","INFO",
                "quarkus.log.category.\"com.independentid.scim.test\".level", "INFO",

                "scim.security.enable","false",
                "scim.kafka.log.enable","true",
                "scim.kafka.rep.enable","true"
                 ));
        cmap.putAll(Map.of(
                "scim.event.enable","true",
                "scim.kafka.log.bootstrap","10.1.10.101:9092",
                "scim.kafka.rep.bootstrap","10.1.10.101:9092",
                "scim.kafka.rep.sub.auto.offset.reset","latest",
                "scim.kafka.rep.sub.max.poll.records","1"  // faster turn around

        ));
        return cmap;


    }

    @Override
    public String getConfigProfile() {
        return "ScimEventsTestProfile";
    }
}
