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
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that with {@code quarkus.log.console.json=true} the root console
 * handler emits records whose JSON keys match the i2goSignals schema:
 * {@code time}, {@code level}, {@code msg}, {@code component}.
 */
@QuarkusTest
@TestProfile(JsonLoggingEnabledProfile.class)
class JsonLogFormatTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void formatterProducesJsonWithRenamedKeys() throws Exception {
        Formatter formatter = consoleFormatter();
        LogRecord record = new LogRecord(Level.INFO, "hello world");
        record.setLoggerName("com.example.Demo");
        record.setMillis(System.currentTimeMillis());

        String line = formatter.format(record).trim();

        JsonNode json = MAPPER.readTree(line);
        assertThat(json.has("time")).as("renamed timestamp -> time").isTrue();
        assertThat(json.has("level")).as("level retained").isTrue();
        assertThat(json.has("msg")).as("renamed message -> msg").isTrue();
        assertThat(json.has("component")).as("renamed loggerName -> component").isTrue();

        assertThat(json.get("msg").asText()).isEqualTo("hello world");
        assertThat(json.get("component").asText()).isEqualTo("com.example.Demo");
        assertThat(json.get("level").asText()).isEqualTo("INFO");

        // None of the legacy Quarkus default keys should leak through.
        assertThat(json.has("timestamp")).as("default timestamp must be renamed").isFalse();
        assertThat(json.has("message")).as("default message must be renamed").isFalse();
        assertThat(json.has("loggerName")).as("default loggerName must be renamed").isFalse();
    }

    @Test
    void unsetNodeIdAndClusterNameYieldUnknownNotLiteralPlaceholder() throws Exception {
        Formatter formatter = consoleFormatter();
        LogRecord record = new LogRecord(Level.INFO, "identity probe");
        record.setLoggerName("com.example.Demo");

        JsonNode json = MAPPER.readTree(formatter.format(record).trim());

        // When NODE_ID / CLUSTER_NAME aren't supplied the property-expression
        // default (`${NODE_ID:unknown}`) must resolve to the sentinel "unknown" —
        // never to the unresolved literal "${NODE_ID}", which would poison any
        // downstream Loki label and silently mask deployment misconfiguration.
        assertThat(json.has("node_id")).isTrue();
        assertThat(json.get("node_id").asText())
                .as("node_id must not be a literal placeholder when env is unset")
                .doesNotContain("${")
                .isEqualTo("unknown");

        assertThat(json.has("cluster_name")).isTrue();
        assertThat(json.get("cluster_name").asText())
                .as("cluster_name must not be a literal placeholder when env is unset")
                .doesNotContain("${")
                .isEqualTo("unknown");
    }

    @Test
    void formatterIncludesStaticServiceAndVersion() throws Exception {
        Formatter formatter = consoleFormatter();
        LogRecord record = new LogRecord(Level.INFO, "static-fields probe");
        record.setLoggerName("com.example.Demo");

        JsonNode json = MAPPER.readTree(formatter.format(record).trim());

        assertThat(json.has("service")).as("static service field present").isTrue();
        assertThat(json.get("service").asText()).isEqualTo("i2scim");

        assertThat(json.has("version")).as("static version field present").isTrue();
        // Built artifact version (e.g. "0.10.1"); non-empty is sufficient — we don't
        // want this test to break on every version bump.
        assertThat(json.get("version").asText()).isNotBlank();
    }

    /**
     * Returns the formatter Quarkus has wired onto the root console handler.
     * Quarkus runs on JBoss LogManager, so we walk that context (not the JUL
     * LogManager) to find the active ConsoleHandler. With JSON logging enabled
     * the formatter is the Quarkus JsonFormatter; with it disabled it is a
     * PatternFormatter.
     */
    static Formatter consoleFormatter() {
        org.jboss.logmanager.Logger root = org.jboss.logmanager.LogContext.getLogContext().getLogger("");
        for (Handler h : root.getHandlers()) {
            // ConsoleHandler may be wrapped in an AsyncHandler; recurse.
            Formatter f = unwrapFormatter(h);
            if (f != null) {
                return f;
            }
        }
        throw new AssertionError("no console handler with a formatter is registered on the JBoss root logger");
    }

    private static Formatter unwrapFormatter(Handler h) {
        Formatter f = h.getFormatter();
        if (f != null) {
            return f;
        }
        // QuarkusDelayedHandler, AsyncHandler, and most JBoss handlers extend
        // ExtHandler, which exposes nested handlers.
        if (h instanceof org.jboss.logmanager.ExtHandler ext) {
            for (Handler inner : ext.getHandlers()) {
                Formatter inner_f = unwrapFormatter(inner);
                if (inner_f != null) {
                    return inner_f;
                }
            }
        }
        return null;
    }
}
