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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@code NODE_ID} / {@code CLUSTER_NAME} supplied at startup
 * appear verbatim as {@code node_id} / {@code cluster_name} in every JSON
 * record — the per-container identity i2goSignals relies on for label-based
 * Loki queries.
 */
@QuarkusTest
@TestProfile(JsonLoggingWithIdentityProfile.class)
class JsonLogIdentityTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void nodeIdAndClusterNameAreInjectedFromEnvironment() throws Exception {
        Formatter formatter = JsonLogFormatTest.consoleFormatter();
        LogRecord record = new LogRecord(Level.INFO, "identity check");
        record.setLoggerName("com.example.Demo");

        JsonNode json = MAPPER.readTree(formatter.format(record).trim());

        assertThat(json.has("node_id")).as("node_id field present").isTrue();
        assertThat(json.get("node_id").asText()).isEqualTo("test-node-1");

        assertThat(json.has("cluster_name")).as("cluster_name field present").isTrue();
        assertThat(json.get("cluster_name").asText()).isEqualTo("test-cluster");
    }
}
