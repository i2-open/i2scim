/*
 * Copyright 2026.  Independent Identity Incorporated
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.independentid.scim.test.devops;

import com.independentid.scim.backend.memory.MemoryProvider;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Profile for {@link PrometheusMetricsEndpointTest}. Security is left
 * <em>enabled</em> (the prod posture) precisely so the test can prove that
 * {@code /q/metrics} is reachable without an Authorization header — moving
 * the endpoint under {@code /q/*} must take it outside the SCIM auth filter.
 * Memory provider keeps the test self-contained (no Mongo dependency).
 */
public class PrometheusMetricsTestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "scim.prov.providerClass", MemoryProvider.class.getName(),
                "scim.security.enable", "true",
                "scim.event.enable", "false",
                "scim.root.dir", "."
        );
    }

    @Override
    public String getConfigProfile() {
        return "PrometheusMetricsTestProfile";
    }
}
