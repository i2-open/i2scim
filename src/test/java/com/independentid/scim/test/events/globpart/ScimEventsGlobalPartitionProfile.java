/*
 * Copyright (c) 2021.
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

package com.independentid.scim.test.events.globpart;

import com.independentid.scim.backend.memory.MemoryProvider;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ScimEventsGlobalPartitionProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> cmap = new HashMap<>(Map.of(
                "scim.prov.providerClass", MemoryProvider.class.getName(),

                "quarkus.log.min-level","DEBUG",
                "logging.level.com.independentid.scim","DEBUG",
                "quarkus.log.category.\"com.independentid.scim.test\".level", "DEBUG",
                "scim.root.dir",".",  //enables local debug testing

                "scim.security.enable","false",
                "scim.event.enable","true",
                "scim.kafka.log.enable","true",
                "scim.kafka.rep.enable","true"
                 ));
        cmap.putAll(Map.of(

                "scim.kafka.log.bootstrap","10.1.10.101:9092",
                "scim.kafka.rep.bootstrap","10.1.10.101:9092",
                "scim.kafka.rep.sub.auto.offset.reset","latest",
                "scim.kafka.rep.sub.max.poll.records","1",  // faster turn around

                "scim.kafka.rep.mode","global-shard",
                "scim.kafka.rep.clientid", UUID.randomUUID().toString(),
                "scim.kafka.rep.cluster.id","<AUTO>"

        ));
        return cmap;


    }

    @Override
    public String getConfigProfile() {
        return "ScimEventsTestProfile";
    }
}
