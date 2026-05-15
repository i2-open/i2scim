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

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression guard for the env-gated rollout: when JSON logging is disabled
 * (the prod default and the path operators get when they don't set
 * QUARKUS_LOG_CONSOLE_JSON), the console handler must emit human-readable text,
 * not JSON. A failure here means we shipped JSON-by-default and broke every
 * existing log consumer that expects the text format.
 */
@QuarkusTest
@TestProfile(JsonLogDisabledProfile.class)
class JsonLogDisabledTest {

    @Test
    void defaultFormatterIsTextNotJson() throws Exception {
        Formatter formatter = JsonLogFormatTest.consoleFormatter();
        LogRecord record = new LogRecord(Level.INFO, "hello text-mode");
        record.setLoggerName("com.example.Demo");

        String line = formatter.format(record);

        assertThat(line)
                .as("text formatter must not emit JSON-looking output")
                .doesNotStartWith("{");
        // The PatternFormatter renders the message verbatim somewhere in the line;
        // JsonFormatter would have quoted it.
        assertThat(line).contains("hello text-mode");
        assertThat(line).doesNotContain("\"msg\"");
        assertThat(line).doesNotContain("\"time\"");
    }
}
