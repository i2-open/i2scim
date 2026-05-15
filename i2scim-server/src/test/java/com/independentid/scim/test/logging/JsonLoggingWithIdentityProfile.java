/*
 * Copyright 2026.  Independent Identity Incorporated
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.independentid.scim.test.logging;

import com.independentid.scim.backend.memory.MemoryProvider;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * JSON logging enabled with NODE_ID / CLUSTER_NAME populated, simulating the
 * i2goSignals compose deployment where each SCIM peer container is given a
 * distinct identity via environment variables.
 */
public class JsonLoggingWithIdentityProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "quarkus.log.console.json.enabled", "true",
                "NODE_ID", "test-node-1",
                "CLUSTER_NAME", "test-cluster",
                "scim.prov.providerClass", MemoryProvider.class.getName(),
                "scim.security.enable", "false",
                "scim.event.enable", "false",
                "scim.root.dir", "."
        );
    }

    @Override
    public String getConfigProfile() {
        return "JsonLoggingWithIdentityProfile";
    }
}
